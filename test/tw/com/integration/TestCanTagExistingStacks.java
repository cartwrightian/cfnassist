package tw.com.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CloudClient;
import tw.com.providers.CloudFormationClient;
import tw.com.repository.CfnRepository;
import tw.com.repository.ResourceRepository;
import tw.com.repository.StackRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

public class TestCanTagExistingStacks {
	
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private ProjectAndEnv projectAndEnv;

	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
	}

	@Before 
	public void beforeEachTestRuns() throws IOException, CfnAssistException, InterruptedException {
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
		
		VpcRepository vpcRepository = new VpcRepository(new CloudClient(ec2Client));
		Vpc vpc = vpcRepository.getCopyOfVpc(projectAndEnv);
		
		StackRepository cfnRepository = new CfnRepository(new CloudFormationClient(cfnClient), EnvironmentSetupForTests.PROJECT);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository );
		
		StackNameAndId stackId = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpc.getVpcId(),"");	
		monitor.waitForCreateFinished(stackId);
	}
	
	@After
	public void afterEachTestRuns() throws InterruptedException, TimeoutException, WrongNumberOfStacksException {
		DeletesStacks deletesStacks = new DeletesStacks(cfnClient).ifPresentNonBlocking(EnvironmentSetupForTests.TEMPORARY_STACK);
		deletesStacks.act();
	}
	
	@Ignore("Does not seem way to lable at existing stack via the apis")
	@Test
	public void shouldBeAbleToLabelExistingStack() throws IOException, CfnAssistException {

		ResourceRepository cfnRepository = new CfnRepository(new CloudFormationClient(cfnClient), EnvironmentSetupForTests.PROJECT);
		String createdSubnet = cfnRepository.findPhysicalIdByLogicalId(new EnvironmentTag(projectAndEnv.getEnv()), "testSubnet");
		
		assertNotNull(createdSubnet);
	}
	
	@Test
	@Ignore("Does not seem way to lable at existing stack via the apis")
	public void shouldNotBeAbleToOverwriteExistingStackEnvAndProject() {
		fail("todo once id way to make this work with apis");
	}
	
}
