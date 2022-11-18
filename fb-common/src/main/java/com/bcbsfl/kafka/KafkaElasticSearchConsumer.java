package com.bcbsfl.kafka;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.config.FBCommonConfiguration;
import com.bcbsfl.config.SystemPropertiesFBCommonConfiguration;
import com.bcbsfl.mail.EmailSender;

public class KafkaElasticSearchConsumer {

	private static Logger logger = LoggerFactory.getLogger(KafkaElasticSearchConsumer.class);
	/**
	 * A 'lock' so that two threads will not try to initialize Kafka at the same time
	 */
	private Long kafkaInitLock = new Long(0);
	/**
	 * The Kafka topic to send documents to
	 */
	private String kafkaTopic = null;

	private KafkaConsumer<String, String> kafkaConsumer = null;

	private FBCommonConfiguration configuration = new SystemPropertiesFBCommonConfiguration();
	
	private EmailSender emailSender;
	
	public KafkaElasticSearchConsumer() {
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
     * Initialize Kafka so we can use the Consumer to get information about the topic
     */
	private void initKafka() {
		/*
		 * Ensure that two threads don't try to init at the same time
		 */
 		synchronized(this.kafkaInitLock) {
 			/*
 			 * Only init if  not already initialized
 			 */
			if(this.kafkaConsumer == null) {
				try {
					String topic = this.configuration.getKafkaTopic();
					if(topic != null) {
						this.kafkaTopic = topic;
						Properties kafkaProperties = KafkaUtils.initKafka(this.configuration, "KafkaElasticSearchConsumer");
						
						/*
						 * Create the KafkaConsumer object. There will only be one and it will
						 * be re-used because closing it and creating it is time-intensive. A
						 * shutdown hook will be registered to do the close upon system exit.
						 */
						kafkaProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
						kafkaProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
						kafkaProperties.put(ConsumerConfig.GROUP_ID_CONFIG, this.configuration.getKafkaConsumerGroup());
						kafkaProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
						this.kafkaConsumer = new KafkaConsumer<String, String>(kafkaProperties);
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

	public long getTopicLag() {
		long lag = 0;
		Map<Integer, Long> lags = new HashMap<Integer, Long>();
	    List<PartitionInfo> partitionInfoList = this.kafkaConsumer.partitionsFor(this.kafkaTopic);
	    List<TopicPartition> partitions = partitionInfoList.stream().map(pi -> new TopicPartition(this.kafkaTopic, pi.partition())).collect(Collectors.toList());
	    this.kafkaConsumer.assign(partitions);
	    this.kafkaConsumer.poll(Duration.ZERO);
	    partitions.forEach(partition -> {
	        OffsetAndMetadata committed = this.kafkaConsumer.committed(partition);
	    	lags.put(partition.partition(), committed.offset());
	    });;
	    this.kafkaConsumer.seekToEnd(partitions);
	    for(TopicPartition partition : partitions) {
	    	Long committedOffset = lags.get(partition.partition());
	    	long endingOffset = this.kafkaConsumer.position(partition);
	    	long partitionLag = endingOffset - committedOffset;
	    	if(partitionLag > lag) {
	    		lag = partitionLag;
	    	}
	    }
	    return lag;
	}
	
	/**
	 * Register a shutdown hook that will be invoked by the JVM when it is shutting down. That
	 * allows us to close the KafkaProducer object before exiting.
	 */
    private void registerShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("ShutDownHook - Flushing and closing producer");
            this.kafkaConsumer.close();
           logger.info("ShutDownHook - Consumer and closed");
        }));
    }
}
