package tw.com.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
import tw.com.StackId;
import tw.com.VpcRepository;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.ec2.AmazonEC2Client;

public class TestCanDeleteAndHandleRollback {

	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	
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
		
}
