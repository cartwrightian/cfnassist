package tw.com.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import tw.com.NotificationProvider;
import tw.com.entity.StackNotification;
import tw.com.exceptions.FailedToCreateQueueException;
import tw.com.exceptions.NotReadyException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SNSEventSource extends QueuePolicyManager implements NotificationProvider {
	private static final Logger logger = LoggerFactory.getLogger(SNSEventSource.class);

	private static final int MAX_NUMBER_MSGS_TO_RECEIVE = 10;
	private static final int QUEUE_READ_TIMEOUT_SECS = 20; // 20 is max allowed
	private static final String SQS_QUEUE_NAME = "CFN_ASSIST_EVENT_QUEUE";
	public static final String SNS_TOPIC_NAME = "CFN_ASSIST_EVENTS";

	public static final String SQS_PROTO = "sqs";
	private static final int QUEUE_CREATE_RETRYS = 3;
	private static final long QUEUE_RETRY_INTERNAL_MILLIS = 70 * 1000;
	
	private SnsClient snsClient;
	private String queueURL;
	private String topicSnsArn;
	private String sqsQueueArn;
	private boolean init;
	
	public SNSEventSource(SnsClient snsClient, SqsClient sqsClient) {
		super(sqsClient);
		this.snsClient = snsClient;
		
		init = false;
	}
	
	public String getQueueURL() {
		return queueURL;
	}
	
	@Override
	public void init() throws MissingArgumentException, FailedToCreateQueueException, InterruptedException {
		if (init) {
			logger.warn("SNSMonitor init called again");
			return;
		}
		logger.info("Init SNSMonitor");
		topicSnsArn = getOrCreateSNSARN();
		queueURL = getOrCreateQueue();
		
		Map<QueueAttributeName, String> queueAttributes = getQueueAttributes(queueURL);
		sqsQueueArn = queueAttributes.get(QueueAttributeName.QUEUE_ARN);
		checkOrCreateQueuePermissions(queueAttributes, topicSnsArn, sqsQueueArn, queueURL);

		String subscriptionArn = createOrGetSQSSubscriptionToSNS();
		logger.info("Using subscription arn " + subscriptionArn);
		init = true;
	}
	
	
	private ReceiveMessageResponse receiveMessages() {
		logger.info("Waiting for messages for queue " + queueURL);
		ReceiveMessageRequest receiveMessageRequest = createWaitRequest();
		return sqsClient.receiveMessage(receiveMessageRequest);
	}
	
	@Override
	public List<StackNotification> receiveNotifications() throws NotReadyException {
		guardForInit();
		List<StackNotification> notifications = new LinkedList<>();
		ReceiveMessageResponse result = receiveMessages();
		ObjectMapper objectMapper = new ObjectMapper();

		List<Message> messages = result.messages();
		logger.info(String.format("Received %s messages", messages.size()));

		for(Message msg : messages) {
			logger.debug(msg.toString());
			JsonNode messageNode;
			try {
				messageNode = extractMessageNode(msg, objectMapper);
				StackNotification notification = StackNotification.parseNotificationMessage(messageNode.textValue());
				logger.info("Received notification for stackid: " + notification.getStackId());
				notifications.add(notification);
			} catch (ArrayIndexOutOfBoundsException | IOException e) {
				logger.warn("unable to parse message: " +msg);
			} 
			deleteMessage(msg);
		}	
		return notifications;
	}
	
	private JsonNode extractMessageNode(Message msg, ObjectMapper objectMapper)
			throws IOException {
		String json = msg.body();
		
		//logger.debug("Body json: " + json);
		
		JsonNode rootNode = objectMapper.readTree(json);
		return rootNode.get("Message");
	}
	
	private ReceiveMessageRequest createWaitRequest() {
		return ReceiveMessageRequest.builder().
				queueUrl(queueURL).
				waitTimeSeconds(QUEUE_READ_TIMEOUT_SECS).
				maxNumberOfMessages(MAX_NUMBER_MSGS_TO_RECEIVE).
				build();
	}

	private List<Subscription> getSNSSubs() {
		ListSubscriptionsResponse subResults = snsClient.listSubscriptions();
		return subResults.subscriptions();
	}

	private String createNewSQSSubscriptionToSNS() {
		logger.info("No SQS Subscription to SNS found, creating a new one");
		SubscribeRequest subscribeRequest = SubscribeRequest.builder().
				topicArn(topicSnsArn).
				protocol(SQS_PROTO).
				endpoint(sqsQueueArn).returnSubscriptionArn(true).build();
		SubscribeResponse result = snsClient.subscribe(subscribeRequest);
		
		String subscriptionArn = result.subscriptionArn();
		logger.info("Created new SNS subscription, subscription arn is: " + subscriptionArn);
		return subscriptionArn;
	}

	// TODO
	private String getOrCreateQueue() throws InterruptedException, FailedToCreateQueueException {
		CreateQueueRequest createQueueRequest = CreateQueueRequest.builder().queueName(SQS_QUEUE_NAME).build();
		int attemptsLefts = QUEUE_CREATE_RETRYS;
		while (attemptsLefts>0) {
			try {
				logger.info("Attempt to create queue with name " + SQS_QUEUE_NAME);
				CreateQueueResponse result = sqsClient.createQueue(createQueueRequest); // creates, or returns existing queue
				String queueUrl = result.queueUrl();
				logger.info("Found sqs queue URL:" +queueUrl);
				return queueUrl;
			}
			catch(QueueDeletedRecentlyException exception) {
				logger.warn("Queue recently deleted, must pause before retry. " + exception.toString());
				// aws docs say have to wait >60 seconds before trying again
				Thread.sleep(QUEUE_RETRY_INTERNAL_MILLIS);
			}
			catch(QueueNameExistsException serviceException) {
				logger.error("Caught service exception during queue creation: " +serviceException.getMessage());
				Thread.sleep(QUEUE_RETRY_INTERNAL_MILLIS);
			}
			attemptsLefts--;
		}
		throw new FailedToCreateQueueException(SQS_QUEUE_NAME);
	}
	
	public String getSNSArn() throws NotReadyException {
		guardForInit();
		return topicSnsArn;
	}
	
	private void guardForInit() throws NotReadyException {
		if (!init) {
			logger.error("Not initialised");
			throw new NotReadyException("SNSMonitor not initialised");
		}
	}
	
	private String getOrCreateSNSARN() {
		CreateTopicRequest createTopicRequest = CreateTopicRequest.builder().name(SNS_TOPIC_NAME).build();
		CreateTopicResponse result = snsClient.createTopic(createTopicRequest); // returns arn if topic already exists
		String topicArn = result.topicArn();
		logger.info("Using arn :" + topicArn);
		return topicArn;
	}
	
	private String createOrGetSQSSubscriptionToSNS() {
		String existing = getARNofSQSSubscriptionToSNS();
		if (existing!=null) {
			return existing;
		}	
		return createNewSQSSubscriptionToSNS();
	}
	
	public String getARNofSQSSubscriptionToSNS() {
		List<Subscription> subs = getSNSSubs();
		for(Subscription sub : subs) {		
			if (sub.protocol().equals(SQS_PROTO)) {
				if (sub.endpoint().equals(sqsQueueArn)) {
					String subscriptionArn = sub.subscriptionArn();
					logger.info("Found existing SNS subscription, subscription arn is: " + subscriptionArn);
					return subscriptionArn;
				}
			}
		}
		return null;
	}

	private void deleteMessage(Message msg) {
		logger.info("Deleting message " + msg.receiptHandle());
		sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueURL)
				.receiptHandle(msg.receiptHandle()).build());
	}

	@Override
	public boolean isInit() {
		return init;
	}


}
