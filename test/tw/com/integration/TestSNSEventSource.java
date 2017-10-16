package tw.com.integration;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import org.apache.commons.cli.MissingArgumentException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.StackNotification;
import tw.com.exceptions.FailedToCreateQueueException;
import tw.com.exceptions.NotReadyException;
import tw.com.providers.SNSEventSource;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestSNSEventSource {
	
	private static AmazonSNS snsClient;
	private static AmazonSQS sqsClient;
	
	SNSEventSource eventSource;

	@BeforeClass
	public static void beforeAllTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		snsClient = EnvironmentSetupForTests.createSNSClient();
		sqsClient = EnvironmentSetupForTests.createSQSClient();
	}
	
	@Before
	public void beforeEachTestRuns() {
		eventSource = new SNSEventSource(snsClient, sqsClient);
	}

	@Test
	public void shouldThrowIfNotInit() {	
		try {
			eventSource.receiveNotifications();
			fail("should have thrown");
		}
		catch(NotReadyException expectedException) {
			// expected
		}
	}
	
	@Test
	public void shouldCreateSNSAndSQSPlusPolicyAsNeeded() throws MissingArgumentException, NotReadyException, FailedToCreateQueueException, InterruptedException {
		eventSource.init();
		String existingSNSARN = eventSource.getSNSArn();
		
		// reset the queue, sns and subscription (this forces policy recreation as well)
		String sub = eventSource.getARNofSQSSubscriptionToSNS();
		if (sub!=null) {
			snsClient.unsubscribe(sub);
		}
		snsClient.deleteTopic(existingSNSARN);
		sqsClient.deleteQueue(eventSource.getQueueURL());
		
		// now recreate the source and make sure we can send/receive
		SNSEventSource anotherEventSource = new SNSEventSource(snsClient, sqsClient);
		anotherEventSource.init();
		// should be able to send via sns and then receive from sqs if everything worked ok
		snsClient.publish(anotherEventSource.getSNSArn(), "aMessage");
		ReceiveMessageRequest request = new ReceiveMessageRequest().
				withQueueUrl(anotherEventSource.getQueueURL()).
				withWaitTimeSeconds(10);
		ReceiveMessageResult result = sqsClient.receiveMessage(request);
		assertTrue(result.getMessages().size()>0);
	}
	
	@Test 
	public void shouldReceiveNotifications() throws MissingArgumentException, NotReadyException, FailedToCreateQueueException, InterruptedException {
		String stackId = UUID.randomUUID().toString();
		String messageContents = String.format("StackName='temporaryStack'\nStackId='%s'\nEventId='9afc1e30-9eff-11e3-b6e7-506cf935a496'\nLogicalResourceId='temporaryStack'\nPhysicalResourceId='arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496'\nResourceType='AWS::CloudFormation::Stack'\nTimestamp='2014-02-26T16:04:00.438Z'\nResourceStatus='CREATE_COMPLETE'\nResourceStatusReason=''\nResourceProperties=''\n",
				stackId);
		
		eventSource.init();	
		
		String topicArn = eventSource.getSNSArn();
		snsClient.publish(topicArn, "willNotParse");
		snsClient.publish(topicArn, messageContents);
		snsClient.publish(topicArn, "AlsoWillNotParse");
		
		int attempts = 10;	
		boolean found = false;
		while((attempts>0) && (!found)) {
			List<StackNotification> results = eventSource.receiveNotifications();
			for(StackNotification candidate : results) {
				if (stackId.equals(candidate.getStackId())) {
					found = true;
					break;
				}
			}
			attempts--;
		}
		assertTrue(found);
	}

}
