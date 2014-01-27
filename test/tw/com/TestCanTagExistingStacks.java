package tw.com;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.model.Vpc;

public class TestCanTagExistingStacks {
	
	private ProjectAndEnv projectAndEnv;
	private AmazonCloudFormationClient cfnClient;
	private AwsProvider aws;

	@Before 
	public void beforeEachTestRuns() throws IOException, WrongNumberOfStacksException, StackCreateFailed, InterruptedException {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
		VpcRepository vpcRepository = new VpcRepository(EnvironmentSetupForTests.createEC2Client(credentialsProvider));
		Vpc vpc = vpcRepository.getCopyOfVpc(projectAndEnv);
		EnvironmentSetupForTests.createTemporaryStack(cfnClient, vpc.getVpcId());	
		
		aws = new AwsFacade(credentialsProvider, EnvironmentSetupForTests.getRegion());
		aws.waitForCreateFinished(EnvironmentSetupForTests.TEMPORARY_STACK);
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
