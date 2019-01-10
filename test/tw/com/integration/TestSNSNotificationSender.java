package tw.com.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.MissingArgumentException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
	private SNSNotificationSender sender;
	private QueuePolicyManager policyManager;
	
	@BeforeClass
	public static void beforeAllTestsRun() {
		snsClient = EnvironmentSetupForTests.createSNSClient();
		sqsClient = EnvironmentSetupForTests.createSQSClient();
	}

	@Before
	public void beforeEachTestRuns() {
		sender = new SNSNotificationSender(snsClient);
		policyManager = new QueuePolicyManager(sqsClient);
	}
	
	@Test
	public void shouldFindSNSTopicIfPresent() {
		CreateTopicResponse createResult = snsClient.createTopic(CreateTopicRequest.builder().
				name(SNSNotificationSender.TOPIC_NAME).build());
		String arn = createResult.topicArn();
		
		assertFalse(sender.getTopicARN().isEmpty());
		
		snsClient.deleteTopic(DeleteTopicRequest.builder().topicArn(arn).build());
		
		SNSNotificationSender senderB = new SNSNotificationSender(snsClient);
		assertTrue(senderB.getTopicARN().isEmpty());
	}
	
	@Test
	public void shouldSendNotificationMessageOnTopic() throws CfnAssistException, MissingArgumentException, InterruptedException, IOException {
		User user = EnvironmentSetupForTests.createUser();

		CFNAssistNotification notification = new CFNAssistNotification("name", "complete", user);

		CreateTopicResponse createResult = snsClient.createTopic(CreateTopicRequest.builder().
				name(SNSNotificationSender.TOPIC_NAME).build());
		String SNSarn = createResult.topicArn();
		assertNotNull(SNSarn);
			
		// test the SNS notification by creating a SQS and subscribing that to the SNS
		CreateQueueResponse queueResult = createQueue();
		String queueUrl = queueResult.queueUrl();
		
		// give queue perms to subscribe to SNS
		Map<QueueAttributeName, String> attribrutes = policyManager.getQueueAttributes(queueUrl);
		String queueArn = attribrutes.get(QueueAttributeName.QUEUE_ARN);
		policyManager.checkOrCreateQueuePermissions(attribrutes, SNSarn, queueArn, queueUrl);
		
		// create subscription
		SubscribeRequest.Builder subscribeRequest = SubscribeRequest.builder().topicArn(SNSarn).protocol(SNSEventSource.SQS_PROTO).endpoint(queueArn);
		SubscribeResponse subResult = snsClient.subscribe(subscribeRequest.build());
		String subscriptionArn = subResult.subscriptionArn();
		
		// send SNS and then check right thing arrives at SQS
		sender.sendNotification(notification);
		
		ReceiveMessageRequest request = ReceiveMessageRequest.builder().
				queueUrl(queueUrl).
				waitTimeSeconds(10).build();
		ReceiveMessageResponse receiveResult = sqsClient.receiveMessage(request);
		List<Message> messages = receiveResult.messages();
		
		sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
		snsClient.unsubscribe(UnsubscribeRequest.builder().subscriptionArn(subscriptionArn).build());
		snsClient.deleteTopic(DeleteTopicRequest.builder().topicArn(SNSarn).build());
		
		assertEquals(1, messages.size());
		
		Message msg = messages.get(0);

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
