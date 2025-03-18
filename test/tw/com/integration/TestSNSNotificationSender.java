package tw.com.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.MissingArgumentException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.iam.model.User;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.CFNAssistNotification;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.QueuePolicyManager;
import tw.com.providers.SNSEventSource;
import tw.com.providers.SNSNotificationSender;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestSNSNotificationSender {
	private static SnsClient snsClient;
	private static SqsClient sqsClient;
	private SNSNotificationSender snsNotificationSender;
	private QueuePolicyManager policyManager;
	
	@BeforeAll
	public static void beforeAllTestsRun() {
		snsClient = EnvironmentSetupForTests.createSNSClient();
		sqsClient = EnvironmentSetupForTests.createSQSClient();
	}

	@BeforeEach
	public void beforeEachTestRuns() {
		snsNotificationSender = new SNSNotificationSender(snsClient);
		policyManager = new QueuePolicyManager(sqsClient);
	}
	
	@Test
	public void shouldFindSNSTopicIfPresent() {
		CreateTopicResponse createResult = snsClient.createTopic(CreateTopicRequest.builder().
				name(SNSNotificationSender.TOPIC_NAME).build());
		String arn = createResult.topicArn();
		
		assertFalse(snsNotificationSender.getTopicARN().isEmpty());
		
		snsClient.deleteTopic(DeleteTopicRequest.builder().topicArn(arn).build());
		
		SNSNotificationSender senderB = new SNSNotificationSender(snsClient);
		assertTrue(senderB.getTopicARN().isEmpty());
	}
	
	@Test
	public void shouldSendNotificationMessageOnTopicToSQSviaSNS() throws CfnAssistException, MissingArgumentException, InterruptedException, IOException {
		User user = EnvironmentSetupForTests.createUser();

		CFNAssistNotification notification = new CFNAssistNotification("name", "complete", user);

		// create topic
		CreateTopicResponse createResult = snsClient.createTopic(CreateTopicRequest.builder().
				name(SNSNotificationSender.TOPIC_NAME).build());
		String SnsTopicArn = createResult.topicArn();
		assertNotNull(SnsTopicArn);
			
		// create an SQS queue, subscribe that to the SNS topic
		CreateQueueResponse queueResult = createQueue();
		String sqsQueueUrl = queueResult.queueUrl();
		
		// give queue perms to subscribe to SNS
		Map<QueueAttributeName, String> attribrutes = policyManager.getQueueAttributes(sqsQueueUrl);
		String sqsQueueArn = attribrutes.get(QueueAttributeName.QUEUE_ARN);
		policyManager.checkOrCreateQueuePermissions(attribrutes, SnsTopicArn, sqsQueueArn, sqsQueueUrl);
		
		// create subscription
		SubscribeRequest.Builder subscribeSQSRequest = SubscribeRequest.builder().topicArn(SnsTopicArn).
				protocol(SNSEventSource.SQS_PROTO).endpoint(sqsQueueArn);
		SubscribeResponse subResult = snsClient.subscribe(subscribeSQSRequest.build());
		String subscriptionArn = subResult.subscriptionArn();
		
		// send SNS and then check right thing arrives at SQS
		snsNotificationSender.sendNotification(notification);

		// No race condition as notification is sent to a queue
		ReceiveMessageRequest sqsReceiveRequest = ReceiveMessageRequest.builder().
				queueUrl(sqsQueueUrl).
				waitTimeSeconds(10).build();
		ReceiveMessageResponse sqsReceiveRequestResponse = sqsClient.receiveMessage(sqsReceiveRequest);
		List<Message> sqsMessages = sqsReceiveRequestResponse.messages();
		
		sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(sqsQueueUrl).build());
		snsClient.unsubscribe(UnsubscribeRequest.builder().subscriptionArn(subscriptionArn).build());
		snsClient.deleteTopic(DeleteTopicRequest.builder().topicArn(SnsTopicArn).build());
		
		assertEquals(1, sqsMessages.size());
		
		Message msg = sqsMessages.get(0);

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(msg.body());
		JsonNode messageNode = rootNode.get("Message");
		
		String json = messageNode.textValue();
		
		CFNAssistNotification result = CFNAssistNotification.fromJSON(json);
		
		assertEquals(notification, result);
	}

	private CreateQueueResponse createQueue() throws InterruptedException {
		CreateQueueResponse queueResult;
		CreateQueueRequest createQueueRequest = CreateQueueRequest.builder().queueName("TestSNSNotificationSender").build();
		try {
			queueResult = sqsClient.createQueue(createQueueRequest);
		}
		catch(QueueDeletedRecentlyException needToWaitException) {
			Thread.sleep(61*1000);
			queueResult = sqsClient.createQueue(createQueueRequest);
		}
		assertNotNull(queueResult);
		return queueResult;
	}

}
