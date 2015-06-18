package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.entity.CFNAssistNotification;
import tw.com.exceptions.CfnAssistException;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.Topic;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SNSNotificationSender implements NotificationSender {
	private static final Logger logger = LoggerFactory.getLogger(SNSNotificationSender.class);

	public static final String TOPIC_NAME = "CFN_ASSIST_NOTIFICATIONS";
	
	private AmazonSNSClient snsClient;

	// stateful to limit number of AWS API calls we make
	private String topicANR ="";

	public SNSNotificationSender(AmazonSNSClient snsClient) {
		this.snsClient = snsClient;
	}

	public String getTopicARN() {
		if (topicANR.isEmpty()) {
			ListTopicsResult topics = snsClient.listTopics();
			for(Topic topic : topics.getTopics()) {
				String foundArn = topic.getTopicArn();
				if (foundArn.contains(TOPIC_NAME)) {
					logger.info("Found notification topic for SNS, ARN is: " + foundArn);
					topicANR =  foundArn;
					break;
				}
			}
			logger.info("Did not find notification topic for SNS, to receive updates create topic: " + TOPIC_NAME);
		}
		return topicANR;
	}

	public String sendNotification(CFNAssistNotification notification) throws CfnAssistException {
		String topicArn = getTopicARN();
		if (topicArn.isEmpty()) {
			logger.info("Will not send notification as sns topic not found, topic is: " + TOPIC_NAME);
			return "";
		}
		
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			String json = objectMapper.writeValueAsString(notification);
			PublishResult result = snsClient.publish(topicArn, json);
			logger.info(String.format("Send message on topic %s with id %s", TOPIC_NAME, result.getMessageId()));
			return result.getMessageId();
		}
		catch (JsonProcessingException jsonException) {
			throw new CfnAssistException("Unable to create notification JSON " + jsonException.toString());
		}
	}

}
