package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestELBSwitchOver {
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	private static AmazonElasticLoadBalancingClient elbClient;
	
	private AwsProvider aws;
	private ProjectAndEnv projAndEnv;
	private MonitorStackEvents monitor;
	private VpcRepository vpcRepository;
	
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
		elbClient = EnvironmentSetupForTests.createELBClient(credentialsProvider);
	}
	
	@Rule public TestName test = new TestName();
	private CfnRepository cfnRepository;
	
	@Before
	public void beforeTestsRun() throws MissingArgumentException {
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestelb");
		
		cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new SNSMonitor(snsClient, sqsClient);
		aws = new AwsFacade(monitor, cfnClient, ec2Client, cfnRepository, vpcRepository);
		aws.setCommentTag(test.getMethodName());
		
		projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		monitor.init();
	}
	
	@After
	public void afterEachTestRuns() {	
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssist123Testinstance");
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssist567Testinstance");
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestelb");
	}
	
	@Test
	public void testShouldFindELBInTheVPC() throws FileNotFoundException, WrongNumberOfStacksException, StackCreateFailed, NotReadyException, WrongStackStatus, DuplicateStackException, IOException, InvalidParameterException, InterruptedException {
		ELBRepository elbRepository = new ELBRepository(elbClient, vpcRepository, cfnRepository);
		aws.applyTemplate(new File(EnvironmentSetupForTests.ELB_FILENAME), projAndEnv);
		
		LoadBalancerDescription elb = elbRepository.findELBFor(projAndEnv);
		
		assertNotNull(elb);
		assertEquals(0, elb.getInstances().size());
	}
	
	@Test
	public void shouldJustAddInstancesWithMatchingBuildNumber() throws FileNotFoundException, CfnAssistException, IOException, InvalidParameterException, InterruptedException {
		ELBRepository elbRepository = new ELBRepository(elbClient, vpcRepository, cfnRepository);
		aws.applyTemplate(new File(EnvironmentSetupForTests.ELB_FILENAME), projAndEnv);
		
		projAndEnv.addBuildNumber("123");
		aws.applyTemplate(new File(EnvironmentSetupForTests.INSTANCE_FILENAME), projAndEnv);
		ProjectAndEnv projAndEnvDiffBuild = EnvironmentSetupForTests.getMainProjectAndEnv();
		
		projAndEnvDiffBuild.addBuildNumber("567");
		aws.applyTemplate(new File(EnvironmentSetupForTests.INSTANCE_FILENAME), projAndEnvDiffBuild);

		elbRepository.updateELBInstancesThatMatchBuild(projAndEnv);
		
		LoadBalancerDescription elb = elbRepository.findELBFor(projAndEnv);
		assertEquals(1, elb.getInstances().size());
	}	
}
