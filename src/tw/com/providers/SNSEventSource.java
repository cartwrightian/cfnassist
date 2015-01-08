package tw.com.providers;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
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
import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Condition;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.auth.policy.conditions.ConditionFactory;
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
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDeletedRecentlyException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SNSEventSource implements NotificationProvider {
	private static final int MAX_NUMBER_MSGS_TO_RECEIVE = 10;
	private static final int QUEUE_READ_TIMEOUT_SECS = 20; // 20 is max allowed
	private static final Logger logger = LoggerFactory.getLogger(SNSEventSource.class);
	private static final String SQS_QUEUE_NAME = "CFN_ASSIST_EVENT_QUEUE";
	public static final String SNS_TOPIC_NAME = "CFN_ASSIST_EVENTS";

	private static final String SQS_PROTO = "sqs";
	private static final String QUEUE_ARN_KEY = "QueueArn";
	private static final String QUEUE_POLICY_KEY = "Policy";
	private static final int QUEUE_CREATE_RETRYS = 3;
	private static final long QUEUE_RETRY_INTERNAL_MILLIS = 70 * 1000;
	private Collection<String> attributeNames = new LinkedList<String>();
	
	private AmazonSNSClient snsClient;
	private AmazonSQSClient sqsClient;
	private String queueURL;
	private String topicSnsArn;
	private String queueArn;
	private boolean init;
	
	public SNSEventSource(AmazonSNSClient snsClient,AmazonSQSClient sqsClient) {
		this.snsClient = snsClient;
		this.sqsClient = sqsClient;
		attributeNames.add(QUEUE_ARN_KEY);
		attributeNames.add(QUEUE_POLICY_KEY);
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
		checkOrCreateQueuePermissions(queueAttributes);
		
		createOrGetSQSSubscriptionToSNS();
		init = true;
	}
	
	private Map<String, String> getQueueAttributes(String url) throws MissingArgumentException {
		// find the queue arn, we need this to create the SNS subscription
		GetQueueAttributesRequest getQueueAttributesRequest = new GetQueueAttributesRequest(url);
		getQueueAttributesRequest.setAttributeNames(attributeNames);
		GetQueueAttributesResult attribResult = sqsClient.getQueueAttributes(getQueueAttributesRequest);
		Map<String, String> attribMap = attribResult.getAttributes();
		if (!attribMap.containsKey(QUEUE_ARN_KEY)) {
			String msg = "Missing arn attirbute, tried attribute with name: " + QUEUE_ARN_KEY;
			logger.error(msg);
			throw new MissingArgumentException(msg);
		}	
		return attribMap;
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
			throws IOException, JsonProcessingException {
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
	
	private void checkOrCreateQueuePermissions(
			Map<String, String> queueAttributes) {
		Policy policy = extractPolicy(queueAttributes);	
		
		if (policy!=null) {
			logger.info("Policy found for queue, check if required conditions set");
			for (Statement statement :  policy.getStatements()) {
				if (allowQueuePublish(statement)) {
					logger.info("Statement allows sending, checking for ARN condition. Statement ID is " + statement.getId());
					for (Condition condition : statement.getConditions()) {
						if (condition.getConditionKey().equals("aws:SourceArn") && 
								condition.getValues().contains(topicSnsArn)) {
								logger.info("Found a matching condition for sns arn " + topicSnsArn);
								return;
						}
					}
				}
			}
		}
		logger.info("Policy allowing SNS to publish to queue not found");
		setQueuePolicy();
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
	
	
	
	private void setQueuePolicy() {
		logger.info("Set up policy for queue to allow SNS to publish to it");
		Policy sqsPolicy = new Policy()
        .withStatements(new Statement(Statement.Effect.Allow)
                            .withPrincipals(Principal.AllUsers)
                            .withResources(new Resource(queueArn))
                            .withConditions(ConditionFactory.newSourceArnCondition(topicSnsArn))
                            .withActions(SQSActions.SendMessage));
		
        Map<String, String> attributes = new HashMap<String,String>();
        attributes.put("Policy", sqsPolicy.toJson());
        
        SetQueueAttributesRequest setQueueAttributesRequest = new SetQueueAttributesRequest();
        setQueueAttributesRequest.setQueueUrl(queueURL);
        setQueueAttributesRequest.setAttributes(attributes);
        sqsClient.setQueueAttributes(setQueueAttributesRequest);		
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
	
	private boolean allowQueuePublish(Statement statement) {
		if (statement.getEffect().equals(Statement.Effect.Allow)) {
			List<Action> actions = statement.getActions();
	
			for(Action action : actions) { // .equals not properly defined on actions
				if (action.getActionName().equals("sqs:"+SQSActions.SendMessage.toString())) {
					return true;
				}
			}
		}	
		return false;
	}

	private Policy extractPolicy(Map<String, String> queueAttributes) {
		String policyJson = queueAttributes.get(QUEUE_POLICY_KEY);
		if (policyJson==null) {
			return null;
		}
		
		logger.debug("Current queue policy: " + policyJson);
		Policy policy = Policy.fromJson(policyJson);
		return policy;
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
