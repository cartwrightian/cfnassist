package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import tw.com.entity.CFNAssistNotification;
import tw.com.exceptions.CfnAssistException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SNSNotificationSender implements NotificationSender {
	private static final Logger logger = LoggerFactory.getLogger(SNSNotificationSender.class);

	public static final String TOPIC_NAME = "CFN_ASSIST_NOTIFICATIONS";
	
	private SnsClient snsClient;

	// stateful to limit number of AWS API calls we make
	private String topicANR ="";

	public SNSNotificationSender(SnsClient snsClient) {
		this.snsClient = snsClient;
	}

	public String getTopicARN() {
		try {
			if (topicANR.isEmpty()) {
				ListTopicsResponse topics = snsClient.listTopics();
				for(Topic topic : topics.topics()) {
					String foundArn = topic.topicArn();
					if (foundArn.contains(TOPIC_NAME)) {
						logger.info("Found notification topic for SNS, ARN is: " + foundArn);
						topicANR =  foundArn;
						break;
					}
				}
			}
			if (topicANR.isEmpty()) {
				logger.info("Did not find notification topic for SNS, to receive updates create topic: " + TOPIC_NAME);
			}
			return topicANR;
		} 
		catch (AuthorizationErrorException authException) {
			logger.error("Did not send SNS notification. You may need to update permissions for user via IAM. Exception was " + authException);
			return "";
		}
	}

	public String sendNotification(CFNAssistNotification notification) throws CfnAssistException {
		String topicArn = getTopicARN();
		if (topicArn.isEmpty()) {
			logger.info("Will not send notification as sns topic not found, topic is: " + TOPIC_NAME);
			return "";
		}
		
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			logger.info("Send notification: " + notification);
			String json = objectMapper.writeValueAsString(notification);

			PublishRequest request = PublishRequest.builder().topicArn(topicArn).message(json).build();
			PublishResponse result = snsClient.publish(request);
			logger.info(String.format("Send message on topic %s with id %s", TOPIC_NAME, result.messageId()));
			return result.messageId();
		}
		catch (JsonProcessingException jsonException) {
			throw new CfnAssistException("Unable to create notification JSON " + jsonException.toString());
		}
		catch (AuthorizationErrorException authException) {
			logger.error("Did not send SNS notification. You may need to update permissions for user via IAM. Exception was " 
					+ authException);
			return "";
		}
	}

}
