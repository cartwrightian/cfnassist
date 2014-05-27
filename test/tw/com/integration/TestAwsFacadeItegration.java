package tw.com.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.AwsProvider;
import tw.com.CfnRepository;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.NotReadyException;
import tw.com.PollingStackMonitor;
import tw.com.ProjectAndEnv;
import tw.com.SNSMonitor;
import tw.com.StackEntry;
import tw.com.StackId;
import tw.com.VpcRepository;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestAwsFacadeItegration {

	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	
	private AwsProvider aws;
	private ProjectAndEnv projectAndEnv;
	private MonitorStackEvents monitor;
	private VpcRepository vpcRepository;
	private CfnRepository cfnRepository;

	private DeletesStacks deletesStacks;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
	}
	
	@Rule public TestName testName = new TestName();
	
	@Before
	public void beforeTestsRun() {
		cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new PollingStackMonitor(cfnRepository);	
		aws = new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);
		aws.setCommentTag(testName.getMethodName());
		projectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		
		deletesStacks = new DeletesStacks(cfnClient).ifPresent("CfnAssistTestsimpleStack").ifPresent("CfnAssistTestsubnet");
		deletesStacks.act();
	}
	
	@After 
	public void afterEachTestRuns() {
		deletesStacks.act();
	}
	
	@Test
	public void createsSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		StackId result = aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);	
		
		assertNotNull(result.getStackId());
		assertTrue(result.getStackId().length()>0);
		assertEquals("CfnAssistTestsimpleStack", result.getStackName());
	}
	
	@Test
	public void createsAndThenUpdateSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		Vpc vpc = vpcRepository.getCopyOfVpc(projectAndEnv);
		
		StackId before = aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK), projectAndEnv);
		List<Subnet> beforeSubnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);	
		String beforeID = cfnRepository.findPhysicalIdByLogicalId(projectAndEnv.getEnvTag(), "testSubnet");
		
		StackId after = aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK_DELTA), projectAndEnv);
		List<Subnet> afterSubnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		String afterID = cfnRepository.findPhysicalIdByLogicalId(projectAndEnv.getEnvTag(), "testSubnet");

		deletesStacks.ifPresent(before);
		
		assertEquals(before.getStackId(), after.getStackId()); // an update, so stack ID is the same
		
		assertEquals(1, beforeSubnets.size());
		assertEquals("10.0.10.0/24", beforeSubnets.get(0).getCidrBlock());
		assertEquals(1, afterSubnets.size()); // should have updated an existing subnet, so same number of subnets
		assertEquals("10.0.99.0/24", afterSubnets.get(0).getCidrBlock());
		
		assertFalse(beforeID.equals(afterID)); // changing the CIDR means cloud formation recreates the subnet, meaning the physical ID changes
	}
	
	@Test
	public void catchIssueWithMonitoringUpdateWithSNSWhenOriginalStackHadNoSNSNotif() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException, MissingArgumentException {
		
		aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK), projectAndEnv);
		
		MonitorStackEvents snsMonitor = new SNSMonitor(snsClient, sqsClient);
		snsMonitor.init();
		AwsFacade snsAws = new AwsFacade(snsMonitor, cfnClient, cfnRepository, vpcRepository);
		try {		
			snsAws.applyTemplate(new File(FilesForTesting.SUBNET_STACK_DELTA), projectAndEnv);
			fail("Should have thrown");
		}
		catch(InvalidParameterException expected) {
			// expected this as SNS notifications will not be available on the stack
		}	
	}
	
	@Test
	public void deleteSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		
		DescribeStacksResult before = cfnClient.describeStacks();
		File templateFile = new File(FilesForTesting.SIMPLE_STACK);
		aws.applyTemplate(templateFile, projectAndEnv);	
		
		aws.deleteStackFrom(templateFile, projectAndEnv);
		DescribeStacksResult after = cfnClient.describeStacks();
		
		assertEquals(before.getStacks().size(), after.getStacks().size());
	}
	
	@Test
	public void deleteSimpleStackWithBuildNumber() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		
		DescribeStacksResult before = cfnClient.describeStacks();
		File templateFile = new File(FilesForTesting.SIMPLE_STACK);
		projectAndEnv.addBuildNumber("987");
		aws.applyTemplate(templateFile, projectAndEnv);	
		
		aws.deleteStackFrom(templateFile, projectAndEnv);
		DescribeStacksResult after = cfnClient.describeStacks();
		
		deletesStacks.ifPresent("CfnAssist987TestsimpleStack");
		assertEquals(before.getStacks().size(), after.getStacks().size());
	}
	
	@Test
	public void catchesStackAlreadyExistingAsExpected() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		StackId stackId = aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);	
		
		try {
			aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);
			fail("Should have thrown exception");
		} 
		catch(DuplicateStackException expected) {
			// expected
		}
		
		deletesStacks.ifPresent(stackId);
	}
	
	@Test
	public void handlesRollBackCompleteStatusAutomatically() throws FileNotFoundException, CfnAssistException, NotReadyException, IOException, InvalidParameterException, InterruptedException {

		StackId id = null;
		try {
			aws.applyTemplate(new File(FilesForTesting.CAUSEROLLBACK), projectAndEnv);
			fail("should have thrown");
		} catch (WrongStackStatus exception) {
			id = exception.getStackId();
		}	
		monitor.waitForRollbackComplete(id);
		try {
			aws.applyTemplate(new File(FilesForTesting.CAUSEROLLBACK), projectAndEnv);
			fail("should have thrown");
		} catch (WrongStackStatus exception) {
			// expected a create fail, and *not* a duplicate stack exception
			id = exception.getStackId();
		}	
		deletesStacks.ifPresent("CfnAssistTestcausesRollBack");
	}
	
	@Test
	public void canListOutCfnAssistStacks() throws FileNotFoundException, CfnAssistException, NotReadyException, IOException, InvalidParameterException, InterruptedException {
		StackId id = aws.applyTemplate(new File(FilesForTesting.SIMPLE_STACK), projectAndEnv);
		
		List<StackEntry> results = aws.listStacks(projectAndEnv);

		boolean seenEntry = false;
		for(StackEntry result : results) {
			if (result.getStack().getStackId().equals(id.getStackId())) {
				assertEquals("CfnAssistTestsimpleStack", result.getStackName());
				assertEquals("Test", result.getEnvTag().getEnv());
				assertEquals("CfnAssist", result.getProject());
				seenEntry = true;
				break;
			}
		}
		assertTrue(seenEntry);
		
	}

	
	
}
