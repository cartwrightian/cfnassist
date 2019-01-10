package tw.com.providers;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.model.Statement;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;
import java.util.concurrent.locks.Condition;

public class QueuePolicyManager {
	private static final Logger logger = LoggerFactory.getLogger(QueuePolicyManager.class);	

	protected SqsClient sqsClient;
	private Collection<QueueAttributeName> attributeNames = new LinkedList<>();

	private String QUEUE_ARN = "QUEUE_ARN";
	private String SNS_TOPIC_ARN = "SNS_TOPIC_ARN";

	private String templatePolicy = "{\n" +
			"      \"Version\": \"2012-10-17\",\n" +
			"      \"Id\": \"MyQueuePolicy\",\n" +
			"      \"Statement\": [{\n" +
			"         \"Sid\":\"SubscribeToSNSFromSQSQueue0001\",\n" +
			"         \"Effect\":\"Allow\",\n" +
			"         \"Principal\":\"*\",\n" +
			"         \"Action\":\"sqs:SendMessage\",\n" +
			"         \"Resource\":\"QUEUE_ARN\",\n" +
			"         \"Condition\":{\n" +
			"           \"ArnEquals\":{\n" +
			"             \"aws:SourceArn\":\"SNS_TOPIC_ARN\"\n" +
			"           }\n" +
			"         }\n" +
			"      }]\n" +
			"    }";

	public QueuePolicyManager(SqsClient sqsClient) {
		this.sqsClient = sqsClient;
		attributeNames.add(QueueAttributeName.QUEUE_ARN);
		attributeNames.add(QueueAttributeName.POLICY);
	}

	public void checkOrCreateQueuePermissions(
			Map<QueueAttributeName, String> queueAttributes, String topicSnsArn, String queueArn, String queueURL) {

		if (queueAttributes.containsKey(QueueAttributeName.POLICY)) {
			String existing = queueAttributes.get(QueueAttributeName.POLICY);
			logger.info("Queue already has policy set: " +existing);
			return;
		}
		logger.info("Policy allowing SNS to publish to queue not found");
		setQueuePolicy(topicSnsArn, queueArn, queueURL);

	}
		
	private void setQueuePolicy(String topicSnsArn, String queueArn, String queueURL) {
		Map<QueueAttributeName, String> attributes = new HashMap<>();
		String thePolicy = templatePolicy.replace(QUEUE_ARN, queueArn).replace(SNS_TOPIC_ARN, topicSnsArn);
		attributes.put(QueueAttributeName.POLICY, thePolicy);
		SetQueueAttributesRequest request = SetQueueAttributesRequest.builder().
				queueUrl(queueURL).attributes(attributes).build();
		sqsClient.setQueueAttributes(request);
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

}
