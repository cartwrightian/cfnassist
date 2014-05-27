package tw.com.integration;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.CfnRepository;
import tw.com.EnvironmentSetupForTests;
import tw.com.PollingStackMonitor;
import tw.com.SNSMonitor;
import tw.com.StackId;
import tw.com.exceptions.CfnAssistException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestStackMonitors {

	private static final String CREATE_COMPLETE = StackStatus.CREATE_COMPLETE.toString();
	private static final String stackName = EnvironmentSetupForTests.TEMPORARY_STACK;
	private static AmazonCloudFormationClient cfnClient;
	private PollingStackMonitor pollingMonitor;
	private String vpcId;
	private SNSMonitor snsMonitor;
	private static AmazonSQSClient sqsClient;
	private static AmazonSNSClient snsClient;
	private DeletesStacks deletesStacks;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
	}

	@Before
	public void beforeTestsRun() {
		CfnRepository cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);	
		pollingMonitor = new PollingStackMonitor(cfnRepository);	
		vpcId = EnvironmentSetupForTests.VPC_ID_FOR_ALT_ENV;
		snsMonitor = new SNSMonitor(snsClient, sqsClient);
		deletesStacks = new DeletesStacks(cfnClient).ifPresent(stackName);;
	}
	
	@After
	public void afterEachTestRuns() {
		deletesStacks.act();
	}
	
	@Test
	public void ShouldCheckStackHasBeenCreated() throws CfnAssistException, InterruptedException, IOException {
		StackId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId,"");
		assertEquals(CREATE_COMPLETE, pollingMonitor.waitForCreateFinished(id));
	}
	
	@Test
	public void ShouldCheckStackHasBeenCreatedSNS() throws CfnAssistException, InterruptedException, IOException, MissingArgumentException {	
		snsMonitor.init();
		StackId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId, snsMonitor.getArn());
		assertEquals(CREATE_COMPLETE, snsMonitor.waitForCreateFinished(id));
	}
	
	@Test 
	public void ShouldCheckStackHasBeenDeleted() throws CfnAssistException, InterruptedException, IOException {
		StackId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId,"");
		pollingMonitor.waitForCreateFinished(id);
		new DeletesStacks(cfnClient).ifPresentNonBlocking(stackName).act(); 
		pollingMonitor.waitForDeleteFinished(id);
	}
	
	@Test 
	public void ShouldCheckStackHasBeenDeletedWithSNS() throws CfnAssistException, InterruptedException, IOException, MissingArgumentException {
		snsMonitor.init();
		StackId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId,snsMonitor.getArn());
		snsMonitor.waitForCreateFinished(id);
		new DeletesStacks(cfnClient).ifPresentNonBlocking(stackName).act(); 
		snsMonitor.waitForDeleteFinished(id);
	}

}
