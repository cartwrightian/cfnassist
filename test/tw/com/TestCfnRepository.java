package tw.com;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.CreateSubnetResult;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.Vpc;

public class TestCfnRepository {

	private static final String CIDR_BLOCK_FOR_SUBNETS = "10.0.11.0/24";
	private AmazonCloudFormationClient cfnClient;
	private Vpc mainTestVPC;
	private Vpc otherTestVPC;
	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private AmazonEC2Client directClient;

	@Before
	public void beforeEachTestIsRun() {		
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(TestAwsFacade.getRegion());
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		
		VpcRepository vpcRepository = new VpcRepository(credentialsProvider, TestAwsFacade.getRegion());
		mainTestVPC = vpcRepository.findVpcForEnv(TestAwsFacade.PROJECT, TestAwsFacade.ENV);	
		otherTestVPC = vpcRepository.findVpcForEnv("CfnAssist", EnvironmentSetupForTests.ALT_ENV);
	}
	
	@Test
	public void shouldFindResourceFromCorrectVPC() {	
		String vpcIdA = mainTestVPC.getVpcId();
		String vpcIdB = otherTestVPC.getVpcId();
		CfnRepository cfnRepository = new CfnRepository(cfnClient);
		
		String logicalSubnetId = "cfnAssistDuplicatedSubnetId";
		String expectedPhysicalId = createSubnet(vpcIdA, logicalSubnetId, CIDR_BLOCK_FOR_SUBNETS);
		String otherSubnet = createSubnet(vpcIdB, logicalSubnetId, CIDR_BLOCK_FOR_SUBNETS);
		
		String physicalId = cfnRepository.findPhysicalIdByLogicalId(vpcIdA, logicalSubnetId);
		
		deleteSubnet(expectedPhysicalId);
		deleteSubnet(otherSubnet);
		
		assertEquals(expectedPhysicalId, physicalId);
	}

	private void deleteSubnet(String physicalId) {
		DeleteSubnetRequest request = new DeleteSubnetRequest();
		request.setSubnetId(physicalId);
		directClient.deleteSubnet(request);	
	}

	private String createSubnet(String vpcId, String logicalSubnetId, String cidrBlock) {
		
		CreateSubnetRequest subnetCreationReuqest = new CreateSubnetRequest(vpcId, cidrBlock);
		CreateSubnetResult response = directClient.createSubnet(subnetCreationReuqest );
		return response.getSubnet().getSubnetId();
	}

}
