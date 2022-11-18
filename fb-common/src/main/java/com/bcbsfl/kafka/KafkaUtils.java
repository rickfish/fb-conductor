package com.bcbsfl.kafka;

import java.net.URL;
import java.util.Properties;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcbsfl.config.FBCommonConfiguration;

public class KafkaUtils {

	private static Logger logger = LoggerFactory.getLogger(KafkaUtils.class);

	public static Properties initKafka(FBCommonConfiguration configuration, String kafkaObjectType) throws Exception {
		Properties kafkaProperties = new Properties();
		/*
		 * Get the name of the file that contains all the authorization settings for the
		 * Kafka objects. If it is 'local' file, we will assume it is in the jar file so 
		 * we need to find the fully qualified path...
		 */
		String configFile = configuration.getKafkaAuthLocalConfigFile();
		if(configFile != null) {
			/*
			 * Now set that file into the system property that the KafkaProducer uses
			 */
			URL jaasConfigUrl = KafkaUtils.class.getResource(configFile);
			if(jaasConfigUrl != null) {
				configFile = jaasConfigUrl.toExternalForm();
			}
		} else {
			configFile = configuration.getKafkaAuthConfigFile();
		}
		if(configFile != null) {
			logger.info(kafkaObjectType + ": Setting 'java.security.auth.login.config' System property to " + configFile);
			System.setProperty("java.security.auth.login.config", configFile);
		} else {
			throw new Exception(kafkaObjectType + ": The '" + FBCommonConfiguration.KAFKA_AUTH_CONFIGFILE_PROPERTY_NAME + "' property could not be found. Cannot initialize Kafka.");
		}
		/*
		 * Get the name of the file that contains all the authorization settings for Kerberos. 
		 * If it is 'local' file, we will assume it is in the jar file so we need to find the 
		 * fully qualified path...
		 */
		configFile = configuration.getKrb5LocalConfigFile();
		if(configFile != null) {
			/*
			 * Now set that file into the system property that the KafkaProducer uses
			 */
			URL krbConfigUrl = KafkaUtils.class.getResource(configFile);
			if(krbConfigUrl != null) {
				configFile = krbConfigUrl.toExternalForm();
			}
		} else {
			configFile = configuration.getKrb5ConfigFile();
		}
		if(configFile != null) {
			logger.info(kafkaObjectType + ": Setting 'java.security.krb5.conf' System property to " + configFile);
			System.setProperty("java.security.krb5.conf", configFile);
		} else {
			logger.info(kafkaObjectType + ": Could not find a value for the '" + FBCommonConfiguration.KRB5_CONFIGFILE_PROPERTY_NAME + "' property. We will assume that file is not needed.");
		}
		/*
		 * Get the Kafka url
		 */
		String url = configuration.getKafkaURL();
		if(url != null) {
			logger.info(kafkaObjectType + ": We will use these bootstrap servers for Kafka: " + url);
			/*
			 * Get the Kafka topic to write to
			 */
			String topic = configuration.getKafkaTopic();
			if(topic != null) {
				/*
				 * Populate the properties that are used for both the producer and the consumer
				 */
				kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, url);
				kafkaProperties.put("security.protocol", "SASL_PLAINTEXT");
			} else {
				logger.info(kafkaObjectType + ": Could not find a value for the 'workflow.elasticsearch.kafka.topic' property. We will assume that file is not needed.");
			}
		} else {
			logger.info(kafkaObjectType + ": Could not find a value for the 'workflow.elasticsearch.kafka.url' property. We will assume that file is not needed.");
		}
		if(configuration.getKafkaMaxRequestSize() > 0) {
			kafkaProperties.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, configuration.getKafkaMaxRequestSize());
		}
		return kafkaProperties;
	}
}
