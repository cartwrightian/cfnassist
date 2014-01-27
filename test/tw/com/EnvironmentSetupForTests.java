package tw.com;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.FileUtils;

import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackStatus;
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
	public static final String PROJECT = "CfnAssist";
	public static final String ENV = "Test";
	public static String ALT_ENV = "AdditionalTest";
	
	public static final String TEMPORARY_STACK = "temporaryStack";

	public static final String FOLDER_PATH = "src/cfnScripts/orderedScripts";
	public static final String SUBNET_WITH_PARAM_FILENAME = "src/cfnScripts/subnetWithParam.json";
	public static final String SUBNET_FILENAME = "src/cfnScripts/subnet.json";
	public static final String SUBNET_FILENAME_WITH_BUILD = "src/cfnScripts/subnetWithBuild.json";
	public static final int NUMBER_AWS_TAGS = 3; // number of tags that cfn itself adds to created resources
	private static final int DELETE_RETRY_LIMIT = 10;
	private static final long DELETE_RETRY_INTERVAL = 3000;
	
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

	public static boolean deleteStack(AmazonCloudFormationClient client,String stackName, boolean blocking)  {
		DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
		deleteStackRequest.setStackName(stackName);
		client.deleteStack(deleteStackRequest);	
		if (!blocking) {
			return true;
		}
		// blocking, wait for delete
		try {
			return waitForStackDelete(client, stackName);
		}
		catch(AmazonServiceException exception) {
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}

	private static boolean waitForStackDelete(AmazonCloudFormationClient client,
			String stackName) throws InterruptedException  {
		DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest();
		describeStackEventsRequest.setStackName(stackName);
		
		int count = 0;
		while (count<DELETE_RETRY_LIMIT) {
			DescribeStackEventsResult result = client.describeStackEvents(describeStackEventsRequest);
			List<StackEvent> events = result.getStackEvents();
			for(StackEvent event : events) {
				if (event.getResourceStatus()==StackStatus.DELETE_COMPLETE.toString()) {
					return true;
				}
				if (event.getResourceStatus()==StackStatus.DELETE_FAILED.toString()) {
					return false;
				}
			}
			count++;
			Thread.sleep(DELETE_RETRY_INTERVAL);
		}
		return false;
	}

	public static void clearVpcTags(AmazonEC2Client directClient, Vpc vpc) {
		List<String> resources = new LinkedList<String>();	
		resources.add(vpc.getVpcId());
		DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest(resources);
		deleteTagsRequest.setTags(vpc.getTags());
		directClient.deleteTags(deleteTagsRequest );
	}


	public static ProjectAndEnv getMainProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);	
	}
	
	public static ProjectAndEnv getAltProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	}
	
	public static void deleteStackIfPresent(AmazonCloudFormationClient cfnClient, String stackName) {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult result = cfnClient.describeStacks();
		if (result.getStacks().size()==0) {
			return;
		}
		
		DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
		deleteStackRequest.setStackName(stackName);
		cfnClient.deleteStack(deleteStackRequest);
		// TODO wait for delete to complete
	}

	public static void createTemporaryStack(AmazonCloudFormationClient cfnClient, String vpcId) throws IOException {
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setStackName(TEMPORARY_STACK);
		File file = new File(EnvironmentSetupForTests.SUBNET_FILENAME);
		createStackRequest.setTemplateBody(FileUtils.readFileToString(file , Charset.defaultCharset()));
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		parameters.add(createParam("env", EnvironmentSetupForTests.ENV));
		parameters.add(createParam("vpc", vpcId));
		createStackRequest.setParameters(parameters);
		cfnClient.createStack(createStackRequest);
	}
	
	private static Parameter createParam(String key, String value) {
		Parameter p = new Parameter();
		p.setParameterKey(key);
		p.setParameterValue(value);
		return p;
	}

	public static void validatedDelete(String stackName, AwsProvider provider)
			throws WrongNumberOfStacksException, InterruptedException {
		provider.deleteStack(stackName);
		String status = provider.waitForDeleteFinished(stackName);
		assertEquals(StackStatus.DELETE_COMPLETE.toString(), status);
	}

}
