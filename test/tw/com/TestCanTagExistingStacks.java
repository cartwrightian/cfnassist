package tw.com;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;

public class TestCanTagExistingStacks {
	
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	
	private ProjectAndEnv projectAndEnv;
	private AwsProvider aws;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
	}

	@Before 
	public void beforeEachTestRuns() throws IOException, WrongNumberOfStacksException, StackCreateFailed, InterruptedException {
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
		
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		Vpc vpc = vpcRepository.getCopyOfVpc(projectAndEnv);
		
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository );
		
		StackId stackId = EnvironmentSetupForTests.createTemporarySimpleStack(cfnClient, vpc.getVpcId(),"");	
		monitor.waitForCreateFinished(stackId);
		
		aws = new AwsFacade(monitor, cfnClient, ec2Client, cfnRepository, vpcRepository);	
	}
	
	@After
	public void afterEachTestRuns() throws InterruptedException, TimeoutException, WrongNumberOfStacksException {
		EnvironmentSetupForTests.deleteStack(cfnClient, EnvironmentSetupForTests.TEMPORARY_STACK,false);
	}
	
	@Ignore("Does not seem way to lable at existing stack via the apis")
	@Test
	public void shouldBeAbleToLabelExistingStack() throws IOException, CfnAssistException {
		aws.initEnvAndProjectForStack(EnvironmentSetupForTests.TEMPORARY_STACK, projectAndEnv);
		
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		String createdSubnet = cfnRepository.findPhysicalIdByLogicalId(new EnvironmentTag(projectAndEnv.getEnv()), "testSubnet");
		
		assertNotNull(createdSubnet);
	}
	
	@Test
	@Ignore("Does not seem way to lable at existing stack via the apis")
	public void shouldNotBeAbleToOverwriteExistingStackEnvAndProject() {
		fail("todo once id way to make this work with apis");
	}
	
}
