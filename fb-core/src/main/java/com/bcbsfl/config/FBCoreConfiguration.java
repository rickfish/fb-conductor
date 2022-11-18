package com.bcbsfl.config;

import com.bcbsfl.util.RuntimeEnvironment;

public interface FBCoreConfiguration extends FBBaseCoreConfig {

	String ENVIRONMENT_PROPERTY_NAME = "environment";
    String ENVIRONMENT_DEFAULT_VALUE = "local";
    
    String EMAIL_INSTEAD_OF_403_PROPERTY_NAME = "email_instead_of_403";
    boolean EMAIL_INSTEAD_OF_403_DEFAULT_VALUE = false;

    String EMAIL_SMTP_HOST_PROPERTY_NAME = "email.smtp.host";
    String EMAIL_SMTP_HOST_DEFAULT_VALUE = "imscorp.bcbsfl.com";
    
    String EMAIL_ADDRESS_FROM_PROPERTY_NAME = "email.address.from";
    String EMAIL_ADDRESS_FROM_DEFAULT_VALUE = "ConflixAdmins@bcbsfl.com";
    
    String EMAIL_ADDRESS_REPLYTO_PROPERTY_NAME = "email.address.replyto";
    String EMAIL_ADDRESS_REPLYTO_DEFAULT_VALUE = "ConflixAdmins@bcbsfl.com";
    
    String EMAIL_ADDRESS_TO_PROPERTY_NAME = "email.address.to";
    String EMAIL_ADDRESS_TO_DEFAULT_VALUE = null;
    
    String EMAIL_ADDRESS_TO_AUTHERROR_ONLY_PROPERTY_NAME = "email.address.to.autherror.only";
    String EMAIL_ADDRESS_TO_AUTHERROR_ONLY_DEFAULT_VALUE = null;
    
    String EMAIL_DUPLICATE_INTERVAL_PROPERTY_NAME = "email.duplicate.interval.minutes";
    int EMAIL_DUPLICATE_INTERVAL_DEFAULT_VALUE = 30;

    String EMAIL_ALERT_INTERVAL_PROPERTY_NAME = "email.alert.interval.minutes";
    int EMAIL_ALERT_INTERVAL_DEFAULT_VALUE = 5;

    String EMAIL_ALERT_EMAILS_THRESHOLD_PROPERTY_NAME = "email.alert.emails.threshold";
    int EMAIL_ALERT_EMAILS_THRESHOLD_DEFAULT_VALUE = 10;

    String KAFKA_EVENTS_ERRORS_PER_EMAIL_PROPERTY_NAME = "kafka.events.errorsPerEmail";
    int KAFKA_EVENTS_ERRORS_PER_EMAIL_DEFAULT_VALUE = 100;

    String KAFKA_EVENTS_MINUTES_UNTIL_ERROR_EMAIL_PROPERTY_NAME = "kafka.events.minutesUntilErrorEmail";
    int KAFKA_EVENTS_MINUTES_UNTIL_ERROR_EMAIL_DEFAULT_VALUE = 30;

    String KAFKA_EVENTS_ERROR_EMAIL_ADDL_RECIPIENTS_PROPERTY_NAME = "kafka.events.errorEmailsAddlRecipients";
    String KAFKA_EVENTS_ERROR_EMAIL_ADDL_RECIPIENTS_DEFAULT_VALUE = "";

    default RuntimeEnvironment getRuntimeEnvironment() {
    	String pipelineEnvironment = getProperty(ENVIRONMENT_PROPERTY_NAME, ENVIRONMENT_DEFAULT_VALUE);
    	return RuntimeEnvironment.fromPipelineEnv(pipelineEnvironment); 
    }
    default String getPipelinedEnvironmentName() {
    	return getProperty(ENVIRONMENT_PROPERTY_NAME, ENVIRONMENT_DEFAULT_VALUE).toUpperCase();
    }

    default boolean sendEmailInsteadOfReturning403() {
        return getBooleanProperty(EMAIL_INSTEAD_OF_403_PROPERTY_NAME, EMAIL_INSTEAD_OF_403_DEFAULT_VALUE);
    }

    default String getEmailSmtpHost() {
    	return getProperty(EMAIL_SMTP_HOST_PROPERTY_NAME, EMAIL_SMTP_HOST_DEFAULT_VALUE);
    }

    default String getEmailAddressFrom() {
    	return getProperty(EMAIL_ADDRESS_FROM_PROPERTY_NAME, EMAIL_ADDRESS_FROM_DEFAULT_VALUE);
    }

    default String getEmailAddressReplyTo() {
    	return getProperty(EMAIL_ADDRESS_REPLYTO_PROPERTY_NAME, EMAIL_ADDRESS_REPLYTO_DEFAULT_VALUE);
    }

    default String getEmailAddressTo() {
    	return getProperty(EMAIL_ADDRESS_TO_PROPERTY_NAME, EMAIL_ADDRESS_TO_DEFAULT_VALUE);
    }
    
    default String getAuthErrorOnlyEmailAddress() {
    	return getProperty(EMAIL_ADDRESS_TO_AUTHERROR_ONLY_PROPERTY_NAME, EMAIL_ADDRESS_TO_AUTHERROR_ONLY_DEFAULT_VALUE);
    }
    
    default int getEmailDuplicateIntervalMinutes() {
    	return getIntProperty(EMAIL_DUPLICATE_INTERVAL_PROPERTY_NAME, EMAIL_DUPLICATE_INTERVAL_DEFAULT_VALUE);
    }
    /**
     * Return the number of errors per error email for Kafka event errors
     * @return
     */
    default int getKafkaEventsErrorsPerEmail() {
        return getIntProperty(KAFKA_EVENTS_ERRORS_PER_EMAIL_PROPERTY_NAME,
        		KAFKA_EVENTS_ERRORS_PER_EMAIL_DEFAULT_VALUE);
    }

    /**
     * Return the number of minutes until we send out the next error email for Kafka event errors
     * @return
     */
    default int getKafkaEventsMinutesUntilErrorEmail() {
        return getIntProperty(KAFKA_EVENTS_MINUTES_UNTIL_ERROR_EMAIL_PROPERTY_NAME,
        		KAFKA_EVENTS_MINUTES_UNTIL_ERROR_EMAIL_DEFAULT_VALUE);
    }

    /**
     * Return the additional recipients that will get the Kafka events error emails
     * @return
     */
    default String getKafkaEventsErrorEmailAddlRecipients() {
        return getProperty(KAFKA_EVENTS_ERROR_EMAIL_ADDL_RECIPIENTS_PROPERTY_NAME,
        		KAFKA_EVENTS_ERROR_EMAIL_ADDL_RECIPIENTS_DEFAULT_VALUE);
    }

}
