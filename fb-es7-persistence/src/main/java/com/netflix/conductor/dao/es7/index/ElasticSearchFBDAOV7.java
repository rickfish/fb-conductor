/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.netflix.conductor.dao.es7.index;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.common.run.FBTaskSummary;
import com.bcbsfl.common.run.FBWorkflowSummary;
import com.bcbsfl.kafka.KafkaElasticSearchProducer;
import com.bcbsfl.mail.EmailSender;
import com.bcbsfl.util.FBElasticSearchDocTagger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;

/**
 * @author Viren
 */
@Trace
@Singleton
public class ElasticSearchFBDAOV7 extends ElasticSearchFBRestDAOV7 implements IndexDAO {

	private static Logger logger = LoggerFactory.getLogger(ElasticSearchFBDAOV7.class);

	private KafkaElasticSearchProducer kafkaProducer = null;
	private FBElasticSearchDocTagger fbDocTagger = null;
	private ExecutionDAO executionDAO = null;
	
	private EmailSender emailSender;
	
	/**
	 * Whether or not to write the ElasticSearch document to a Kafka topic instead of direct to ElasticSearch
	 */
	private boolean useKafkaTopicInsteadOfElasticSearch = false;
	
	/**
	 * Whether or not to write the ElasticSearch document to an 'active' index if the workflow/task is not in terminal state
	 */
	private boolean useActiveIndex = false;
			
	@Inject
	public ElasticSearchFBDAOV7(RestClientBuilder restClientBuilder, ElasticSearchConfiguration config, ObjectMapper objectMapper, ExecutionDAO executionDAO) {
    	super(restClientBuilder, config, objectMapper);
    	this.executionDAO = executionDAO;
    	this.fbDocTagger = new FBElasticSearchDocTagger();
		this.kafkaProducer = new KafkaElasticSearchProducer();
		this.emailSender = new EmailSender();
		
		if(kafkaProducer.useKafka()) {
			this.useKafkaTopicInsteadOfElasticSearch = true;
			logger.info("We will send all ElasticSearch documents to a Kafka topic instead of directly to ElasticSearch");
		} else {
			this.useKafkaTopicInsteadOfElasticSearch = false;
			logger.info("We will send all ElasticSearch documents directly to ElasticSearch (not to a Kafka topic)");
			this.useActiveIndex = config.useActiveIndex();
			logger.info("We will send all workflow/task ElasticSearch documents that are 'active' to an active index and the ones that are in terminal state to the non-active index");
		}
	}

	public long getKafkaLagTime() {
		long lagTime = 0;
		String timestampString = null;
		long kafkaTimestamp = 0;
    	long esTimestamp = 0;
		
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(1);
        SortOrder order = SortOrder.DESC;
        searchSourceBuilder.sort(new FieldSortBuilder("@timestamp").order(order));

        SearchRequest searchRequest = new SearchRequest(taskIndexPrefix);
        searchRequest.source(searchSourceBuilder);
        try {
	        SearchResponse response = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);
	        if(response != null && response.getHits() != null && response.getHits().getHits() != null &&  response.getHits().getHits().length > 0) {
	        	Map<String, Object> sourceAsMap = response.getHits().getHits()[0].getSourceAsMap(); 
	            Object o = sourceAsMap.get("@timestamp");
	            if(o != null) {
	            	timestampString = o.toString();
	           		try {
	           			Date date = ES_TIMESTAMP_FORMAT.parse(timestampString);
	    	            o = sourceAsMap.get("kafka_timestamp");
	    	            if(o != null) {
	    	            	kafkaTimestamp = Long.parseLong((String)o);
	    	            	esTimestamp = date.getTime();
	    	            	lagTime = esTimestamp - kafkaTimestamp;
	    	            }
	           		} catch(Exception e) {
	                	logger.error("error trying to get kafka lag time", e);
	           		}
	            }
	        } else {
	        	logger.error("error trying to get kafka lag time...could not find an ElasticSearch document at all");
	        }
        } catch(Exception e) {
        	logger.error("error trying to get kafka lag time", e);
        }
//        System.out.println("LagTime: " + lagTime + ", task: " + taskId + ", timestamp: " + timestampString + ", esTimestamp: " + esTimestamp + ", kafkaTimestamp: " + kafkaTimestamp + ", took " + (System.currentTimeMillis() - timerStart) + "ms to execute");
        return lagTime;
	}
	
	@Override
	protected void clusterNotAvailable(Exception e) throws Exception {
		this.emailSender.sendExceptionEmail("Error initializing ElasticSearch. Cluster not available.",  e);
		throw e;
	}

    @Override
    public void indexTask(Task task) {
        FBTaskSummary summary = new FBTaskSummary(task);
        this.fbDocTagger.populateFBTags(summary.getTags(), task.getInputData(), task.getOutputData());
        /*
         * Some tasks do not have secRole populated (DECISION, JOIN, FORK, etc) so populate them based on their workflow's secRole
         * which should be non-null.
         */
        if(summary.getTags().get(SECROLE_ATTRIBUTE_NAME) == null) {
			String errorMessage = null;
        	try {
        		Workflow workflow = this.executionDAO.getWorkflow(task.getWorkflowInstanceId());
				String secRole = null;
				if(workflow != null) {
					secRole = this.getSecRoleFromWorkflowInput(workflow.getInput());
					if(secRole != null) {
        				summary.getTags().put(SECROLE_ATTRIBUTE_NAME, secRole);
					}
	        	} else {
	        		errorMessage = "WorkflowId: " + summary.getWorkflowId() + ", taskid: " + summary.getTaskId() + ", taskType: " + summary.getTaskType() + ", status: " + task.getStatus()  
					+ ",  no secRole found in the task so we tried to get the one in its workflow but could not find the workflow in the database so the task will not have a secRole.";
	        	}
        	} catch(Exception e) {
        		errorMessage = "WorkflowId: " + summary.getWorkflowId() + ", taskid: " + summary.getTaskId() + ", taskType: " + summary.getTaskType() + ", status: " + task.getStatus()  
				+ ",  no secRole found in the task so we want to get the one in its workflow. While getting the workflow we got this exception: "
				+ e.getMessage() + ", so the task will not have a secRole.";
        	}
			if(errorMessage != null) {
				logger.error(errorMessage);
				this.emailSender.sendErrorMessageEmail("task '" + task.getTaskId() + "' (" + task.getStatus() + ") No secRole for task", errorMessage);
        	}
        }
        if(this.useKafkaTopicInsteadOfElasticSearch) {
        	this.kafkaProducer.sendToKafka(summary);
        } else {
        	indexObject(getTaskWriteIndexName(task, this.useActiveIndex && !task.getStatus().isTerminal()), 
      			TASK_DOC_TYPE, task.getTaskId(), summary);
        	/*
        	 * If we are using an 'active' index for non-terminal tasks, put any non-terminal tasks into the active index. Otherwise, put it into the non-active
        	 * index and remove it from the active one so there is only ever one copy of the task.
        	 */
        	if(this.useActiveIndex && task.getStatus().isTerminal()) {
        		removeDocumentFromIndex(getTaskWriteIndexName(task, true), task.getTaskId());
        	}
        }
    }

	private String getSecRoleFromWorkflowInput(Map<String, Object> input) {
    	String secRole = null;
    	if(input != null) {
        	for(String key : input.keySet()) {
        		if(SECROLE_ATTRIBUTE_NAME.equals(key) && input.get(key) instanceof String) {
        			secRole = (String) input.get(key);
    				if(StringUtils.isNotBlank(secRole)) {
    					break;
    				}
        		}
        	}
    	}
    	return secRole;
    }
    
    @Override
    public void indexWorkflow(Workflow workflow) {
        FBWorkflowSummary summary = new FBWorkflowSummary(workflow);
        this.fbDocTagger.populateFBTags(summary.getTags(), workflow.getInput(), workflow.getOutput());
        if(this.useKafkaTopicInsteadOfElasticSearch) {
        	this.kafkaProducer.sendToKafka(summary);
        } else {
        	indexObject(getWorkflowWriteIndexName(workflow, this.useActiveIndex && !workflow.getStatus().isTerminal()), 
      			WORKFLOW_DOC_TYPE, workflow.getWorkflowId(), summary);
        	/*
        	 * If we are using an 'active' index for non-terminal workflows, put any non-terminal workflows into the active index. Otherwise, put it into the non-active
        	 * index and remove it from the active one so there is only ever one copy of the workflow.
        	 */
        	if(this.useActiveIndex && workflow.getStatus().isTerminal()) {
        		removeDocumentFromIndex(getWorkflowWriteIndexName(workflow, true), workflow.getWorkflowId());
        	}
        }
    }

    @Override
    public void addMessage(String queue, Message message) {
        if(this.useKafkaTopicInsteadOfElasticSearch) {
        	this.kafkaProducer.sendMessageToKafka(queue, message.getId(), message.getPayload());
        } else {
            super.addMessage(queue, message);
        }
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {
        if(this.useKafkaTopicInsteadOfElasticSearch) {
        	this.kafkaProducer.sendToKafka(eventExecution);
        } else {
        	super.addEventExecution(eventExecution);
        }
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> taskExecLogs) {
        if(this.useKafkaTopicInsteadOfElasticSearch) {
        	this.kafkaProducer.sendToKafka(taskExecLogs);
        } else {
        	super.addTaskExecutionLogs(taskExecLogs);
        }
    }
}
