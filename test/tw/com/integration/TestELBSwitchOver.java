package tw.com.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.AwsProvider;
import tw.com.CfnRepository;
import tw.com.ELBRepository;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.NotReadyException;
import tw.com.ProjectAndEnv;
import tw.com.SNSMonitor;
import tw.com.VpcRepository;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class TestELBSwitchOver {
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonSNSClient snsClient;
	private static AmazonSQSClient sqsClient;
	private static AmazonElasticLoadBalancingClient elbClient;
	
	private static AwsProvider aws;
	private static ProjectAndEnv projAndEnv;
	private static MonitorStackEvents monitor;
	private static VpcRepository vpcRepository;
	private static ProjectAndEnv projAndEnvDiffBuild;
	
	private static String webType = "web";
	private static DeletesStacks deleter;
		
	@BeforeClass
	public static void beforeAllTestsOnce() throws FileNotFoundException, CfnAssistException, NotReadyException, IOException, InvalidParameterException, InterruptedException, MissingArgumentException {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		snsClient = EnvironmentSetupForTests.createSNSClient(credentialsProvider);
		sqsClient = EnvironmentSetupForTests.createSQSClient(credentialsProvider);
		elbClient = EnvironmentSetupForTests.createELBClient(credentialsProvider);
		
		deleter = new DeletesStacks(cfnClient);
		deleter.ifPresent("CfnAssist123Testinstance")
			.ifPresent("CfnAssist123TestinstanceWithTypeTag")
			.ifPresent("CfnAssist567TestinstanceWithTypeTag")
			.ifPresent("CfnAssistTestelb");
		deleter.act();
		
		/////
		// ELB very slow to create and delete so do it once for all the tests
		////
		cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		vpcRepository = new VpcRepository(ec2Client);
		
		monitor = new SNSMonitor(snsClient, sqsClient);
		aws = new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);
		monitor.init();
		projAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
		aws.applyTemplate(FilesForTesting.ELB, projAndEnv);
		
		projAndEnv.addBuildNumber("123");
		aws.applyTemplate(FilesForTesting.INSTANCE, projAndEnv); // should get ignored, no type tag
		aws.applyTemplate(FilesForTesting.INSTANCE_WITH_TYPE, projAndEnv);
		
		projAndEnvDiffBuild = EnvironmentSetupForTests.getMainProjectAndEnv();
		projAndEnvDiffBuild.addBuildNumber("567");
		aws.applyTemplate(FilesForTesting.INSTANCE_WITH_TYPE, projAndEnvDiffBuild);
	}
	
	@AfterClass
	public static void afterAllTheTestsHaveRun() {	
		deleter.act();	
	}
	
	@Rule public TestName test = new TestName();
	private static CfnRepository cfnRepository;

	@Before
	public void beforeTestsRun() throws MissingArgumentException {	
		aws.setCommentTag(test.getMethodName());	
	}
	
	@Test
	public void testShouldFindELBInTheVPC() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, IOException, InvalidParameterException, InterruptedException {
		ELBRepository elbRepository = new ELBRepository(elbClient, ec2Client, vpcRepository, cfnRepository);	
		LoadBalancerDescription elb = elbRepository.findELBFor(projAndEnv);
		
		assertNotNull(elb);
		assertEquals(0, elb.getInstances().size());
	}
	
	@Test
	public void shouldJAddInstancesWithMatchingBuildNumber() throws FileNotFoundException, CfnAssistException, IOException, InvalidParameterException, InterruptedException {
		ELBRepository elbRepository = new ELBRepository(elbClient, ec2Client, vpcRepository, cfnRepository);
		elbRepository.updateInstancesMatchingBuild(projAndEnv, webType);
		
		LoadBalancerDescription elb = elbRepository.findELBFor(projAndEnv);
		List<Instance> instances = elb.getInstances();
		assertEquals(1, instances.size());
		
		deregisterInstances(elb,instances);
	}

	private void deregisterInstances(LoadBalancerDescription elb, Collection<Instance> instances) {
		DeregisterInstancesFromLoadBalancerRequest request = new DeregisterInstancesFromLoadBalancerRequest();
		request.withLoadBalancerName(elb.getLoadBalancerName()).withInstances(instances);
		elbClient.deregisterInstancesFromLoadBalancer(request);
	}

	@Test
	public void shouldAddInstancesWithMatchingBuildNumberAndRemoveNonMatching() throws FileNotFoundException, CfnAssistException, IOException, InvalidParameterException, InterruptedException {
		ELBRepository elbRepository = new ELBRepository(elbClient, ec2Client, vpcRepository, cfnRepository);
	
		LoadBalancerDescription elb = elbRepository.findELBFor(projAndEnv);
		List<Instance> instances = elb.getInstances();
		assertEquals(0, instances.size());

		elbRepository.updateInstancesMatchingBuild(projAndEnvDiffBuild, webType); // add instance from build 567 first
		
		// check got added
		elb = elbRepository.findELBFor(projAndEnv);
		instances = elb.getInstances();
		assertEquals(1, instances.size());
		
		List<Instance> registeredInstances = elbRepository.updateInstancesMatchingBuild(projAndEnv, webType); // now switch to build 123
		
		// should still be 1
		elb = elbRepository.findELBFor(projAndEnv);
		instances = elb.getInstances();
		assertEquals(1, instances.size());
		assertEquals(1, registeredInstances.size());
		
		assertEquals(instances.get(0), registeredInstances.get(0));	
		deregisterInstances(elb, instances);
	}	
}
