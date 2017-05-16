package tw.com.providers;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.NotificationProvider;
import tw.com.entity.StackNotification;
import tw.com.exceptions.FailedToCreateQueueException;
import tw.com.exceptions.NotReadyException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.ListSubscriptionsResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sns.model.Subscription;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDeletedRecentlyException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SNSEventSource extends QueuePolicyManager implements NotificationProvider {
	private static final Logger logger = LoggerFactory.getLogger(SNSEventSource.class);

	private static final int MAX_NUMBER_MSGS_TO_RECEIVE = 10;
	private static final int QUEUE_READ_TIMEOUT_SECS = 20; // 20 is max allowed
	private static final String SQS_QUEUE_NAME = "CFN_ASSIST_EVENT_QUEUE";
	public static final String SNS_TOPIC_NAME = "CFN_ASSIST_EVENTS";

	public static final String SQS_PROTO = "sqs";
	private static final int QUEUE_CREATE_RETRYS = 3;
	private static final long QUEUE_RETRY_INTERNAL_MILLIS = 70 * 1000;
	
	private AmazonSNSClient snsClient;
	private String queueURL;
	private String topicSnsArn;
	private String queueArn;
	private boolean init;
	
	public SNSEventSource(AmazonSNSClient snsClient,AmazonSQSClient sqsClient) {
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
		
		Map<String, String> queueAttributes = getQueueAttributes(queueURL);
		queueArn = queueAttributes.get(QUEUE_ARN_KEY);
		checkOrCreateQueuePermissions(queueAttributes, topicSnsArn, queueArn, queueURL);
		
		createOrGetSQSSubscriptionToSNS();
		init = true;
	}
	
	
	private ReceiveMessageResult receiveMessages() {
		logger.info("Waiting for messages for queue " + queueURL);
		ReceiveMessageRequest receiveMessageRequest = createWaitRequest();
		return sqsClient.receiveMessage(receiveMessageRequest);
	}
	
	@Override
	public List<StackNotification> receiveNotifications() throws NotReadyException {
		guardForInit();
		List<StackNotification> notifications = new LinkedList<StackNotification>();
		ReceiveMessageResult result = receiveMessages();
		ObjectMapper objectMapper = new ObjectMapper();

		List<Message> messages = result.getMessages();
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
		String json = msg.getBody();
		
		//logger.debug("Body json: " + json);
		
		JsonNode rootNode = objectMapper.readTree(json);		
		JsonNode messageNode = rootNode.get("Message");
		return messageNode;
	}
	
	private ReceiveMessageRequest createWaitRequest() {
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueURL);
		receiveMessageRequest.setWaitTimeSeconds(QUEUE_READ_TIMEOUT_SECS);
		receiveMessageRequest.setMaxNumberOfMessages(MAX_NUMBER_MSGS_TO_RECEIVE);
		return receiveMessageRequest;
	}

	
	private List<Subscription> getSNSSubs() {
		ListSubscriptionsResult subResults = snsClient.listSubscriptions();
		List<Subscription> subs = subResults.getSubscriptions();
		return subs;
	}

	private String createNewSQSSubscriptionToSNS() {
		logger.info("No SQS Subscription to SNS found, creating a new one");
		SubscribeRequest subscribeRequest = new SubscribeRequest(topicSnsArn, SQS_PROTO, queueArn);
		SubscribeResult result = snsClient.subscribe(subscribeRequest);
		
		String subscriptionArn = result.getSubscriptionArn();
		logger.info("Created new SNS subscription, subscription arn is: " + subscriptionArn);
		return subscriptionArn;
	}
	
	private String getOrCreateQueue() throws InterruptedException, FailedToCreateQueueException {
		CreateQueueRequest createQueueRequest = new CreateQueueRequest(SQS_QUEUE_NAME);
		int attemptsLefts = QUEUE_CREATE_RETRYS;
		while (attemptsLefts>0) {
			try {
				logger.info("Attempt to create queue with name " + SQS_QUEUE_NAME);
				CreateQueueResult result = sqsClient.createQueue(createQueueRequest); // creates, or returns existing queue
				String queueUrl = result.getQueueUrl();
				logger.info("Found sqs queue URL:" +queueUrl);
				return queueUrl;
			}
			catch(QueueDeletedRecentlyException exception) {
				logger.warn("Queue recently deleted, must pause before retry. " + exception.toString());
				// aws docs say have to wait >60 seconds before trying again
				Thread.sleep(QUEUE_RETRY_INTERNAL_MILLIS);
			}
			catch(AmazonServiceException serviceException) {
				logger.error("Caught service exception during queue creation: " +serviceException.getErrorMessage());
				Thread.sleep(QUEUE_RETRY_INTERNAL_MILLIS);
			}
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
		CreateTopicRequest createTopicRequest = new CreateTopicRequest(SNS_TOPIC_NAME);
		CreateTopicResult result = snsClient.createTopic(createTopicRequest); // returns arn if topic already exists
		String topicArn = result.getTopicArn();
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
			if (sub.getProtocol().equals(SQS_PROTO)) {
				if (sub.getEndpoint().equals(queueArn)) {
					String subscriptionArn = sub.getSubscriptionArn();
					logger.info("Found existing SNS subscription, subscription arn is: " + subscriptionArn);
					return subscriptionArn;
				}
			}
		}
		return null;
	}

	private void deleteMessage(Message msg) {
		logger.info("Deleting message " + msg.getReceiptHandle());
		sqsClient.deleteMessage(new DeleteMessageRequest()
	    .withQueueUrl(queueURL)
	    .withReceiptHandle(msg.getReceiptHandle()));	
	}

	@Override
	public boolean isInit() {
		return init;
	}


}
