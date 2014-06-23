package tw.com.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.AwsProvider;
import tw.com.CfnRepository;
import tw.com.DeletesStacks;
import tw.com.EnvironmentSetupForTests;
import tw.com.EnvironmentTag;
import tw.com.FilesForTesting;
import tw.com.MonitorStackEvents;
import tw.com.NotReadyException;
import tw.com.PollingStackMonitor;
import tw.com.ProjectAndEnv;
import tw.com.StackId;
import tw.com.VpcRepository;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

public class TestCfnRepository {

	private static final String TEST_CIDR_SUBNET = "testCidrSubnet";
	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client directClient;
	private static VpcRepository vpcRepository;
	
	private Vpc mainTestVPC;
	private AwsProvider awsProvider;
	private Vpc otherVPC;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	private ProjectAndEnv altProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	private static CfnRepository cfnRepository;
	private DeletesStacks deletesStacks;

	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);	
		vpcRepository = new VpcRepository(directClient);
		cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		
		new DeletesStacks(cfnClient).ifPresent("CfnAssistTestcausesRollBack")
			.ifPresent("CfnAssistTestsubnetWithCIDRParam").act();
	}
	
	@Rule public TestName test = new TestName();
	
	@Before
	public void beforeEachTestIsRun() {						
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository );
		awsProvider = new AwsFacade(monitor , cfnClient, cfnRepository, vpcRepository);
		awsProvider.setCommentTag(test.getMethodName());
		
		mainTestVPC = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		otherVPC = vpcRepository.getCopyOfVpc(altProjectAndEnv);
		
		deletesStacks = new DeletesStacks(cfnClient);
	}
	
	@After
	public void afterEachTestHasRun() {
		deletesStacks.act();
	}
	
	@Test
	public void shouldFindResourceFromCorrectVPC() throws FileNotFoundException, IOException, InvalidParameterException, CfnAssistException, InterruptedException {	
		String vpcIdA = mainTestVPC.getVpcId();
		String vpcIdB = otherVPC.getVpcId();
		String cidrA = "10.0.10.0/24";
		String cidrB = "10.0.11.0/24";
	
		//create two subnets with same logical id's but different VPCs		
		StackId stackA = invokeSubnetCreation(cidrA, mainProjectAndEnv);	
		StackId stackB = invokeSubnetCreation(cidrB,  altProjectAndEnv);
		
		// find the id's
		String physicalIdA = cfnRepository.findPhysicalIdByLogicalId(new EnvironmentTag(EnvironmentSetupForTests.ENV), TEST_CIDR_SUBNET);
		String physicalIdB = cfnRepository.findPhysicalIdByLogicalId(new EnvironmentTag(EnvironmentSetupForTests.ALT_ENV), TEST_CIDR_SUBNET);
		
		// fetch the subnet id directly
		DescribeSubnetsResult subnetResultsA = getSubnetDetails(physicalIdA);
		DescribeSubnetsResult subnetResultsB = getSubnetDetails(physicalIdB);
		
		// remove the stacks before we do any validation to make sure things left in clean state
		deletesStacks.ifPresent(stackA).ifPresent(stackB);
	
		// check we found the physical ids
		assertNotNull(physicalIdA);
		assertNotNull(physicalIdB);
		assert(!physicalIdA.equals(physicalIdB));	
		
		// validate the subnets have the expected address block and is from the correct VPC
		assertEquals(1, subnetResultsA.getSubnets().size());
		assertEquals(1, subnetResultsB.getSubnets().size());
		
		Subnet subnetA = subnetResultsA.getSubnets().get(0);
		assertEquals(cidrA, subnetA.getCidrBlock());
		assertEquals(vpcIdA, subnetA.getVpcId());
		
		Subnet subnetB = subnetResultsB.getSubnets().get(0);
		assertEquals(cidrB, subnetB.getCidrBlock());
		assertEquals(vpcIdB, subnetB.getVpcId());
	}
	
	@Test
	public void detectsRollbackStatusOfStack() throws FileNotFoundException, CfnAssistException, NotReadyException, IOException, InvalidParameterException, InterruptedException {
		
		String name = "CfnAssistTestcausesRollBack";
		try {
			awsProvider.applyTemplate(new File(FilesForTesting.CAUSEROLLBACK), mainProjectAndEnv);
			fail("Expected exception");
		} catch (WrongStackStatus e) {
			// expected
		}
		
		String result = StackStatus.ROLLBACK_IN_PROGRESS.toString();
		while (result.equals(StackStatus.ROLLBACK_IN_PROGRESS.toString())) {
			result = cfnRepository.getStackStatus(name);
			Thread.sleep(2500);
		}
		
		deletesStacks.ifPresent(name).act();
		
		assertEquals(StackStatus.ROLLBACK_COMPLETE.toString(), result);	
	}
	
	@Test
	public void emptyStatusIfNoSuchStatck() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, IOException, InvalidParameterException, InterruptedException {			
		String result = cfnRepository.getStackStatus("thisStackShouldNotExist");
		
		assertEquals(0, result.length());
	}

	private DescribeSubnetsResult getSubnetDetails(String physicalId) {
		DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest();
		Collection<String> subnetIds = new LinkedList<String>();
		subnetIds.add(physicalId);
		describeSubnetsRequest.setSubnetIds(subnetIds);
		DescribeSubnetsResult result = directClient.describeSubnets(describeSubnetsRequest);
		return result;
	}

	private StackId invokeSubnetCreation(String cidr, ProjectAndEnv projectAndEnv)
			throws FileNotFoundException, IOException,
			InvalidParameterException, CfnAssistException, InterruptedException {
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		Parameter cidrParameter = new Parameter();
		cidrParameter.setParameterKey("cidr");
		cidrParameter.setParameterValue(cidr);
		parameters.add(cidrParameter);
		File templateFile = new File(FilesForTesting.SUBNET_CIDR_PARAM);
		return awsProvider.applyTemplate(templateFile, projectAndEnv, parameters );
	}


}
