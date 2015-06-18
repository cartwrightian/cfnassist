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

public class SNSNotificationSender {
	private static final Logger logger = LoggerFactory.getLogger(SNSNotificationSender.class);

	public static final String TOPIC_NAME = "CFN_ASSIST_NOTIFICATIONS";
	
	private AmazonSNSClient snsClient;

	public SNSNotificationSender(AmazonSNSClient snsClient) {
		this.snsClient = snsClient;
	}

	public String getTopicARN() {
		ListTopicsResult topics = snsClient.listTopics();
		for(Topic topic : topics.getTopics()) {
			String foundArn = topic.getTopicArn();
			if (foundArn.contains(TOPIC_NAME)) {
				logger.info("Found notification topic for SNS, ARN is: " + foundArn);
				return foundArn;
			}
		}
	
		logger.info("Did not find notification topic for SNS");
		return "";
	}

	public void sendNotification(CFNAssistNotification notification) throws CfnAssistException {
		String topicArn = getTopicARN();
		if (topicArn.isEmpty()) {
			throw new CfnAssistException("Cannot send notification as sns topic not found, topic is: " + TOPIC_NAME);
		}
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			String json = objectMapper.writeValueAsString(notification);
			PublishResult result = snsClient.publish(topicArn, json);
			logger.info(String.format("Send message on topic %s with id %s", TOPIC_NAME, result.getMessageId()));
		}
		catch (JsonProcessingException jsonException) {
			throw new CfnAssistException("Unable to create notification JSON " + jsonException.toString());
		}
	}

}
