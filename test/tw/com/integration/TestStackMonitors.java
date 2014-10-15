package tw.com.integration;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.PollingStackMonitor;
import tw.com.SNSMonitor;
import tw.com.SetsDeltaIndex;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.providers.SNSEventSource;
import tw.com.repository.CfnRepository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestStackMonitors implements SetsDeltaIndex {

	private static final String DELETED = StackStatus.DELETE_COMPLETE.toString();
	private static final String CREATE_COMPLETE = StackStatus.CREATE_COMPLETE.toString();
	private static final String stackName = EnvironmentSetupForTests.TEMPORARY_STACK;
	private static AmazonCloudFormationClient cfnClient;
	private PollingStackMonitor pollingMonitor;
	private String vpcId;
	private SNSMonitor snsMonitor;
	private static AmazonSQSClient sqsClient;
	private static AmazonSNSClient snsClient;
	private DeletesStacks deletesStacks;
	private int deltaIndexResult;
	private SNSEventSource eventSource;
	
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
		deltaIndexResult = -1;
		CfnRepository cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);	
		pollingMonitor = new PollingStackMonitor(cfnRepository);	
		vpcId = EnvironmentSetupForTests.VPC_ID_FOR_ALT_ENV;
		eventSource = new SNSEventSource(snsClient, sqsClient);
		snsMonitor = new SNSMonitor(eventSource, cfnRepository);
		deletesStacks = new DeletesStacks(cfnClient).ifPresent(stackName);;
	}
	
	@After
	public void afterEachTestRuns() {
		deletesStacks.act();
	}
	
	@Test
	public void ShouldCheckStackHasBeenCreated() throws CfnAssistException, InterruptedException, IOException {
		StackNameAndId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId,"");
		assertEquals(CREATE_COMPLETE, pollingMonitor.waitForCreateFinished(id));
	}
	
	@Test
	public void ShouldCheckStackHasBeenCreatedSNS() throws CfnAssistException, InterruptedException, IOException, MissingArgumentException {	
		snsMonitor.init();
		StackNameAndId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId, eventSource.getArn());
		assertEquals(CREATE_COMPLETE, snsMonitor.waitForCreateFinished(id));
	}
	
	@Test 
	public void ShouldCheckStackHasBeenDeleted() throws CfnAssistException, InterruptedException, IOException {
		StackNameAndId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId,"");
		pollingMonitor.waitForCreateFinished(id);
		new DeletesStacks(cfnClient).ifPresentNonBlocking(stackName).act(); 
		assertEquals(DELETED, pollingMonitor.waitForDeleteFinished(id));
	}
	
	@Test 
	public void ShouldCheckStackHasBeenDeletedWithSNS() throws CfnAssistException, InterruptedException, IOException, MissingArgumentException {
		snsMonitor.init();
		StackNameAndId id = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpcId, eventSource.getArn());
		snsMonitor.waitForCreateFinished(id);
		new DeletesStacks(cfnClient).ifPresentNonBlocking(stackName).act(); 
		assertEquals(DELETED, snsMonitor.waitForDeleteFinished(id));
	}
	
	@Test
	public void ShouldCopeWithNonExistanceStack() throws WrongNumberOfStacksException, InterruptedException {
		StackNameAndId id = new StackNameAndId("alreadyGone", "11222");
		assertEquals(DELETED, pollingMonitor.waitForDeleteFinished(id));
	}
	
	@Test
	public void ShouldCopeWithNonExistanceStackSNS() throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus, MissingArgumentException {
		snsMonitor.init();
		StackNameAndId id = new StackNameAndId("alreadyGone", "11222");
		assertEquals(DELETED, snsMonitor.waitForDeleteFinished(id));
	}
	
	@Test
	public void ShouldCopeWithListOfNonExistanceStacksToDelete() {
		DeletionsPending pending = createPendingDeletes();
		pollingMonitor.waitForDeleteFinished(pending, this);
		assertEquals(2, deltaIndexResult);
	}
	
	@Test
	public void ShouldCopeWithListOfNonExistanceStacksToDeleteSNS() throws CfnAssistException {
		DeletionsPending pending = createPendingDeletes();
		snsMonitor.waitForDeleteFinished(pending, this);
		assertEquals(2, deltaIndexResult);
	}

	private DeletionsPending createPendingDeletes() {
		DeletionsPending pending = new DeletionsPending();
		pending.add(3, new StackNameAndId("alreadyGone1","11")); // final index should end up as 2
		pending.add(4, new StackNameAndId("alreadyGone2","12"));
		pending.add(5, new StackNameAndId("alreadyGone3","13"));
		return pending;
	}

	@Override
	public void setDeltaIndex(int newDelta) throws CannotFindVpcException {
		this.deltaIndexResult = newDelta;	
	}

}
