package tw.com;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.Parameter;
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

	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
	}
	
	@Before
	public void beforeEachTestIsRun() {				
		VpcRepository vpcRepository = new VpcRepository(directClient);
		
		templateFile = new File("src/cfnScripts/subnetWithCIDRParam.json");
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository );
		awsProvider = new AwsFacade(monitor , cfnClient, directClient, cfnRepository, vpcRepository);
		
		mainTestVPC = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		otherVPC = vpcRepository.getCopyOfVpc(altProjectAndEnv);
	}
	
	@Test
	public void shouldFindResourceFromCorrectVPC() throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {	
		String vpcIdA = mainTestVPC.getVpcId();
		String vpcIdB = otherVPC.getVpcId();
		String cidrA = "10.0.10.0/24";
		String cidrB = "10.0.11.0/24";

		CfnRepository cfnRepository = new CfnRepository(cfnClient);
	
		//create two subnets with same logical id's but different VPCs		
		StackId stackA = invokeSubnetCreation(cidrA, mainProjectAndEnv);	
		StackId stackB = invokeSubnetCreation(cidrB,  altProjectAndEnv);
		//awsProvider.waitForCreateFinished(stackA);
		//awsProvider.waitForCreateFinished(stackB);
		
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
			InvalidParameterException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		Parameter cidrParameter = new Parameter();
		cidrParameter.setParameterKey("cidr");
		cidrParameter.setParameterValue(cidr);
		parameters.add(cidrParameter);
		return awsProvider.applyTemplate(templateFile, projectAndEnv, parameters );
	}


}
