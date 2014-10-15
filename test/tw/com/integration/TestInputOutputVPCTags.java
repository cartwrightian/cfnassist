package tw.com.integration;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.AwsFacade;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.repository.CfnRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestInputOutputVPCTags {
	private static AmazonEC2Client ec2Client;
	private static AmazonCloudFormationClient cfnClient;
	private static VpcRepository vpcRepository;
	private static AwsFacade aws;
	private static String env = EnvironmentSetupForTests.ENV;
	private static String proj = EnvironmentSetupForTests.PROJECT;
	private static ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(proj, env);
	private DeletesStacks deletesStacks;
	
	@BeforeClass
	public static void beforeAllTestsOnce() throws FileNotFoundException, CfnAssistException, IOException, InvalidParameterException, InterruptedException {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
		vpcRepository = new VpcRepository(ec2Client);
		
		CfnRepository cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository);
		aws = new AwsFacade(monitor, cfnRepository, vpcRepository);
	}

	@Before
	public void beforeEachTestRuns() {
		deletesStacks = new DeletesStacks(cfnClient);
		deletesStacks.ifPresent("CfnAssistTestsubnetWithVPCTagParam");
		deletesStacks.act();
	}
	
	@After
	public void afterEachTestRuns() {
		deletesStacks.act();
	}
	
	@Test
	public void testValueFromVPCTagAutopopulatedIntoTemplate() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, DuplicateStackException, IOException, InvalidParameterException, InterruptedException, CannotFindVpcException {
		vpcRepository.setVpcTag(mainProjectAndEnv, "testVPCTAG", "originalTagValue");
		aws.applyTemplate(new File(FilesForTesting.SUBNET_STACK_WITH_VPCTAG_PARAM), mainProjectAndEnv);
		vpcRepository.deleteVpcTag(mainProjectAndEnv, "testVPCTAG");
		
		// fetch created subnet and check the VPC tag got propogated through
		Vpc vpc = vpcRepository.getCopyOfVpc(mainProjectAndEnv);	
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		
		assertEquals(1,subnets.size());
		Subnet testSubnet = subnets.get(0);
		List<Tag> tags = testSubnet.getTags();
		Collection<Tag> expectedTags = new LinkedList<Tag>();
		expectedTags.add(new Tag().withKey("expectedTAG").withValue("originalTagValue"));
		assert(tags.containsAll(expectedTags));	
	}
	
}
