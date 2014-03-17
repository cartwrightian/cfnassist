package tw.com;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Condition;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.auth.policy.conditions.ConditionFactory;
import com.amazonaws.services.cloudformation.model.StackStatus;
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
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

public class SNSMonitor implements MonitorStackEvents {
	private static final String QUEUE_ARN_KEY = "QueueArn";
	private static final String QUEUE_POLICY_KEY = "Policy";
	private Collection<String> attributeNames = new LinkedList<String>();

	private static final int QUEUE_READ_TIMEOUT_SECS = 20; // 20 is max allowed
	private static final String SQS_PROTO = "sqs";
	private static final Logger logger = LoggerFactory.getLogger(SNSMonitor.class);
	public static final String SNS_TOPIC_NAME = "CFN_ASSIST_EVENTS";
	private static final String SQS_QUEUE_NAME = "CFN_ASSIST_EVENT_QUEUE";
	private static final int LIMIT = 50;

	private AmazonSNSClient snsClient;
	private AmazonSQSClient sqsClient;
	private String topicSnsArn;
	private String queueArn;
	private String queueURL;
	private boolean init;

	public SNSMonitor(AmazonSNSClient snsClient, AmazonSQSClient sqsClient) {
		this.snsClient = snsClient;
		this.sqsClient = sqsClient;
		attributeNames.add(QUEUE_ARN_KEY);
		attributeNames.add(QUEUE_POLICY_KEY);
		init = false;
	}
	
	public void init() throws MissingArgumentException {
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

	private String getOrCreateQueue() {
		CreateQueueRequest createQueueRequest = new CreateQueueRequest(SQS_QUEUE_NAME);
		CreateQueueResult result = sqsClient.createQueue(createQueueRequest); // creates, or returns existing queue
		String queueUrl = result.getQueueUrl();
		logger.info("Found sqs queue URL:" +queueUrl);
		return queueUrl;
	}
	
	private void checkOrCreateQueuePermissions(
			Map<String, String> queueAttributes) {
		Policy policy = extractPolicy(queueAttributes);	

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
		logger.info("Policy allowing SNS to publish to queue not found");
		setQueuePolicy();
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
		logger.debug("Current queue policy: " + policyJson);
		Policy policy = Policy.fromJson(policyJson);
		return policy;
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

	private String createOrGetSQSSubscriptionToSNS() {
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
		return createNewSQSSubscriptionToSNS();
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

	private String getOrCreateSNSARN() {
		CreateTopicRequest createTopicRequest = new CreateTopicRequest(SNS_TOPIC_NAME);
		CreateTopicResult result = snsClient.createTopic(createTopicRequest); // returns arn if topic already exists
		String topicArn = result.getTopicArn();
		logger.info("Using arn :" + topicArn);
		return topicArn;
	}

	@Override
	public String waitForCreateFinished(StackId stackId)
			throws WrongNumberOfStacksException, InterruptedException,
			StackCreateFailed, NotReadyException {
		guardForInit();
		return waitForStatus(stackId, StackStatus.CREATE_COMPLETE.toString());
	}
	
	@Override
	public String waitForDeleteFinished(StackId stackId)
			throws WrongNumberOfStacksException, InterruptedException, NotReadyException {
		guardForInit();
		return waitForStatus(stackId, StackStatus.DELETE_COMPLETE.toString());
	}

	private String waitForStatus(StackId stackId, String requiredStatus) throws InterruptedException {
		logger.info(String.format("Waiting for stack %s to change to status %s", stackId, requiredStatus));
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueURL);
		receiveMessageRequest.setWaitTimeSeconds(QUEUE_READ_TIMEOUT_SECS);
		List<Message> msgs = new LinkedList<Message>();
		int count = 0;
		while (count<LIMIT) {
			while (msgs.size()==0) {
				logger.info("Waiting for messages for queue " + queueURL);
				ReceiveMessageResult result = sqsClient.receiveMessage(receiveMessageRequest);
				msgs = result.getMessages();
			}
			String status = processMessages(stackId, msgs);
			if (status.equals(requiredStatus)) {
				return status;
			}
			msgs.clear();
			count++;
		}
		throw new InterruptedException("Did not receieve stack status change within required time");
	}

	private void guardForInit() throws NotReadyException {
		if (!init) {
			logger.error("Not initialised");
			throw new NotReadyException("SNSMonitor not initialised");
		}
	}

	private String processMessages(StackId stackId, List<Message> msgs) {
		logger.info(String.format("Received %s messages",msgs.size()));
		for(Message msg : msgs) {
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				JsonNode messageNode = extractMessageNode(msg, objectMapper);
				deleteMessage(msg);
				
				StackNotification notification = parseNotificationMessage(messageNode.textValue());
				if (notification.getStackId().equals(stackId.getStackId())) {
					logger.info(String.format("Received notification for stack %s status was %s", stackId, notification.getStatus()));
					return notification.getStatus();
				} else {
					logger.info(String.format("Notification did not match stackId, expected: %s was: %s", stackId.getStackId(),
							notification.getStackId()));
				}			
			} catch (IOException e) {
				logger.error("Unable to process message: " + e.getMessage());
				logger.error("Message body was: " + msg.getBody());
			}
		}
		return "";
	}

	private JsonNode extractMessageNode(Message msg, ObjectMapper objectMapper)
			throws IOException, JsonProcessingException {
		String json = msg.getBody();
		
		logger.debug("Body json: " + json);
		
		JsonNode rootNode = objectMapper.readTree(json);		
		JsonNode messageNode = rootNode.get("Message");
		return messageNode;
	}

	private void deleteMessage(Message msg) {
		logger.info("Deleting message " + msg.getReceiptHandle());
		sqsClient.deleteMessage(new DeleteMessageRequest()
	    .withQueueUrl(queueURL)
	    .withReceiptHandle(msg.getReceiptHandle()));
		
	}

	public String getArn() throws NotReadyException {
		guardForInit();
		return topicSnsArn;
	}

	public StackNotification parseNotificationMessage(String notificationMessage) {
		String[] parts = notificationMessage.split("\n");
		String status="";
		String foundName="";
		String stackId="";
		for(int i=0; i<parts.length; i++) {
			String[] elements = parts[i].split("=");
			String key = elements[0];
			String containsValue = elements[1];
			if (key.equals("StackName")) {
				foundName=extractValue(containsValue);
			}
			if (key.equals("ResourceStatus")) {
				status=extractValue(containsValue);
			}
			if (key.equals("StackId")) {
				stackId = extractValue(containsValue);
			}
		}
		return new StackNotification(foundName,status,stackId);
	}

	private String extractValue(String value) {
		return value.replace('\'',' ').trim();
	}

}
