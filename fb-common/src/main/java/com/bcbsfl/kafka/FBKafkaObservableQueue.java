package com.bcbsfl.kafka;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.contribs.queue.kafka.KafkaObservableQueue;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.MessageEventFailure;

public class FBKafkaObservableQueue extends KafkaObservableQueue {
	private static final String ERROR_NOTIFICATION_EMAIL_ATTRNAME = "errorNotificationEmail";

	private final ObjectMapper objectMapper = new ObjectMapper();

	private KafkaErrorEmailSender errorEmailSender;

	@Inject
	public FBKafkaObservableQueue(String queueName, Configuration config) {
		super(queueName, config);
		/**
		 * This will send emails to recipients if there is an error processing an event and something is specified 
		 * on the 'errorNotificationEmail' attribute of the event payload.
		 */
		this.errorEmailSender = new KafkaErrorEmailSender();
	}

	protected void processFailure(String topicName, String errorTopicName, MessageEventFailure failure) {
		String notificationEmailAddress = getErrorNotificationEmailAddress(failure);
		this.errorEmailSender.newError(topicName, errorTopicName, notificationEmailAddress, failure);
	}

	private String getErrorNotificationEmailAddress(MessageEventFailure failure) {
		String emailAddress = null;
		String payload = failure.getMessage().getPayload();
		try {
			JsonNode payloadNode = this.objectMapper.readTree(payload);
			if(payloadNode != null) {
				JsonNode emailNode = payloadNode.get(ERROR_NOTIFICATION_EMAIL_ATTRNAME);
				if(emailNode != null) {
					emailAddress = emailNode.asText();
				}
			}
		} catch(Exception e) {
		}
		return emailAddress;
	}
	
}
