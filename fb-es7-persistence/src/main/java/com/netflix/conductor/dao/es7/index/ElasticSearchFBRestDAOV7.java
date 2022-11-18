/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.netflix.conductor.dao.es7.index;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.aggregations.metrics.CardinalityAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.filter.security.jwt.ThreadLocalUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.RetryUtil;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.query.parser.ParserException;
import com.netflix.conductor.metrics.Monitors;

@Trace
@Singleton
public class ElasticSearchFBRestDAOV7 extends ElasticSearchFBBaseDAO implements IndexDAO {

    private static Logger logger = LoggerFactory.getLogger(ElasticSearchFBRestDAOV7.class);
    
    protected static final String SECROLE_ATTRIBUTE_NAME = "secRole";
    private static final String SECROLE_INDEX_GRANULARITY_MONTHLY = "MONTHLY";
    @SuppressWarnings("unused")
	private static final String SECROLE_INDEX_GRANULARITY_YEARLY = "YEARLY";

    private static final int RETRY_COUNT = 3;
    private static final int CORE_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 1L;
    private static final int SCROLL_SIZE = 10000;

    protected static final String WORKFLOW_DOC_TYPE = "workflow";
    protected static final String TASK_DOC_TYPE = "task";
    protected static final String LOG_DOC_TYPE = "tasklog";
    protected static final String EVENT_DOC_TYPE = "event";
    protected static final String MSG_DOC_TYPE = "message";
    protected static final String ACTIVE_INDEX_SUFFIX = "active";
    protected static final String INVALID_SECROLE_INDEX_SUFFIX = "invalid_secrole";

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final SimpleDateFormat SIMPLE_YEARLY_DATE_FORMAT = new SimpleDateFormat("yyyy");
    private static final SimpleDateFormat SIMPLE_MONTHLY_DATE_FORMAT = new SimpleDateFormat("yyyy.MM");
    protected static final SimpleDateFormat ES_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private @interface HttpMethod {
        String GET = "GET";
        String PUT = "PUT";
    }

    private static final String className = ElasticSearchFBRestDAOV7.class.getSimpleName();

    protected String workflowIndexName;

    protected String workflowIndexPrefix;

    protected String taskIndexName;

    protected String taskIndexPrefix;

    private String eventIndexPrefix;

    protected String eventIndexName;

    private String messageIndexPrefix;

    protected String messageIndexName;

    protected String logIndexName;

    private String logIndexPrefix;

    private boolean useActiveIndex = false;
    private boolean createIndexes = false;
    private boolean useMultipleWorkflowAndTaskIndexes = false;
    private boolean addMappingsToIndexes = true;

    private final String clusterHealthColor;

    protected final ObjectMapper objectMapper;
    protected final RestHighLevelClient elasticSearchClient;
    private final RestClient elasticSearchAdminClient;
    private final ExecutorService executorService;
    private final ExecutorService logExecutorService;
    private final ConcurrentHashMap<String, BulkRequests> bulkRequests;
    private final int indexBatchSize;
    private final int asyncBufferFlushTimeout;
    private final ElasticSearchConfiguration config;
    private Map<String, String> secRoleIndexSettings = new HashMap<String, String>();
    private Map<String, String> taskIndexNamesBySecRole = new ConcurrentHashMap<String, String>();
    private Map<String, String> workflowIndexNamesBySecRole = new ConcurrentHashMap<String, String>();
    private Map<String, String> activeTaskIndexNamesBySecRole = new ConcurrentHashMap<String, String>();
    private Map<String, String> activeWorkflowIndexNamesBySecRole = new ConcurrentHashMap<String, String>();


    static {
        SIMPLE_YEARLY_DATE_FORMAT.setTimeZone(GMT);
        SIMPLE_MONTHLY_DATE_FORMAT.setTimeZone(GMT);
        ES_TIMESTAMP_FORMAT.setTimeZone(GMT);
    }

    @Inject
    public ElasticSearchFBRestDAOV7(RestClientBuilder restClientBuilder, ElasticSearchConfiguration config, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.elasticSearchAdminClient = restClientBuilder.build();
        this.elasticSearchClient = new RestHighLevelClient(restClientBuilder);
        this.clusterHealthColor = config.getClusterHealthColor();
        this.bulkRequests = new ConcurrentHashMap<>();
        this.indexBatchSize = config.getIndexBatchSize();
        this.asyncBufferFlushTimeout = config.getAsyncBufferFlushTimeout();
        this.config = config;

        this.indexPrefix = config.getIndexName();
        this.workflowIndexName = indexName(WORKFLOW_DOC_TYPE);
        this.taskIndexName = indexName(TASK_DOC_TYPE);
        
        loadSecRoleIndexSettings();

        this.createIndexes = config.createIndexes();
        /*
         * Gotta put this in the constructor just in case an index operation happens before setup() is called by the framework
         */
        updateIndexesNames();
        
        /*
         * These will only be used if useMultipleWorkflowAndTaskIndexes is true
         */
        this.workflowIndexPrefix = this.indexPrefix + "_" + WORKFLOW_DOC_TYPE;
        this.taskIndexPrefix = this.indexPrefix + "_" + TASK_DOC_TYPE;
        
        this.logIndexPrefix = this.indexPrefix + "_" + LOG_DOC_TYPE;
        this.messageIndexPrefix = this.indexPrefix + "_" + MSG_DOC_TYPE;
        this.eventIndexPrefix = this.indexPrefix + "_" + EVENT_DOC_TYPE;
        this.useMultipleWorkflowAndTaskIndexes = config.useMultipleWorkflowAndTaskIndexes();
        this.useActiveIndex = config.useActiveIndex();
        this.addMappingsToIndexes = config.addMappingsToIndexes();
        int workerQueueSize = config.getAsyncWorkerQueueSize();
        int maximumPoolSize = config.getAsyncMaxPoolSize();

        // Set up a workerpool for performing async operations.
        this.executorService = new ThreadPoolExecutor(CORE_POOL_SIZE,
            maximumPoolSize,
            KEEP_ALIVE_TIME,
            TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(workerQueueSize),
                (runnable, executor) -> {
                    logger.warn("Request  {} to async dao discarded in executor {}", runnable, executor);
                    Monitors.recordDiscardedIndexingCount("indexQueue");
                });

        // Set up a workerpool for performing async operations for task_logs, event_executions, message
        int corePoolSize = 1;
        maximumPoolSize = 2;
        long keepAliveTime = 30L;
        this.logExecutorService = new ThreadPoolExecutor(corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(workerQueueSize),
            (runnable, executor) -> {
                logger.warn("Request {} to async log dao discarded in executor {}", runnable, executor);
                Monitors.recordDiscardedIndexingCount("logQueue");
            });

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::flushBulkRequests, 60, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    private void shutdown() {
        logger.info("Gracefully shutdown executor service");
        shutdownExecutorService(logExecutorService);
        shutdownExecutorService(executorService);
    }

    private void shutdownExecutorService(ExecutorService execService) {
        try {
            execService.shutdown();
            if (execService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.debug("tasks completed, shutting down");
            } else {
                logger.warn("Forcing shutdown after waiting for 30 seconds");
                execService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            logger.warn("Shutdown interrupted, invoking shutdownNow on scheduledThreadPoolExecutor for delay queue");
            execService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void setup() throws Exception {
        waitForHealthyCluster();
        createIndexesTemplates();
        if(!this.useMultipleWorkflowAndTaskIndexes) {
	        createWorkflowIndex(false);
	        createTaskIndex(false);
        }
        if(!this.useMultipleWorkflowAndTaskIndexes && this.useActiveIndex) {
	        createWorkflowIndex(true);
	        createTaskIndex(true);
        }
    }

    /**
     * Load from a JSON file the settings on how granular we want to index each document based on its secRole 
     */
    private void loadSecRoleIndexSettings() {
    	try {
        	JsonObject o = getJsonObjectFromFile("/secrole_index_settings.json");
        	if(o != null) {
        		JsonArray a = o.get("secRoleIndexSettings").getAsJsonArray();
        		if(a != null) {
        			a.forEach(secRoleSetting -> {
        				JsonObject jo = secRoleSetting.getAsJsonObject();
        				this.secRoleIndexSettings.put(jo.get("secRole").getAsString(), jo.get("indexGranularity").getAsString());
        			});
        		}
        	}
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    }
    private void createIndexesTemplates() {
        try {
        	if(addMappingsToIndexes) {
	            initIndexesTemplates();
        	}
            updateIndexesNames();
            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this::updateIndexesNames, 0, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error("Error creating index templates!", e);
        }
    }

    private void initIndexesTemplates() {
    	String envSuffix = getEnvironmentFromIndexPrefix();
        initIndexTemplate(LOG_DOC_TYPE);
        initIndexTemplate(EVENT_DOC_TYPE);
        initIndexTemplate(MSG_DOC_TYPE);
        initIndexTemplate(WORKFLOW_DOC_TYPE);
        initIndexTemplate(WORKFLOW_DOC_TYPE, envSuffix);
        initIndexTemplate(TASK_DOC_TYPE);
        initIndexTemplate(TASK_DOC_TYPE, envSuffix);
    }

    private String getEnvironmentFromIndexPrefix() {
    	String env = null;
    	String[] indexNameParts = this.indexPrefix.split("_");
    	if(indexNameParts != null && indexNameParts.length > 1) {
    		env = indexNameParts[1];
    	}
    	return env;
    }
    
    /**
     * Initializes the index with the required templates and mappings.
     */
    private void initIndexTemplate(String type) {
    	initIndexTemplate(type, null);
    }

    /**
     * Initializes the index with the required templates and mappings.
     */
    private void initIndexTemplate(String type, String envSuffix) {
        String template = "fb_template_" + type;
        if(StringUtils.isNotBlank(envSuffix)) {
        	template += "_" + envSuffix;
        }
        try {
            if (doesResourceNotExist("/_template/" + template)) {
                logger.info("Creating the index template '" + template + "'");
                InputStream stream = ElasticSearchFBRestDAOV7.class.getResourceAsStream("/" + template + ".json");
                if(stream != null) {
                    byte[] templateSource = IOUtils.toByteArray(stream);
    				Request newRequest = new Request(HttpMethod.PUT, "/_template/" + template);
    				newRequest.setEntity(new NByteArrayEntity(templateSource, ContentType.APPLICATION_JSON));
                    elasticSearchAdminClient.performRequest(newRequest);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to init " + template, e);
        }
    }

    private void updateIndexesNames() {
        logIndexName = updateIndexName(LOG_DOC_TYPE, null, false, false, SIMPLE_MONTHLY_DATE_FORMAT);
        eventIndexName = updateIndexName(EVENT_DOC_TYPE, null, false ,false, SIMPLE_MONTHLY_DATE_FORMAT);
        messageIndexName = updateIndexName(MSG_DOC_TYPE, null, false, false, SIMPLE_MONTHLY_DATE_FORMAT);
        if(this.useMultipleWorkflowAndTaskIndexes) {
       		this.secRoleIndexSettings.keySet().forEach(secRole -> {
       			SimpleDateFormat indexSuffixFormat =
  					SECROLE_INDEX_GRANULARITY_MONTHLY.equals(this.secRoleIndexSettings.get(secRole)) ? SIMPLE_MONTHLY_DATE_FORMAT : SIMPLE_YEARLY_DATE_FORMAT;
       			this.workflowIndexNamesBySecRole.put(secRole, updateIndexName(WORKFLOW_DOC_TYPE, secRole, true, false, indexSuffixFormat));
   				updateIndexName(WORKFLOW_DOC_TYPE, INVALID_SECROLE_INDEX_SUFFIX, true, false, null);
       			if(this.useActiveIndex) {
       				this.activeWorkflowIndexNamesBySecRole.put(secRole, updateIndexName(WORKFLOW_DOC_TYPE, secRole, true, true, null));
       				updateIndexName(WORKFLOW_DOC_TYPE, INVALID_SECROLE_INDEX_SUFFIX, true, true, null);
       			}
       			this.taskIndexNamesBySecRole.put(secRole, updateIndexName(TASK_DOC_TYPE, secRole, true, false, indexSuffixFormat));
   				updateIndexName(TASK_DOC_TYPE, INVALID_SECROLE_INDEX_SUFFIX, true, false, null);
       			if(this.useActiveIndex) {
       				this.activeTaskIndexNamesBySecRole.put(secRole, updateIndexName(TASK_DOC_TYPE, secRole, true, true, null));
       				updateIndexName(TASK_DOC_TYPE, INVALID_SECROLE_INDEX_SUFFIX, true, true, null);
       			}
       		});
        }
    }

    private String updateIndexName(String type, String secRole, boolean createAlias, boolean active, SimpleDateFormat indexSuffixFormat) {
        String indexName = this.indexPrefix + "_" + type;
        if(secRole != null) {
        	indexName += "_" + secRole.toLowerCase();
        }
        if(active) {
        	indexName += "_" + ACTIVE_INDEX_SUFFIX;
        }
        if(indexSuffixFormat != null) {
        	indexName += "_" + indexSuffixFormat.format(new Date());
        }
        try {
        	if(createAlias) {
        		addIndex(indexName, this.indexPrefix + "_" + type);
        	} else {
        		addIndex(indexName);
        	}
            return indexName;
        } catch (IOException e) {
            logger.error("Failed to update log index name: {}", indexName, e);
            throw new ApplicationException(e.getMessage(), e);
        }
    }

    private void createWorkflowIndex(boolean activeIndex) {
        String indexName = activeIndex ? this.workflowIndexPrefix + "_" + ACTIVE_INDEX_SUFFIX : indexName(WORKFLOW_DOC_TYPE);
        try {
       		addIndex(indexName, this.workflowIndexPrefix);
        } catch (IOException e) {
            logger.error("Failed to initialize index '{}'", indexName, e);
        }
    }

    private void createTaskIndex(boolean activeIndex) {
        String indexName = activeIndex ? this.taskIndexPrefix + "_" + ACTIVE_INDEX_SUFFIX : indexName(TASK_DOC_TYPE);
        try {
       		addIndex(indexName, this.taskIndexPrefix);
        } catch (IOException e) {
            logger.error("Failed to initialize index '{}'", indexName, e);
        }
    }

    /**
     * Waits for the ES cluster to become green.
     *
     * @throws Exception If there is an issue connecting with the ES cluster.
     */
    private void waitForHealthyCluster() throws Exception {
        try {
			Request newRequest = new Request(HttpMethod.GET, "/_cluster/health");
			newRequest.addParameter("wait_for_status", this.clusterHealthColor);
			newRequest.addParameter("timeout", "30s");
			elasticSearchAdminClient.performRequest(newRequest);
        } catch(Exception e) {
        	clusterNotAvailable(e);
        }
    }

    protected void clusterNotAvailable(Exception e) throws Exception {
    	throw e;
    }
    
    /**
     * Adds an index to elasticsearch if it does not exist.
     *
     * @param index The name of the index to create.
     * @return true if index already existed, false if it was created
     * @throws IOException If an error occurred during requests to ES.
     */
    private boolean addIndex(final String index) throws IOException {
    	return addIndex(index, null);
    }

    /**
     * Adds an index to elasticsearch if it does not exist.
     *
     * @param index The name of the index to create.
     * @return true if index already existed, false if it was created
     * @throws IOException If an error occurred during requests to ES.
     */
    private boolean addIndex(final String index, final String alias) throws IOException {
    	boolean alreadyExisted = true;
    	if(this.createIndexes) {
	        String resourcePath = "/" + index;
	
	        if (doesResourceNotExist(resourcePath)) {
	        	alreadyExisted = false;
	            logger.info("Adding index '{}'...", index);
	
	            try {
	            	CreateIndexRequest request = new CreateIndexRequest(index);
	            	request.settings(Settings.builder()
	            		.put("index.number_of_shards", config.getElasticSearchIndexShardCount())
	            		.put("index.number_of_replicas", config.getElasticSearchIndexReplicationCount()));
					elasticSearchClient.indices().create(request, RequestOptions.DEFAULT);
					if(this.useMultipleWorkflowAndTaskIndexes && alias != null) {
						addIndexToAlias(index, alias);
					}
	                logger.info("Added '{}' index", index);
	            } catch (ResponseException e) {
	
	                boolean errorCreatingIndex = true;
	
	                Response errorResponse = e.getResponse();
	                if (errorResponse.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
	                    JsonNode root = objectMapper.readTree(EntityUtils.toString(errorResponse.getEntity()));
	                    String errorCode = root.get("error").get("type").asText();
	                    if ("index_already_exists_exception".equals(errorCode)) {
	                        errorCreatingIndex = false;
	                    }
	                }
	
	                if (errorCreatingIndex) {
	                    throw e;
	                }
	            }
	        } else {
	            logger.debug("Index '{}' already exists", index);
	        }
    	}
        return alreadyExisted;
    }

    /**
     * Adds an index to elasticsearch if it does not exist.
     *
     * @param index The name of the index to create.
     * @return true if index already existed, false if it was created
     * @throws IOException If an error occurred during requests to ES.
     */
    private void addIndexToAlias(final String index, final String alias) throws IOException {
		IndicesAliasesRequest request = new IndicesAliasesRequest(); 
		AliasActions aliasAction = new AliasActions(AliasActions.Type.ADD).index(index).alias(alias); 
		request.addAliasAction(aliasAction);
		elasticSearchClient.indices().updateAliases(request, RequestOptions.DEFAULT);
        logger.info("Added '{}' index to {} alias", index, alias);
    }

    /**
     * Determines whether a resource exists in ES. This will call a GET method to a particular path and
     * return true if status 200; false otherwise.
     *
     * @param resourcePath The path of the resource to get.
     * @return True if it exists; false otherwise.
     * @throws IOException If an error occurred during requests to ES.
     */
    public boolean doesResourceExist(final String resourcePath) throws IOException {
		Request newRequest = new Request(HttpMethod.GET, resourcePath);
		Response response = null;
		try {
			response = elasticSearchAdminClient.performRequest(newRequest);
		} catch(ResponseException e) {
			if(e.getResponse().getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				return false;
			}
			throw new IOException(e);
		}
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

    /**
     * The inverse of doesResourceExist.
     *
     * @param resourcePath The path of the resource to check.
     * @return True if it does not exist; false otherwise.
     * @throws IOException If an error occurred during requests to ES.
     */
    public boolean doesResourceNotExist(final String resourcePath) throws IOException {
        return !doesResourceExist(resourcePath);
    }

    @Override
    public void indexWorkflow(Workflow workflow) {
        String workflowId = workflow.getWorkflowId();
        WorkflowSummary summary = new WorkflowSummary(workflow);
        String indexName = getWorkflowWriteIndexName(workflow, this.useActiveIndex && !workflow.getStatus().isTerminal());
        if(indexName != null) {
        	bulkIndexObject(indexName, WORKFLOW_DOC_TYPE, workflowId, summary);
        }
    }

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(Workflow workflow) {
        return CompletableFuture.runAsync(() -> indexWorkflow(workflow), executorService);
    }

    @Override
    public void indexTask(Task task) {
        String taskId = task.getTaskId();
        TaskSummary summary = new TaskSummary(task);
        String indexName = getTaskWriteIndexName(task, this.useActiveIndex && !task.getStatus().isTerminal());
        if(indexName != null) {
        	bulkIndexObject(indexName, TASK_DOC_TYPE, taskId, summary);
        }
    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(Task task) {
        return CompletableFuture.runAsync(() -> indexTask(task), executorService);
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> taskExecLogs) {
        if (taskExecLogs.isEmpty()) {
            return;
        }

        BulkRequest bulkRequest = new BulkRequest();

        for (TaskExecLog log : taskExecLogs) {

            byte[] docBytes;
            try {
                docBytes = objectMapper.writeValueAsBytes(log);
            } catch (JsonProcessingException e) {
                logger.error("Failed to convert task log to JSON for task {}", log.getTaskId());
                continue;
            }

            IndexRequest request = new IndexRequest(logIndexName);
            request.source(docBytes, XContentType.JSON);
            bulkRequest.add(request);
        }

        try {
            new RetryUtil<BulkResponse>().retryOnException(() -> {
                try {
                    return elasticSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, null, BulkResponse::hasFailures, RETRY_COUNT, "Indexing all execution logs into doc_type task", "addTaskExecutionLogs");
        } catch (Exception e) {
            List<String> taskIds = taskExecLogs.stream().map(TaskExecLog::getTaskId).collect(Collectors.toList());
            logger.error("Failed to index task execution logs for tasks: {}", taskIds, e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        return CompletableFuture.runAsync(() -> addTaskExecutionLogs(logs), executorService);
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        try {
            BoolQueryBuilder query = boolQueryBuilder("taskId='" + taskId + "'", "*");

            // Create the searchObjectIdsViaExpression source
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.sort(new FieldSortBuilder("createdTime").order(SortOrder.ASC));
            searchSourceBuilder.size(config.getElasticSearchTasklogLimit());

            // Generate the actual request to send to ES.
            SearchRequest searchRequest = new SearchRequest(logIndexPrefix + "*");
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);

            return mapTaskExecLogsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get task execution logs for task: {}", taskId, e);
        }
        return null;
    }

    private List<TaskExecLog> mapTaskExecLogsResponse(SearchResponse response) throws IOException {
        SearchHit[] hits = response.getHits().getHits();
        List<TaskExecLog> logs = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            String source = hit.getSourceAsString();
            TaskExecLog tel = objectMapper.readValue(source, TaskExecLog.class);
            logs.add(tel);
        }
        return logs;
    }

    @Override
    public List<Message> getMessages(String queue) {
        try {
            BoolQueryBuilder query = boolQueryBuilder("queue='" + queue + "'", "*");

            // Create the searchObjectIdsViaExpression source
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.sort(new FieldSortBuilder("created").order(SortOrder.ASC));

            // Generate the actual request to send to ES.
            SearchRequest searchRequest = new SearchRequest(messageIndexPrefix + "*");
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return mapGetMessagesResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get messages for queue: {}", queue, e);
        }
        return null;
    }

    private List<Message> mapGetMessagesResponse(SearchResponse response) throws IOException {
        SearchHit[] hits = response.getHits().getHits();
        TypeFactory factory = TypeFactory.defaultInstance();
        MapType type = factory.constructMapType(HashMap.class, String.class, String.class);
        List<Message> messages = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            String source = hit.getSourceAsString();
            Map<String, String> mapSource = objectMapper.readValue(source, type);
            Message msg = new Message(mapSource.get("messageId"), mapSource.get("payload"), null);
            messages.add(msg);
        }
        return messages;
    }

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        try {
            BoolQueryBuilder query = boolQueryBuilder("event='" + event + "'", "*");

            // Create the searchObjectIdsViaExpression source
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.sort(new FieldSortBuilder("created").order(SortOrder.ASC));

            // Generate the actual request to send to ES.
            SearchRequest searchRequest = new SearchRequest(eventIndexPrefix + "*");
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);

            return mapEventExecutionsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get executions for event: {}", event, e);
        }
        return null;
    }

    private List<EventExecution> mapEventExecutionsResponse(SearchResponse response) throws IOException {
        SearchHit[] hits = response.getHits().getHits();
        List<EventExecution> executions = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            String source = hit.getSourceAsString();
            EventExecution tel = objectMapper.readValue(source, EventExecution.class);
            executions.add(tel);
        }
        return executions;
    }

    @Override
    public void addMessage(String queue, Message message) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("messageId", message.getId());
        doc.put("payload", message.getPayload());
        doc.put("queue", queue);
        doc.put("created", System.currentTimeMillis());

        indexObject(messageIndexName, MSG_DOC_TYPE, doc);
    }

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        return CompletableFuture.runAsync(() -> addMessage(queue, message), executorService);
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {
        String id = eventExecution.getName() + "." + eventExecution.getEvent() + "." + eventExecution.getMessageId() + "." + eventExecution.getId();

        indexObject(eventIndexName, EVENT_DOC_TYPE, id, eventExecution);
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return CompletableFuture.runAsync(() -> addEventExecution(eventExecution), executorService);
    }

    @Override
    public SearchResult<String> searchWorkflows(String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectIdsViaExpression(query, start, count, sort, freeText, WORKFLOW_DOC_TYPE);
        } catch (Exception e) {
        	logger.error("ParserError during searchWorkflows, freeTextQuery: " + freeText);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "For freeTextQuery '" + freeText + "', getting this error:" + e.getMessage(), e);
        }
    }

    @Override
    public SearchResult<String> searchTasks(String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectIdsViaExpression(query, start, count, sort, freeText, TASK_DOC_TYPE);
        } catch (Exception e) {
        	logger.error("Error during searchTasks, start: " + start + ", size: " + count + ", query: " + query + ", freeTextQuery: " + freeText);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Error during searchTasks: start: " + start + ", size: " + count + ", query: " + query + ", freeTextQuery: '" + freeText + "', getting this error:" + e.getMessage(), e);
        }
    }

    @Override
    public void removeWorkflow(String workflowId) {

        DeleteByQueryRequest request = new DeleteByQueryRequest(this.workflowIndexPrefix);

        try {
            request.setQuery(boolQueryBuilder("workflowId='" + workflowId + "'", "*"));
            BulkByScrollResponse response = elasticSearchClient.deleteByQuery(request, RequestOptions.DEFAULT);

            if (response.getDeleted() == 0) {
                logger.error("Index removal failed - document not found by id: {}", workflowId);
            }

        } catch (Exception e) {
            logger.error("Failed to remove workflow {} from index", workflowId, e);
            Monitors.error(className, "remove");
        }
    }

    public void removeDocumentFromIndex(String index, String id) {
    	if(this.config != null && this.useActiveIndex) {
    		ActionListener<DeleteResponse> listener = new ActionListener<DeleteResponse>() {
    		    @Override
    		    public void onResponse(DeleteResponse deleteResponse) {
    		    }

    		    @Override
    		    public void onFailure(Exception e) {
                    logger.error("Failed to remove workflow {} from index {}", id, index, e);
                    Monitors.error(className, "remove");
    		    }
    		};
            try {
	    		DeleteRequest request = new DeleteRequest(index, id);
	    		elasticSearchClient.deleteAsync(request, RequestOptions.DEFAULT, listener);    		
            } catch (Exception e) {
                logger.error("Failed to remove workflow {} from index {}", id, index, e);
                Monitors.error(className, "remove");
            }
    	}
    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return CompletableFuture.runAsync(() -> removeWorkflow(workflowId), executorService);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {

        if (keys.length != values.length) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, "Number of keys and values do not match");
        }

        UpdateByQueryRequest request = new UpdateByQueryRequest(this.workflowIndexPrefix);
    	String scriptString = null;
        try {
        	request.setQuery(boolQueryBuilder("workflowId='" + workflowInstanceId + "'", "*"));
        	StringBuffer script = new StringBuffer();
        	for(int i = 0; i < keys.length; i++) {
        		script.append("ctx._source.");
        		script.append(keys[i]);
        		script.append("=params.");
           		script.append(keys[i]);
        		script.append(";");
        	}
        	scriptString = script.toString();
            Map<String, Object> params = IntStream.range(0, keys.length).boxed().collect(Collectors.toMap(i -> keys[i], i -> values[i]));
        	request.setScript(new Script(ScriptType.INLINE, "painless", scriptString, params));
       	} catch(Exception e) {
        	throw new RuntimeException(e);
        }

        logger.debug("Updating workflow {} with {}", workflowInstanceId, scriptString);

        try {
            BulkByScrollResponse response =  elasticSearchClient.updateByQuery(request, RequestOptions.DEFAULT);
            if(response.getUpdated() == 0) {
                logger.error("Index update failed - document not found by id: {}", workflowInstanceId);
            }
        } catch (IOException e) {
            logger.error("Index update failed for workflow {} - got this exception {}", workflowInstanceId, e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        return CompletableFuture.runAsync(() -> updateWorkflow(workflowInstanceId, keys, values), executorService);
    }

    @Override
    public String get(String workflowInstanceId, String fieldToGet) {
        try {
        	if(this.useMultipleWorkflowAndTaskIndexes) {
        		/* 
        		 * If there are multiple indexes, we can't use the get() method because it only allows searching one index, so we have 
        		 * to go the long way and find the latest one, assuming there is only one per index and max 20 indexes that contain it.
        		 */
                QueryBuilder queryBuilder = boolQueryBuilder("workflowId='" + workflowInstanceId + "'", "*");
        		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                searchSourceBuilder.query(queryBuilder);
                searchSourceBuilder.size(20);

                // Generate the actual request to send to ES.
                SearchRequest searchRequest = new SearchRequest(this.workflowIndexPrefix);
                searchRequest.source(searchSourceBuilder);
                SearchResponse response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);
                SearchHits hits = response.getHits();
	            Map<String, Object> latestSourceAsMap = null; 
                if(hits.getHits().length == 1) {
                	latestSourceAsMap = hits.getHits()[0].getSourceAsMap();
                } else {
                	long latestTimestamp = 0;
                    for(SearchHit hit : hits.getHits()) {
       		            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
       		            Object o = sourceAsMap.get("@timestamp");
       		            if(o != null) {
    	            		try {
    	            			Date date = ES_TIMESTAMP_FORMAT.parse((String)o);
    	            			if(date.getTime() > latestTimestamp) {
    	            				latestTimestamp = date.getTime();
    	            				latestSourceAsMap = sourceAsMap;
    	            			}
    	            		} catch(Exception e) {
    	            		}
       		            }
	            	}
                }
                if(latestSourceAsMap != null && latestSourceAsMap.get(fieldToGet) != null) {
	                return latestSourceAsMap.get(fieldToGet).toString();
                }                    	
        	} else {
   		        GetRequest request = new GetRequest(this.workflowIndexPrefix, workflowInstanceId);
   		        GetResponse response = elasticSearchClient.get(request, RequestOptions.DEFAULT);
   		        if (response != null && response.isExists()) {
   		            Map<String, Object> sourceAsMap = response.getSourceAsMap();
   		            if (sourceAsMap.get(fieldToGet) != null) {
   		                return sourceAsMap.get(fieldToGet).toString();
   		            }
   		        }
        	}
        } catch (Exception e) {
            logger.error("Unable to get Workflow: {} from ElasticSearch index: {}", workflowInstanceId, workflowIndexName, e);
            return null;
        }

        logger.debug("Unable to find Workflow: {} in ElasticSearch index: {}.", workflowInstanceId, workflowIndexName);
        return null;
    }

    protected Map<String, Object> getWorkflowAsSourceMap(String workflowInstanceId) throws Exception {
    	if(this.useMultipleWorkflowAndTaskIndexes) {
    		/* 
    		 * If there are multiple indexes, we can't use the get() method because it only allows searching one index, so we have 
    		 * to go the long way and find the latest one, assuming there is only one per index and max 20 indexes that contain it.
    		 */
            QueryBuilder queryBuilder = boolQueryBuilder("workflowId='" + workflowInstanceId + "'", "*");
    		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(queryBuilder);
            searchSourceBuilder.size(20);

            // Generate the actual request to send to ES.
            SearchRequest searchRequest = new SearchRequest(this.workflowIndexPrefix);
            searchRequest.source(searchSourceBuilder);
            SearchResponse response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            Map<String, Object> latestSourceAsMap = null; 
            if(hits.getHits().length == 1) {
            	latestSourceAsMap = hits.getHits()[0].getSourceAsMap();
            } else {
            	long latestTimestamp = 0;
                for(SearchHit hit : hits.getHits()) {
   		            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
   		            Object o = sourceAsMap.get("@timestamp");
   		            if(o != null) {
	            		try {
	            			Date date = ES_TIMESTAMP_FORMAT.parse((String)o);
	            			if(date.getTime() > latestTimestamp) {
	            				latestTimestamp = date.getTime();
	            				latestSourceAsMap = sourceAsMap;
	            			}
	            		} catch(Exception e) {
	            		}
   		            }
            	}
            }
            if(latestSourceAsMap != null) {
                return latestSourceAsMap;
            }                    	
    	} else {
	        GetRequest request = new GetRequest(this.workflowIndexPrefix, workflowInstanceId);
	        GetResponse response = elasticSearchClient.get(request, RequestOptions.DEFAULT);
	        if (response != null && response.isExists()) {
	            return response.getSourceAsMap();
	        }
    	}
        return null;
    }

    private SearchResult<String> searchObjectIdsViaExpression(String structuredQuery, int start, int size, List<String> sortOptions, String freeTextQuery, String docType) throws ParserException, IOException {
    	String fullFreeTextQuery = freeTextQuery;
    	String secRoleSearchString = getSecRoleSearchString(docType);
    	if(StringUtils.isNotEmpty(secRoleSearchString)) {
    		if(StringUtils.isEmpty(freeTextQuery) || "*".equals(freeTextQuery.trim())) {
    			fullFreeTextQuery = secRoleSearchString;
    		} else {
    			fullFreeTextQuery = secRoleSearchString + " AND " + freeTextQuery;
    		}
    	}
    	
        QueryBuilder queryBuilder = boolQueryBuilder(structuredQuery, fullFreeTextQuery);
// Thought this might help performance but it doesn't, it is even a little slower        
//        QueryBuilder queryBuilder = QueryBuilders.constantScoreQuery(boolQueryBuilder(structuredQuery, fullFreeTextQuery));
        return searchObjectIds(this.indexPrefix + "_" + docType, queryBuilder, start, size, sortOptions, docType);
    }

    private String getSecRoleSearchString(String docType) {
    	StringBuffer searchString = null;
    	String[] secRoles = ThreadLocalUser.getValidSecRoles();
    	if(secRoles != null && secRoles.length > 0) {
    		searchString = new StringBuffer("tags.secRole IN ('");
    		for(int i = 0; i < secRoles.length; i++) {
    			searchString.append(secRoles[i] + "'");
    			if(i < (secRoles.length - 1)) {
    				searchString.append(",'");
    			}
    		}
    		searchString.append(")");
    	}
    	return searchString == null ? null : searchString.toString();
    }
    
    private SearchResult<String> searchObjectIds(String indexName, QueryBuilder queryBuilder, int start, int size, String docType) throws IOException {
        return searchObjectIds(indexName, queryBuilder, start, size, null, docType);
    }

    /**
     * Tries to find object ids for a given query in an index.
     *
     * @param indexName    The name of the index.
     * @param queryBuilder The query to use for searching.
     * @param start        The start to use.
     * @param size         The total return size.
     * @param sortOptions  A list of string options to sort in the form VALUE:ORDER; where ORDER is optional and can be either ASC OR DESC.
     * @param docType      The document type to searchObjectIdsViaExpression for.
     * @return The SearchResults which includes the count and IDs that were found.
     * @throws IOException If we cannot communicate with ES.
     */
    private SearchResult<String> searchObjectIds(String indexName, QueryBuilder queryBuilder, int start, int size, List<String> sortOptions, String docType) throws IOException {
    	/*
    	 * The non-scrolling api will not allow start+size to be greater than 10,000 so we have to use the Scroll API if that is the case
    	 */
    	boolean useScrolling = (start + size > 10000);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        searchSourceBuilder.size(useScrolling ? SCROLL_SIZE : size);
        // We just need the id
        searchSourceBuilder.fetchSource(false);
        
        /*
         * Scroll API does not support starting at a certain point
         */
		if(!useScrolling) {
	        searchSourceBuilder.from(start);
		}

        if (sortOptions != null && !sortOptions.isEmpty()) {
            for (String sortOption : sortOptions) {
                SortOrder order = SortOrder.ASC;
                String field = sortOption;
                int index = sortOption.indexOf(":");
                if (index > 0) {
                    field = sortOption.substring(0, index);
                    order = SortOrder.valueOf(sortOption.substring(index + 1));
                }
                searchSourceBuilder.sort(new FieldSortBuilder(field).order(order));
            }
        }

        /*
         * If using multiple indexes we have to worry about duplicate documents for a workflow/task because the same id could
         * be in more than one index.
         * 
         * NOTE THAT the Scroll API does not support the collapse() method so we can't eliminate duplicates if using that API.
         */
        if(this.useMultipleWorkflowAndTaskIndexes && !useScrolling) {
        	String docIdField = WORKFLOW_DOC_TYPE.equals(docType) ? "workflowId" : "taskId";
        	// Collapse the id field to eliminate duplicates
	        searchSourceBuilder.collapse(new CollapseBuilder(docIdField));
	        // Must get the total count with a cardinality aggregation which gives us a count of unique ids.
	        CardinalityAggregationBuilder dedupCountAggregation = AggregationBuilders.cardinality("dedup_count").field(docIdField);
	        searchSourceBuilder.aggregation(dedupCountAggregation);
        }

        // Generate the actual request to send to ES.
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);

        long totalScrollCount = 0;
        SearchResponse response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);
        List<String> result = new LinkedList<>();
        SearchHits hits = response.getHits();
        long totalRetrieved = 0;
        long count = 0;
        if(useScrolling) {
        	CountRequest countRequest = new CountRequest(indexName);
        	countRequest.query(queryBuilder);
        	CountResponse countResponse = this.elasticSearchClient.count(countRequest, RequestOptions.DEFAULT);
        	count = countResponse.getCount();

        	hits = response.getHits();
            SearchHit last = hits.getAt(hits.getHits().length - 1);
            totalRetrieved = hits.getHits().length;
            totalScrollCount = totalRetrieved;
            while(totalScrollCount < count && (totalScrollCount < start || result.size() != size)) {
            	if(totalScrollCount > start) {
            		addToScrollResult(totalRetrieved, totalScrollCount, start, size, response, result);
            	}
            	if(totalScrollCount < count && (totalScrollCount < start || result.size() != size)) {
	                searchRequest = new SearchRequest(indexName);
	                searchSourceBuilder.searchAfter(last.getSortValues());
	                searchRequest.source(searchSourceBuilder);
	                response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);
	                hits = response.getHits();
	                last = hits.getAt(hits.getHits().length - 1);
	                totalRetrieved = hits.getHits().length;
	            	totalScrollCount += totalRetrieved;
	            	if(result.size() != size) {
	            		addToScrollResult(totalRetrieved, totalScrollCount, start, size, response, result);
	            	}
            	}
            }
        }

        /*
         * If we scrolled, count and results are already set
         */
        if(!useScrolling) {
	        if(this.useMultipleWorkflowAndTaskIndexes) {
	            Cardinality cardinalityCount = response.getAggregations().get("dedup_count");
	            count = cardinalityCount.getValue();
	        } else {
	        	count = response.getHits().getTotalHits().value;
	        }
	        if(count > 0) {
	        	response.getHits().forEach(hit -> result.add(hit.getId()));
	        }
	        /* 
	         * The search request will return a maximum of 10,000 as the total hits so we must use a CountRequest if that
	         * is what it returns.
	         */
	        if(count >= 10000) {
	        	CountRequest countRequest = new CountRequest(indexName);
	        	countRequest.query(queryBuilder);
	        	CountResponse countResponse = this.elasticSearchClient.count(countRequest, RequestOptions.DEFAULT);
	        	count = countResponse.getCount();
	        }
        }
        return new SearchResult<>(count, result);
    }

    private void addToScrollResult(long totalRetrieved, long totalScrollCount, int start, int size, SearchResponse response, List<String> result) {
		long startAt = totalRetrieved - (totalScrollCount - start);
		if(startAt >= 0) {
    		SearchHits partialHits = response.getHits();
    		for(int i = 0; i < totalRetrieved; i++) {
    			if(i >= startAt) {
    				result.add(partialHits.getHits()[i].getId());
    			}
    			if(result.size() == size) {
    				break;
    			}
    		}
		}
    }
    
    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        QueryBuilder q = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("endTime").lt(LocalDate.now().minusDays(archiveTtlDays).toString()))
                .should(QueryBuilders.termQuery("status", "COMPLETED"))
                .should(QueryBuilders.termQuery("status", "FAILED"))
                .should(QueryBuilders.termQuery("status", "TIMED_OUT"))
                .should(QueryBuilders.termQuery("status", "TERMINATED"))
                .mustNot(QueryBuilders.existsQuery("archived"))
                .minimumShouldMatch(1);

        SearchResult<String> workflowIds;
        try {
            workflowIds = searchObjectIds(indexName, q, 0, 1000, WORKFLOW_DOC_TYPE);
        } catch (IOException e) {
            logger.error("Unable to communicate with ES to find archivable workflows", e);
            return Collections.emptyList();
        }

        return workflowIds.getResults();
    }

    public List<String> searchRecentRunningWorkflows(int lastModifiedHoursAgoFrom, int lastModifiedHoursAgoTo) {
        DateTime dateTime = new DateTime();
        QueryBuilder q = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("updateTime")
                        .gt(dateTime.minusHours(lastModifiedHoursAgoFrom)))
                .must(QueryBuilders.rangeQuery("updateTime")
                        .lt(dateTime.minusHours(lastModifiedHoursAgoTo)))
                .must(QueryBuilders.termQuery("status", "RUNNING"));

        SearchResult<String> workflowIds;
        try {
            workflowIds = searchObjectIds(this.workflowIndexPrefix, q, 0, 5000, Collections.singletonList("updateTime:ASC"), WORKFLOW_DOC_TYPE);
        } catch (IOException e) {
            logger.error("Unable to communicate with ES to find recent running workflows", e);
            return Collections.emptyList();
        }

        return workflowIds.getResults();
    }

    protected void indexObject(final String index, final String docType, final Object doc) {
        indexObject(index, docType, null, doc);
    }

    protected void indexObject(final String index, final String docType, final String docId, final Object doc) {
        byte[] docBytes;
        long startTime = Instant.now().toEpochMilli();
        try {
            docBytes = objectMapper.writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert {} '{}' to byte string", docType, docId);
            return;
        }

        IndexRequest request = new IndexRequest(index);
        request.id(docId);
        request.source(docBytes, XContentType.JSON);

        indexWithRetry(request, "Indexing " + docType + ": " + docId);
        long endTime = Instant.now().toEpochMilli();
        logger.debug("Time taken {} for  indexing {}:{}", endTime - startTime, docType, docId);
        Monitors.recordESIndexTime("index_" + docType, docType, endTime - startTime);
        Monitors.recordWorkerQueueSize("indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
    }

	protected String getWorkflowWriteIndexName(Workflow workflow, boolean activeIndex) {
		String indexName = this.workflowIndexPrefix;
		if(this.useMultipleWorkflowAndTaskIndexes) {
			String secRole = workflow.getInput() != null ? (String) workflow.getInput().get(SECROLE_ATTRIBUTE_NAME) : null;
			if(secRole == null) {
				logger.error("**** Workflow with id '" + workflow.getWorkflowId() + "' does not have a secRole attribute");
			} else {
				if(activeIndex) {
					indexName = this.activeWorkflowIndexNamesBySecRole.get(secRole);
					if(indexName == null) {
						indexName = this.workflowIndexPrefix + "_" + INVALID_SECROLE_INDEX_SUFFIX + "_" + ACTIVE_INDEX_SUFFIX;
					}
				} else {
					indexName = this.workflowIndexNamesBySecRole.get(secRole);
					if(indexName == null) {
						indexName = this.workflowIndexPrefix + "_" + INVALID_SECROLE_INDEX_SUFFIX;
					}
				}
			}
		} else {
			if(this.useActiveIndex && !workflow.getStatus().isTerminal()) {
				indexName += "_" + ACTIVE_INDEX_SUFFIX;
			}
		}
		return indexName;
	}
	
	protected String getTaskWriteIndexName(Task task, boolean activeIndex) {
		String indexName = this.taskIndexPrefix;
		if(this.useMultipleWorkflowAndTaskIndexes) {
			String secRole = task.getInputData() != null ? (String) task.getInputData().get(SECROLE_ATTRIBUTE_NAME) : null;
			if(secRole == null) {
				logger.error("**** Task with id '" + task.getTaskId() + "' does not have a secRole attribute");
			} else {
				if(activeIndex) {
					indexName = this.activeTaskIndexNamesBySecRole.get(secRole);
					if(indexName == null) {
						indexName = this.taskIndexPrefix + "_" + INVALID_SECROLE_INDEX_SUFFIX + "_" + ACTIVE_INDEX_SUFFIX;
					}
				} else {
					indexName = this.taskIndexNamesBySecRole.get(secRole);
					if(indexName == null) {
						indexName = this.taskIndexPrefix + "_" + INVALID_SECROLE_INDEX_SUFFIX;
					}
				}
			}
		} else {
			if(this.useActiveIndex && !task.getStatus().isTerminal()) {
				indexName += "_" + ACTIVE_INDEX_SUFFIX;
			}
		}
		return indexName;
	}

	protected void bulkIndexObject(final String index, final String docType, final String docId, final Object doc) {
        byte[] docBytes;
        try {
            docBytes = objectMapper.writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert {} '{}' to byte string", docType, docId);
            return;
        }

        IndexRequest request = new IndexRequest(index);
        request.id(docId);
        request.source(docBytes, XContentType.JSON);

        if(bulkRequests.get(docType) == null) {
            bulkRequests.put(docType, new BulkRequests(System.currentTimeMillis(), new BulkRequest()));
        }

        bulkRequests.get(docType).getBulkRequest().add(request);
        if (bulkRequests.get(docType).getBulkRequest().numberOfActions() >= this.indexBatchSize) {
            indexBulkRequest(docType);
        }
    }

    /**
     * Performs an index operation with a retry.
     *
     * @param request              The index request that we want to perform.
     * @param operationDescription The type of operation that we are performing.
     */
    private void indexWithRetry(final IndexRequest request, final String operationDescription) {

        try {
            new RetryUtil<IndexResponse>().retryOnException(() -> {
                try {
                    return elasticSearchClient.index(request, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, null, null, RETRY_COUNT, operationDescription, "indexWithRetry");
        } catch (Exception e) {
            Monitors.error(className, "index");
            logger.error("Failed to index {} for operation: {}", request.id(), operationDescription, e);
        }
    }

    private synchronized void indexBulkRequest(String docType) {
        if (bulkRequests.get(docType).getBulkRequest() != null && bulkRequests.get(docType).getBulkRequest().numberOfActions() > 0) {
            synchronized (bulkRequests.get(docType).getBulkRequest()) {
                indexWithRetry(bulkRequests.get(docType).getBulkRequest().get(), "Bulk Indexing " + docType, docType);
                bulkRequests.put(docType, new BulkRequests(System.currentTimeMillis(), new BulkRequest()));
            }
        }
    }

    /**
     * Performs an index operation with a retry.
     *
     * @param request              The index request that we want to perform.
     * @param operationDescription The type of operation that we are performing.
     */
    private void indexWithRetry(final BulkRequest request, final String operationDescription, String docType) {
        try {
            long startTime = Instant.now().toEpochMilli();
            new RetryUtil<BulkResponse>().retryOnException(() -> {
                try {
                    return elasticSearchClient.bulk(request, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, null, null, RETRY_COUNT, operationDescription, "indexWithRetry");
            long endTime = Instant.now().toEpochMilli();
            logger.debug("Time taken {} for indexing object of type: {}", endTime - startTime, docType);
            Monitors.recordESIndexTime("index_object", docType,endTime - startTime);
            Monitors.recordWorkerQueueSize("indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
            Monitors.recordWorkerQueueSize("logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
        } catch (Exception e) {
            Monitors.error(className, "index");
            logger.error("Failed to index {} for request type: {}", request, docType, e);
        }
    }

    /**
     * Flush the buffers if bulk requests have not been indexed for the past {@link ElasticSearchConfiguration#getAsyncBufferFlushTimeout()} seconds
     * This is to prevent data loss in case the instance is terminated, while the buffer still holds documents to be indexed.
     */
    private void flushBulkRequests() {
        bulkRequests.entrySet().stream()
            .filter(entry -> (System.currentTimeMillis() - entry.getValue().getLastFlushTime()) >= asyncBufferFlushTimeout * 1000)
            .filter(entry -> entry.getValue().getBulkRequest() != null && entry.getValue().getBulkRequest().numberOfActions() > 0)
            .forEach(entry -> {
                logger.debug("Flushing bulk request buffer for type {}, size: {}", entry.getKey(), entry.getValue().getBulkRequest().numberOfActions());
                indexBulkRequest(entry.getKey());
            });
    }

    
    private JsonObject getJsonObjectFromFile(String filepath) {
    	try {
    		JsonElement jsonElement = getJsonElementFromFile(filepath);
    		if(jsonElement != null) {
        		JsonObject jsonObject = jsonElement.getAsJsonObject();
        		return jsonObject;
    		}
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    private JsonElement getJsonElementFromFile(String filepath) {
    	try {
    		JsonParser parser = new JsonParser();
    		InputStream stream = this.getClass().getResourceAsStream(filepath);
    		JsonElement jsonElement = parser.parse(new InputStreamReader(stream));
    		return jsonElement;
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    private static class BulkRequests {
        private long lastFlushTime;
        private BulkRequestWrapper bulkRequestWrapper;

        long getLastFlushTime() {
            return lastFlushTime;
        }

        @SuppressWarnings("unused")
		public void setLastFlushTime(long lastFlushTime) {
            this.lastFlushTime = lastFlushTime;
        }

        public BulkRequestWrapper getBulkRequest() {
            return bulkRequestWrapper;
        }

        BulkRequests(long lastFlushTime, BulkRequest bulkRequestWrapper) {
            this.lastFlushTime = lastFlushTime;
            this.bulkRequestWrapper = new BulkRequestWrapper(bulkRequestWrapper);
        }
    }
}
