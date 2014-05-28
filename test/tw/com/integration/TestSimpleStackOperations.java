package tw.com.integration;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

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
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

public class TestSimpleStackOperations {
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	
	private static AwsProvider aws;
	private static ProjectAndEnv projectAndEnv;
	private static MonitorStackEvents monitor;
	private static VpcRepository vpcRepository;
	private static CfnRepository cfnRepository;

	private static DeletesStacks deletesStacks;
	private static StackId theStack;
	private static DefaultAWSCredentialsProviderChain credentialsProvider;
	
	@BeforeClass
	public static void beforeAllTestsOnce() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, IOException, InvalidParameterException, InterruptedException {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		
		deletesStacks = new DeletesStacks(cfnClient).ifPresent("CfnAssistTestsubnet");
		deletesStacks.act();
		
		cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new PollingStackMonitor(cfnRepository);	
		aws = new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);
		projectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		
		theStack = aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK), projectAndEnv);	
	}
	
	@AfterClass
	public static void afterAllTestsHaveRun() {
		deletesStacks.act();
	}
	
	@Rule public TestName testName = new TestName();
	
	@Before
	public void beforeTestsRun() {
		aws.setCommentTag(testName.getMethodName());	
	}

	@Test
	public void createdSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
			
		assertNotNull(theStack.getStackId());
		assertTrue(theStack.getStackId().length()>0);
		assertEquals("CfnAssistTestsubnet", theStack.getStackName());
	}
	
	@Test
	public void catchIssueWithMonitoringUpdateWithSNSWhenOriginalStackHadNoSNSNotif() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException, MissingArgumentException {
				
		AmazonSNSClient snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		AmazonSQSClient sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
		
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
	public void catchesStackAlreadyExistingAsExpected() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		
		try {
			aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK), projectAndEnv);
			fail("Should have thrown exception");
		} 
		catch(DuplicateStackException expected) {
			// expected
		}		
	}
		
	@Test
	public void createsAndThenUpdateSimpleStack() throws FileNotFoundException, IOException, CfnAssistException, 
		InterruptedException, InvalidParameterException {
		Vpc vpc = vpcRepository.getCopyOfVpc(projectAndEnv);
		
		List<Subnet> beforeSubnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);	
		String beforeID = cfnRepository.findPhysicalIdByLogicalId(projectAndEnv.getEnvTag(), "testSubnet");
		
		StackId after = aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK_DELTA), projectAndEnv);
		List<Subnet> afterSubnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		String afterID = cfnRepository.findPhysicalIdByLogicalId(projectAndEnv.getEnvTag(), "testSubnet");
		
		assertEquals(theStack.getStackId(), after.getStackId()); // an update, so stack ID is the same
		
		assertEquals(1, beforeSubnets.size());
		assertEquals("10.0.10.0/24", beforeSubnets.get(0).getCidrBlock());
		assertEquals(1, afterSubnets.size()); // should have updated an existing subnet, so same number of subnets
		assertEquals("10.0.99.0/24", afterSubnets.get(0).getCidrBlock());
		
		assertFalse(beforeID.equals(afterID)); // changing the CIDR means cloud formation recreates the subnet, meaning the physical ID changes
	}
	
	@Test
	public void canListOutCfnAssistStacks() throws FileNotFoundException, CfnAssistException, NotReadyException, IOException, InvalidParameterException, InterruptedException {		
		List<StackEntry> results = aws.listStacks(projectAndEnv);

		boolean seenEntry = false;
		for(StackEntry result : results) {
			if (result.getStack().getStackId().equals(theStack.getStackId())) {
				assertEquals("CfnAssistTestsubnet", result.getStackName());
				assertEquals("Test", result.getEnvTag().getEnv());
				assertEquals("CfnAssist", result.getProject());
				seenEntry = true;
				break;
			}
		}
		assertTrue(seenEntry);	
	}

}
