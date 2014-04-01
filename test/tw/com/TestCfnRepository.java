package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
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
	
	private Vpc mainTestVPC;
	private AwsProvider awsProvider;
	private File templateFile;
	private Vpc otherVPC;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	private ProjectAndEnv altProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	private CfnRepository cfnRepository;

	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestcausesRollBack");
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestsubnetWithCIDRParam");
	}
	
	@Rule public TestName test = new TestName();
	
	@Before
	public void beforeEachTestIsRun() {				
		VpcRepository vpcRepository = new VpcRepository(directClient);
		
		templateFile = new File("src/cfnScripts/subnetWithCIDRParam.json");
		cfnRepository = new CfnRepository(cfnClient);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository );
		awsProvider = new AwsFacade(monitor , cfnClient, directClient, cfnRepository, vpcRepository);
		awsProvider.setCommentTag(test.getMethodName());
		
		mainTestVPC = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		otherVPC = vpcRepository.getCopyOfVpc(altProjectAndEnv);
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
		EnvironmentSetupForTests.validatedDelete(stackA, awsProvider);	
		EnvironmentSetupForTests.validatedDelete(stackB, awsProvider);
		
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
	public void detectsRollbackStatusOfStack() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, IOException, InvalidParameterException, InterruptedException, WrongStackStatus, DuplicateStackException {
		
		String name = "CfnAssistTestcausesRollBack";
		try {
			awsProvider.applyTemplate(new File(EnvironmentSetupForTests.CAUSEROLLBACK), mainProjectAndEnv);
			fail("Expected exception");
		} catch (StackCreateFailed e) {
			// expected
		}
		
		String result = StackStatus.ROLLBACK_IN_PROGRESS.toString();
		while (result.equals(StackStatus.ROLLBACK_IN_PROGRESS.toString())) {
			result = cfnRepository.getStackStatus(name);
			Thread.sleep(2500);
		}
		
		EnvironmentSetupForTests.deleteStack(cfnClient, name, true);
		
		assertEquals(StackStatus.ROLLBACK_COMPLETE.toString(), result);	
	}
	
	@Test
	public void emptyStatusIfNoSuchStatck() throws FileNotFoundException, WrongNumberOfStacksException, StackCreateFailed, NotReadyException, IOException, InvalidParameterException, InterruptedException {			
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
		return awsProvider.applyTemplate(templateFile, projectAndEnv, parameters );
	}


}
