package tw.com.providers;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.model.Statement;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.util.*;
import java.util.concurrent.locks.Condition;

public class QueuePolicyManager {
	private static final Logger logger = LoggerFactory.getLogger(QueuePolicyManager.class);	

	protected SqsClient sqsClient;
	private Collection<QueueAttributeName> attributeNames = new LinkedList<>();

	public QueuePolicyManager(SqsClient sqsClient) {
		this.sqsClient = sqsClient;
		attributeNames.add(QueueAttributeName.QUEUE_ARN);
		attributeNames.add(QueueAttributeName.POLICY);
	}

	public void checkOrCreateQueuePermissions(
			Map<QueueAttributeName, String> queueAttributes, String topicSnsArn, String queueArn, String queueURL) {
		String policy = extractPolicy(queueAttributes);

		return;
//		if (policy!=null) {
//			logger.info("Policy found for queue, check if required conditions set");
//			for (Statement statement :  policy.getStatements()) {
//				if (allowQueuePublish(statement)) {
//					logger.info("Statement allows sending, checking for ARN condition. Statement ID is " + statement.getId());
//					for (Condition condition : statement.getConditions()) {
//						if (condition.getConditionKey().equals("aws:SourceArn") &&
//								condition.getValues().contains(topicSnsArn)) {
//								logger.info("Found a matching condition for sns arn " + topicSnsArn);
//								return;
//						}
//					}
//				}
//			}
//		}
//		logger.info("Policy allowing SNS to publish to queue not found");
//		setQueuePolicy(topicSnsArn, queueArn, queueURL);
	}
		
	private void setQueuePolicy(String topicSnsArn, String queueArn, String queueURL) {
		logger.info("Set up policy for queue to allow SNS to publish to it");
		Policy sqsPolicy = Policy.builder().build();

//				.builder().
//        .withStatements(new Statement(Statement.Effect.Allow)
//                            .withPrincipals(Principal.AllUsers)
//                            .withResources(new Resource(queueArn))
//                            .withConditions(ConditionFactory.newSourceArnCondition(topicSnsArn))
//                            .withActions(SQSActions.SendMessage));
		
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.POLICY, "sqsPolicy");
        
        SetQueueAttributesRequest setQueueAttributesRequest = SetQueueAttributesRequest.builder().
				queueUrl(queueURL).attributes(attributes).build();
        sqsClient.setQueueAttributes(setQueueAttributesRequest);
	}
	
	private boolean allowQueuePublish(Statement statement) {
//		statement.getValueForField()
//		if (statement.getEffect().equals(Statement.Effect.Allow)) {
//			List<Action> actions = statement.getActions();
//
//			for(Action action : actions) { // .equals not properly defined on actions
//				if (action.getActionName().equals("sqs:"+SQSActions.SendMessage.toString())) {
//					return true;
//				}
//			}
//		}
		return false;
	}
	
	public Map<QueueAttributeName, String> getQueueAttributes(String url) throws MissingArgumentException {
		// find the queue arn, we need this to create the SNS subscription
		GetQueueAttributesRequest getQueueAttributesRequest = GetQueueAttributesRequest.builder().
				queueUrl(url).
				attributeNames(attributeNames).build();

		GetQueueAttributesResponse attribResult = sqsClient.getQueueAttributes(getQueueAttributesRequest);
		Map<QueueAttributeName, String> attribMap = attribResult.attributes();
		if (!attribMap.containsKey(QueueAttributeName.QUEUE_ARN)) {
			String msg = "Missing arn attirbute, tried attribute with name: " + QueueAttributeName.QUEUE_ARN;
			logger.error(msg);
			throw new MissingArgumentException(msg);
		}	
		return attribMap;
	}

	private String extractPolicy(Map<QueueAttributeName, String> queueAttributes) {
		return queueAttributes.get(QueueAttributeName.POLICY);
//		String policyJson = queueAttributes.get(QueueAttributeName.POLICY);
//		if (policyJson==null) {
//			return null;
//		}
//
//		logger.debug("Current queue policy: " + policyJson);
//		Policy policy = Policy.fromJson(policyJson);
//		return policy;
	}


}
