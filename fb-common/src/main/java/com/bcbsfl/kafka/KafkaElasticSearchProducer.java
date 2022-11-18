package com.bcbsfl.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.common.run.FBTaskSummary;
import com.bcbsfl.common.run.FBWorkflowSummary;
import com.bcbsfl.config.FBCommonConfiguration;
import com.bcbsfl.config.SystemPropertiesFBCommonConfiguration;
import com.bcbsfl.mail.EmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;

public class KafkaElasticSearchProducer {

	private static Logger logger = LoggerFactory.getLogger(KafkaElasticSearchProducer.class);
	
	private ObjectMapper objectMapper;
	/**
	 * A 'lock' so that two threads will not try to initialize Kafka at the same time
	 */
	private Long kafkaInitLock = new Long(0);
	/**
	 * The Kafka topic to send documents to
	 */
	private String kafkaTopic = null;

	private KafkaProducer<String, String> kafkaProducer = null;

	private FBCommonConfiguration configuration = new SystemPropertiesFBCommonConfiguration();
	
	private EmailSender emailSender;
	
	public KafkaElasticSearchProducer() {
    	this.objectMapper = new ObjectMapper();
    	this.emailSender = new EmailSender();
    	if(this.configuration.useKafka()) {
    		String kafkaUrl = this.configuration.getKafkaURL();
    		if(kafkaUrl != null) {
    			initKafka();
    		}
		}
	}

	public boolean useKafka() {
		return this.configuration.useKafka();
	}
	
    /**
     * Initialize Kafka so we can send the ElasticSearch documents to a Kafka topic. There will be
     * a logstash layer that will move them from Kafka to ElasticSearch
     */
	private void initKafka() {
		/*
		 * Ensure that two threads don't try to init at the same time
		 */
 		synchronized(this.kafkaInitLock) {
 			/*
 			 * Only init if  not already initialized
 			 */
			if(this.kafkaProducer == null) {
				try {
					String topic = this.configuration.getKafkaTopic();
					if(topic != null) {
						this.kafkaTopic = topic;
						Properties kafkaProperties = KafkaUtils.initKafka(this.configuration, "KafkaElasticSearchProducer");
						kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
						kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
						this.kafkaProducer = new KafkaProducer<String, String>(kafkaProperties);
						registerShutDownHook();
					} else {
						logger.info("Could not find a value for the 'workflow.elasticsearch.kafka.topic' property. We will assume that file is not needed.");
					}
				} catch(Exception e) {
					e.printStackTrace();
					this.emailSender.sendExceptionEmail("Error initializing Kafka",  e);
				}
			}
		}
	}

	/**
	 * Register a shutdown hook that will be invoked by the JVM when it is shutting down. That
	 * allows us to close the KafkaProducer object before exiting.
	 */
    private void registerShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("ShutDownHook - Flushing and closing producer");

            this.kafkaProducer.flush();
            this.kafkaProducer.close();

           logger.info("ShutDownHook - Producer flushed and closed");
        }));
    }

    public void sendToKafka(String workflowId, Map<String, Object> updates) {
		try {
			sendToKafka(workflowId, "workflow_updates", this.objectMapper.writeValueAsString(updates));
		} catch(Exception e) {
			logger.error("Failed to send workflow to Kafka: {}", workflowId, e);
			this.emailSender.sendExceptionEmail("Failed to send Workflow to Kafka, workflow id: " + workflowId, e);
		}
    }
    
	public void sendToKafka(FBWorkflowSummary summary) {
		try {
			sendToKafka(summary.getWorkflowId(), "workflow", this.objectMapper.writeValueAsString(summary));
		} catch(Exception e) {
			logger.error("Failed to send workflow to Kafka: {}", summary.getWorkflowId(), e);
			this.emailSender.sendExceptionEmail("Failed to send Workflow to Kafka, workflow id: " + summary.getWorkflowId(), e);
		}
	}

	public void sendToKafka(FBTaskSummary summary) {
		try {
			sendToKafka(summary.getTaskId(), "task", this.objectMapper.writeValueAsString(summary));
		} catch(Exception e) {
			logger.error("Failed to send task to Kafka: {}", summary.getTaskId(), e);
			this.emailSender.sendExceptionEmail("Failed to send Task to Kafka, task id: " + summary.getTaskId(), e);
		}
	}

    public void sendMessageToKafka(String queue, String id, String payload) {
		try {
	        Map<String, Object> doc = new HashMap<>();
	        doc.put("messageId", id);
	        doc.put("payload", payload);
	        doc.put("queue", queue);
	        doc.put("created", System.currentTimeMillis());
			sendToKafka(id, "message", this.objectMapper.writeValueAsString(doc));
		} catch(Exception e) {
			logger.error("Failed to send message to Kafka: {}", id, e);
			this.emailSender.sendExceptionEmail("Failed to send Message to Kafka, message id: " + id, e);
		}
    }

    public void sendToKafka(EventExecution event) {
        String id = event.getName() + "." + event.getEvent() + "." + event.getMessageId() + "." + event.getId();
		try {
			sendToKafka(id, "event", this.objectMapper.writeValueAsString(event));
		} catch(Exception e) {
			logger.error("Failed to send event to Kafka: {}", id, e);
			this.emailSender.sendExceptionEmail("Failed to send Event to Kafka, event message id: " + event.getMessageId(), e);
		}
	}

    public void sendToKafka(List<TaskExecLog> taskExecLogs) {
        if (taskExecLogs.isEmpty()) {
            return;
        }

        String id = taskExecLogs.get(0).getTaskId();
		try {
			sendToKafka(id, "task_log", this.objectMapper.writeValueAsString(taskExecLogs));
		} catch(Exception e) {
			logger.error("Failed to send task_log to Kafka: {}", id, e);
			this.emailSender.sendExceptionEmail("Failed to send TaskLog to Kafka, TaskLog id: " + id, e);
		}
    }

    public void sendToKafka(String id, String docType, String value) {
		try {
			if(this.kafkaProducer == null) {
				initKafka();
			}
			ProducerRecord<String, String> record = new ProducerRecord<String, String>(this.kafkaTopic, id, value);
			this.kafkaProducer.send(record, new Callback() {
				public void onCompletion(RecordMetadata metadata, Exception exception) {
					if(exception != null) {
						logger.error("Got this exception while processing value: " + record, exception);
					}
				}
			});
		} catch (Throwable e) {
			logger.error("Failed to send " + docType + " to Kafka: {}", id, e);
			this.emailSender.sendExceptionEmail("Failed to send " + docType + " to Kafka, id: " + id, e);
		} 
	}

	public String getKafkaTopic() {
		return kafkaTopic;
	}

	public void setKafkaTopic(String kafkaTopic) {
		this.kafkaTopic = kafkaTopic;
	}
}
