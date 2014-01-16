package tw.com;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteTagsRequest;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

public class EnvironmentSetupForTests {

	public static final String ENV = "Test";
	public static final String PROJECT = "CfnAssist";
	public static final String FOLDER_PATH = "src/cfnScripts/orderedScripts";
	public static final String SUBNET_WITH_PARAM_FILENAME = "src/cfnScripts/subnetWithParam.json";
	public static final String SUBNET_FILENAME = "src/cfnScripts/subnet.json";
	public static String ALT_ENV = "AdditionalTest";
	
	public static List<Subnet> getSubnetFors(AmazonEC2Client ec2Client, Vpc vpc) {
		DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest();
		Collection<Filter> filters = new HashSet<Filter>();
		Filter vpcFilter = createVPCFilter(vpc);
		filters.add(vpcFilter);
		describeSubnetsRequest.setFilters(filters);
		
		DescribeSubnetsResult results = ec2Client.describeSubnets(describeSubnetsRequest );
		return results.getSubnets();
	}

	private static Filter createVPCFilter(Vpc vpc) {
		Filter vpcFilter = new Filter().withName("vpc-id").withValues(vpc.getVpcId());
		return vpcFilter;
	}

	public static AmazonEC2Client createEC2Client(DefaultAWSCredentialsProviderChain credentialsProvider) {
		AmazonEC2Client ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(EnvironmentSetupForTests.getRegion());
		return ec2Client;
	}
	
	public static Vpc createVpc(AmazonEC2Client client) {
		CreateVpcRequest createVpcRequest = new CreateVpcRequest();
		createVpcRequest.setCidrBlock("10.0.10.0/24");
		CreateVpcResult result = client.createVpc(createVpcRequest );
		return result.getVpc();
	}
	
	public static void deleteVpc(AmazonEC2Client client, Vpc vpc) {
		DeleteVpcRequest deleteVpcRequest = new DeleteVpcRequest();
		deleteVpcRequest.setVpcId(vpc.getVpcId());
		client.deleteVpc(deleteVpcRequest);
	}

	public static Region getRegion() {
		return Region.getRegion(Regions.EU_WEST_1);
	}

	public static AmazonCloudFormationClient createCFNClient(AWSCredentialsProvider credentialsProvider) {
		AmazonCloudFormationClient cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(EnvironmentSetupForTests.getRegion());
		return cfnClient;
	}

	public static void deleteStack(AmazonCloudFormationClient client,String stackName) {
		DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
		deleteStackRequest.setStackName(stackName);
		client.deleteStack(deleteStackRequest );	
	}

	public static void clearVpcTags(AmazonEC2Client directClient, Vpc vpc) {
		List<String> resources = new LinkedList<String>();	
		resources.add(vpc.getVpcId());
		DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest(resources);
		deleteTagsRequest.setTags(vpc.getTags());
		directClient.deleteTags(deleteTagsRequest );
	}

	public static ProjectAndEnv getAltProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	}

}
