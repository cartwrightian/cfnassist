package tw.com.providers;

import com.amazonaws.auth.policy.*;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.auth.policy.conditions.ConditionFactory;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class QueuePolicyManager {
	private static final Logger logger = LoggerFactory.getLogger(QueuePolicyManager.class);	
	private static final String QUEUE_POLICY_KEY = "Policy";
	public static final String QUEUE_ARN_KEY = "QueueArn";

	protected AmazonSQS sqsClient;
	private Collection<String> attributeNames = new LinkedList<String>();

	public QueuePolicyManager(AmazonSQS sqsClient) {
		this.sqsClient = sqsClient;
		attributeNames.add(QUEUE_ARN_KEY);
		attributeNames.add(QUEUE_POLICY_KEY);
	}

	public void checkOrCreateQueuePermissions(
			Map<String, String> queueAttributes, String topicSnsArn, String queueArn, String queueURL) {
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
		setQueuePolicy(topicSnsArn, queueArn, queueURL);
	}
		
	private void setQueuePolicy(String topicSnsArn, String queueArn, String queueURL) {
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
	
	public Map<String, String> getQueueAttributes(String url) throws MissingArgumentException {
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

	private Policy extractPolicy(Map<String, String> queueAttributes) {
		String policyJson = queueAttributes.get(QUEUE_POLICY_KEY);
		if (policyJson==null) {
			return null;
		}
		
		logger.debug("Current queue policy: " + policyJson);
		Policy policy = Policy.fromJson(policyJson);
		return policy;
	}


}
