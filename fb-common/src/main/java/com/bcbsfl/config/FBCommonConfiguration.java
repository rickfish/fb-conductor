package com.bcbsfl.config;

import com.bcbsfl.util.RuntimeEnvironment;

public interface FBCommonConfiguration extends BaseConfig {

	String ENVIRONMENT_PROPERTY_NAME = "environment";
    String ENVIRONMENT_DEFAULT_VALUE = "local";
    
    String USERSERVICE_URL_PROPERTY_NAME = "USERSERVICE.URL";
    String USERSERVICE_URL_DEFAULT_VALUE = null;
    
    String USERSERVICE_USERNAME_PROPERTY_NAME = "USERSERVICE.USERID";
    String USERSERVICE_USERNAME_DEFAULT_VALUE = null;
    
    String USERSERVICE_PASSWORD_PROPERTY_NAME = "USERSERVICE.PASSWORD";
    String USERSERVICE_PASSWORD_DEFAULT_VALUE = null;
    
    String REDIS_HOST_PROPERTY_NAME = "REDIS.HOST";
    String REDIS_HOST_DEFAULT_VALUE = null;
    
    String REDIS_PORT_PROPERTY_NAME = "REDIS.PORT";
    int REDIS_PORT_DEFAULT_VALUE = 6379;

    String ENABLE_AUTH_CHECKING_PROPERTY_NAME = "enable_authorization_checking";
    boolean ENABLE_AUTH_CHECKING_DEFAULT_VALUE = false;
    
    String ENABLE_DATA_ENTITLEMENT_VALIDATION_PROPERTY_NAME = "enable_data_entitlement_validation";
    boolean ENABLE_DATA_ENTITLEMENT_VALIDATION_DEFAULT_VALUE = false;

    String EMAIL_INSTEAD_OF_403_PROPERTY_NAME = "email_instead_of_403";
    boolean EMAIL_INSTEAD_OF_403_DEFAULT_VALUE = false;
    
	String KAFKA_URL_PROPERTY_NAME = "workflow.elasticsearch.kafka.url";
    String KAFKA_URL_DEFAULT_VALUE = null;
    
    String KAFKA_JAAS_USERNAME_PROPERTY_NAME = "workflow.elasticsearch.kafka.jaas.username";
    String KAFKA_JAAS_USERNAME_DEFAULT_VALUE = null;
    
    String KAFKA_JAAS_PASSWORD_PROPERTY_NAME = "workflow.elasticsearch.kafka.jaas.password";
    String KAFKA_JAAS_PASSWORD_DEFAULT_VALUE = null;
    
    String KAFKA_AUTH_LOCAL_CONFIGFILE_PROPERTY_NAME = "java.security.auth.login.localconfigfile";
    String KAFKA_AUTH_LOCAL_CONFIGFILE_DEFAULT_VALUE = null;
    
    String KAFKA_AUTH_CONFIGFILE_PROPERTY_NAME = "java.security.auth.login.configfile";
    String KAFKA_AUTH_CONFIGFILE_DEFAULT_VALUE = null;
    
    String KAFKA_TOPIC_PROPERTY_NAME = "workflow.elasticsearch.kafka.topic";
    String KAFKA_TOPIC_DEFAULT_VALUE = "etd-conductor";

    String KAFKA_CONSUMERGROUP_PROPERTY_NAME = "workflow.elasticsearch.kafka.consumergroup";
    String KAFKA_CONSUMERGROUP_DEFAULT_VALUE = "elk-conductor";

    String KAFKA_MAX_REQUEST_SIZE_PROPERTY_NAME = "kafka.max.request.size";
    int KAFKA_MAX_REQUEST_SIZE_DEFAULT_VALUE = -1;

    String KRB5_LOCAL_CONFIGFILE_PROPERTY_NAME = "java.security.krb5.localconfigfile";
    String KRB5_LOCAL_CONFIGFILE_DEFAULT_VALUE = null;

    String KRB5_CONFIGFILE_PROPERTY_NAME = "java.security.krb5.configfile";
    String KRB5_CONFIGFILE_DEFAULT_VALUE = null;

    String ENABLE_FB_ELASTICSEARCH_PROPERTY_NAME = "workflow.elasticsearch.fb.module.enable";
    boolean ENABLE_FB_ELASTICSEARCH_DEFAULT_VALUE = false;
    
    default RuntimeEnvironment getRuntimeEnvironment() {
    	String pipelineEnvironment = getProperty(ENVIRONMENT_PROPERTY_NAME, ENVIRONMENT_DEFAULT_VALUE);
    	return RuntimeEnvironment.fromPipelineEnv(pipelineEnvironment); 
    }
    default String getPipelinedEnvironmentName() {
    	return getProperty(ENVIRONMENT_PROPERTY_NAME, ENVIRONMENT_DEFAULT_VALUE).toUpperCase();
    }
    default String getUserServiceURL() {
        return getProperty(USERSERVICE_URL_PROPERTY_NAME, USERSERVICE_URL_DEFAULT_VALUE);
    }

    default String getUserServiceUsername() {
        return getProperty(USERSERVICE_USERNAME_PROPERTY_NAME, USERSERVICE_USERNAME_DEFAULT_VALUE);
    }

    default String getUserServicePassword() {
        return getProperty(USERSERVICE_PASSWORD_PROPERTY_NAME, USERSERVICE_PASSWORD_DEFAULT_VALUE);
    }

    default String getRedisHost() {
        return getProperty(REDIS_HOST_PROPERTY_NAME, REDIS_HOST_DEFAULT_VALUE);
    }

    default int getRedisPort() {
        return getIntProperty(REDIS_PORT_PROPERTY_NAME, REDIS_PORT_DEFAULT_VALUE);
    }

    default boolean isBlueRedisCacheToBeUsed() {
    	return null != getRedisHost();
    }
    
    default boolean enableAuthorizationChecking() {
        return getBooleanProperty(ENABLE_AUTH_CHECKING_PROPERTY_NAME, ENABLE_AUTH_CHECKING_DEFAULT_VALUE);
    }

    default boolean enableDataEntitlementValidation() {
        return getBooleanProperty(ENABLE_DATA_ENTITLEMENT_VALIDATION_PROPERTY_NAME, ENABLE_DATA_ENTITLEMENT_VALIDATION_DEFAULT_VALUE);
    }

    default boolean sendEmailInsteadOfReturning403() {
        return getBooleanProperty(EMAIL_INSTEAD_OF_403_PROPERTY_NAME, EMAIL_INSTEAD_OF_403_DEFAULT_VALUE);
    }

    default String getKafkaURL() {
        return getProperty(KAFKA_URL_PROPERTY_NAME, KAFKA_URL_DEFAULT_VALUE);
    }

    default String getKafkaJaasUsername() {
        return getProperty(KAFKA_JAAS_USERNAME_PROPERTY_NAME, KAFKA_JAAS_USERNAME_DEFAULT_VALUE);
    }

    default String getKafkaJaasPassword() {
        return getProperty(KAFKA_JAAS_PASSWORD_PROPERTY_NAME, KAFKA_JAAS_PASSWORD_DEFAULT_VALUE);
    }

    default String getKafkaAuthLocalConfigFile() {
        return getProperty(KAFKA_AUTH_LOCAL_CONFIGFILE_PROPERTY_NAME, KAFKA_AUTH_LOCAL_CONFIGFILE_DEFAULT_VALUE);
    }

    default String getKafkaAuthConfigFile() {
        return getProperty(KAFKA_AUTH_CONFIGFILE_PROPERTY_NAME, KAFKA_AUTH_CONFIGFILE_DEFAULT_VALUE);
    }

    default String getKrb5LocalConfigFile() {
        return getProperty(KRB5_LOCAL_CONFIGFILE_PROPERTY_NAME, KRB5_LOCAL_CONFIGFILE_DEFAULT_VALUE);
    }

    default String getKrb5ConfigFile() {
        return getProperty(KRB5_CONFIGFILE_PROPERTY_NAME, KRB5_CONFIGFILE_DEFAULT_VALUE);
    }

    default String getKafkaTopic() {
        return getProperty(KAFKA_TOPIC_PROPERTY_NAME, KAFKA_TOPIC_DEFAULT_VALUE);
    }

    default String getKafkaConsumerGroup() {
        return getProperty(KAFKA_CONSUMERGROUP_PROPERTY_NAME, KAFKA_CONSUMERGROUP_DEFAULT_VALUE);
    }

    default int getKafkaMaxRequestSize() {
        return getIntProperty(KAFKA_MAX_REQUEST_SIZE_PROPERTY_NAME, KAFKA_MAX_REQUEST_SIZE_DEFAULT_VALUE);
    }

    default boolean useKafka() {
    	return null != getKafkaURL();
    }

    default boolean enableFBElasticSearch() {
    	return getBooleanProperty(ENABLE_FB_ELASTICSEARCH_PROPERTY_NAME, ENABLE_FB_ELASTICSEARCH_DEFAULT_VALUE);
    }
}
