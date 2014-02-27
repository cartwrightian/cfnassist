package tw.com;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestSnsMonitor {
	
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	
	String sampleText = "StackName='temporaryStack'\nStackId='arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496'\nEventId='9afc1e30-9eff-11e3-b6e7-506cf935a496'\nLogicalResourceId='temporaryStack'\nPhysicalResourceId='arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496'\nResourceType='AWS::CloudFormation::Stack'\nTimestamp='2014-02-26T16:04:00.438Z'\nResourceStatus='CREATE_COMPLETE'\nResourceStatusReason=''\nResourceProperties=''\n";

	@BeforeClass
	public static void beforeTheTestsAreRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
	}
	
	// TODO Messaging based tests
	
	@Test
	public void testCanParseMessageNodeTestNameMatches() {
		
		SNSMonitor monitor = new SNSMonitor(snsClient, sqsClient);
		StackNotification notification = monitor.parseNotificationMessage("temporaryStack", sampleText);	
		
		assertEquals("temporaryStack", notification.getStackName());
		assertEquals("CREATE_COMPLETE", notification.getStatus());
	}
	
}
