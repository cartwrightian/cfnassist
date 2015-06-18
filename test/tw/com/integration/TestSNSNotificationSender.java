package tw.com.integration;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.EnvironmentSetupForTests;
import tw.com.entity.CFNAssistNotification;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.QueuePolicyManager;
import tw.com.providers.SNSEventSource;
import tw.com.providers.SNSNotificationSender;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDeletedRecentlyException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestSNSNotificationSender {
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	private SNSNotificationSender sender;
	private QueuePolicyManager policyManager;
	
	@BeforeClass
	public static void beforeAllTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
	}

	@Before
	public void beforeEachTestRuns() {
		sender = new SNSNotificationSender(snsClient);
		policyManager = new QueuePolicyManager(sqsClient);
	}
	
	@Test
	public void shouldFindSNSTopicIfPresent() {
		CreateTopicResult createResult = snsClient.createTopic(SNSNotificationSender.TOPIC_NAME);
		String arn = createResult.getTopicArn();
		
		assertFalse(sender.getTopicARN().isEmpty());
		
		snsClient.deleteTopic(arn);
		
		assertTrue(sender.getTopicARN().isEmpty());
	}
	
	@Test
	public void shouldSendNotificationMessageOnTopic() throws CfnAssistException, MissingArgumentException, InterruptedException, JsonParseException, JsonMappingException, IOException {
		CFNAssistNotification notification = new CFNAssistNotification("name", "complete");
		
		CreateTopicResult createResult = snsClient.createTopic(SNSNotificationSender.TOPIC_NAME);
		String SNSarn = createResult.getTopicArn();
		assertNotNull(SNSarn);
		
		
		// test the SNS notification by creating a SQS and subscribing that to the SNS
		CreateQueueResult queueResult = createQueue();
		String queueUrl = queueResult.getQueueUrl();
		
		// give queue perms to subscribe to SNS
		Map<String, String> attribrutes = policyManager.getQueueAttributes(queueUrl);
		String queueArn = attribrutes.get(QueuePolicyManager.QUEUE_ARN_KEY);
		policyManager.checkOrCreateQueuePermissions(attribrutes, SNSarn, queueArn, queueUrl);
		
		// create subscription
		SubscribeRequest subscribeRequest = new SubscribeRequest(SNSarn, SNSEventSource.SQS_PROTO, queueArn);
		SubscribeResult subResult = snsClient.subscribe(subscribeRequest);
		String subscriptionArn = subResult.getSubscriptionArn();
		
		// send SNS and then check right thing arrives at SQS
		sender.sendNotification(notification);
		
		ReceiveMessageRequest request = new ReceiveMessageRequest().
				withQueueUrl(queueUrl).
				withWaitTimeSeconds(10);
		ReceiveMessageResult receiveResult = sqsClient.receiveMessage(request);
		List<Message> messages = receiveResult.getMessages();
		
		sqsClient.deleteQueue(queueUrl);
		snsClient.unsubscribe(subscriptionArn);
		snsClient.deleteTopic(SNSarn);
		
		assertEquals(1, messages.size());
		
		Message msg = messages.get(0);

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(msg.getBody());		
		JsonNode messageNode = rootNode.get("Message");
		
		String json = messageNode.textValue();
		
		CFNAssistNotification result = CFNAssistNotification.fromJSON(json);
		
		assertEquals(notification, result);
	}

	private CreateQueueResult createQueue() throws InterruptedException {
		CreateQueueResult queueResult = null;
		try {
			queueResult = sqsClient.createQueue("TestSNSNotificationSender");
		}
		catch(QueueDeletedRecentlyException needToWaitException) {
			Thread.sleep(61*1000);
			queueResult = sqsClient.createQueue("TestSNSNotificationSender");
		}
		assertNotNull(queueResult);
		return queueResult;
	}

}
