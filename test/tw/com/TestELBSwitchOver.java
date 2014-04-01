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
import tw.com.exceptions.InvalidParameterException;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Vpc;
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
	
	@Before
	public void beforeTestsRun() throws MissingArgumentException {
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestelb");
		
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new SNSMonitor(snsClient, sqsClient);
		aws = new AwsFacade(monitor, cfnClient, ec2Client, cfnRepository, vpcRepository);
		aws.setCommentTag(test.getMethodName());
		
		projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		monitor.init();
	}
	
	@After
	public void afterEachTestRuns() {
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestelb");
	}
	
	@Test
	public void testFindELBInVPC() throws FileNotFoundException, CfnAssistException, IOException, InvalidParameterException, InterruptedException {
		ELBRepository elbRepository = new ELBRepository(elbClient);
		aws.applyTemplate(new File(EnvironmentSetupForTests.ELB_FILENAME), projAndEnv);
	
		Vpc vpc = vpcRepository.getCopyOfVpc(projAndEnv);
		LoadBalancerDescription elb = elbRepository.findELBFor(vpc.getVpcId());
		
		assertNotNull(elb);
	}
}
