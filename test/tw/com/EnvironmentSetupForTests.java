package tw.com;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
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
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

public class EnvironmentSetupForTests {
	private static final Logger logger = LoggerFactory.getLogger(EnvironmentSetupForTests.class);

	// User/Env specific constants, these will need to change for others running these tests!
	static final String ARN_FOR_TESTING = "arn:aws:sns:eu-west-1:619378453009:cfn_assist";
	static final String VPC_ID_FOR_ALT_ENV = "vpc-21e5ee43";
	
	// TODO should we pick this up from the environment?
	private static final Regions AWS_REGION = Regions.EU_WEST_1;
	
	public static final String PROJECT = "CfnAssist";
	public static final String ENV = "Test";
	public static String ALT_ENV = "AdditionalTest";
	
	public static final String TEMPORARY_STACK = "temporaryStack";

	public static final String FOLDER_PATH = "src/cfnScripts/orderedScripts";
	
	public static final String SUBNET_STACK_FILE = "src/cfnScripts/subnet.json";
	public static final String SIMPLE_STACK_FILE = "src/cfnScripts/simpleStack.json";
	public static final String ACL_FILENAME = "src/cfnScripts/acl.json";
	public static final String SUBNET_WITH_PARAM_FILENAME = "src/cfnScripts/subnetWithParam.json";
	public static final String SUBNET_FILENAME_WITH_BUILD = "src/cfnScripts/subnetWithBuild.json";
	public static final String ELB_FILENAME = "src/cfnScripts/elb.json";
	public static final String CAUSEROLLBACK = "src/cfnScripts/causesRollBack.json";
	
	public static final int NUMBER_AWS_TAGS = 3; // number of tags that aws cfn itself adds to created resources
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

	public static AmazonEC2Client createEC2Client(AWSCredentialsProvider credentialsProvider) {
		AmazonEC2Client ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(EnvironmentSetupForTests.getRegion());
		return ec2Client;
	}
		
	public static AmazonCloudFormationClient createCFNClient(AWSCredentialsProvider credentialsProvider) {
		AmazonCloudFormationClient cfnClient = new AmazonCloudFormationClient(credentialsProvider);
		cfnClient.setRegion(EnvironmentSetupForTests.getRegion());
		return cfnClient;
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

	public static Vpc findAltVpc(VpcRepository repository) {
		return repository.getCopyOfVpc(VPC_ID_FOR_ALT_ENV);
	}

	public static Region getRegion() {
		return Region.getRegion(AWS_REGION);
	}


	public static boolean deleteStack(AmazonCloudFormationClient client, String stackName, boolean blocking)  {
		logger.debug("Request deletion of stack: " + stackName);
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
			logger.error(exception.toString());
			return false;
		} catch (InterruptedException e) {
			logger.error(e.toString());
			return false;
		}
	}

	// TODO use monitor here instead?
	private static boolean waitForStackDelete(AmazonCloudFormationClient client,
			String stackName) throws InterruptedException  {
		logger.debug("Waiting for deletion to complete for stack: " + stackName);
		DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest();
		describeStackEventsRequest.setStackName(stackName);
		
		int count = 0;
		while (count<DELETE_RETRY_LIMIT) {
			logger.debug("Check stack events");
			DescribeStackEventsResult result = client.describeStackEvents(describeStackEventsRequest);
			List<StackEvent> events = result.getStackEvents();
			for(StackEvent event : events) {
				if (event.getResourceStatus()==StackStatus.DELETE_COMPLETE.toString()) {
					logger.debug("Delete complete for stack: " + stackName);
					return true;
				}
				if (event.getResourceStatus()==StackStatus.DELETE_FAILED.toString()) {
					logger.error("Delete failed for stack: " + stackName);
					return false;
				}
			}
			count++;
			Thread.sleep(DELETE_RETRY_INTERVAL);
		}
		logger.error("Delete timed out for stack: " + stackName);
		return false;
	}

	public static void clearVpcTags(AmazonEC2Client directClient, Vpc vpc) throws InterruptedException {
		List<String> resources = new LinkedList<String>();	
		resources.add(vpc.getVpcId());
		List<Tag> existingTags = vpc.getTags();
		
		DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest(resources);
		deleteTagsRequest.setTags(existingTags);
		directClient.deleteTags(deleteTagsRequest);
		
		DescribeTagsRequest describeTagsRequest = new DescribeTagsRequest();
		Collection<Filter> filters = new LinkedList<Filter>();
		filters.add(createVPCFilter(vpc));
		describeTagsRequest.setFilters(filters);
		boolean notDeleted = false;
		while(notDeleted) {
			DescribeTagsResult result = directClient.describeTags(describeTagsRequest);
			notDeleted = (result.getTags().size()!=0);
			Thread.sleep(DELETE_RETRY_INTERVAL);
			logger.debug("waiting for tags to clear on vpc :" + vpc.getVpcId());
		}
	}


	public static ProjectAndEnv getMainProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);	
	}
	
	public static ProjectAndEnv getAltProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	}
	
	public static void deleteStackIfPresent(AmazonCloudFormationClient cfnClient, String stackName) {
		logger.info("deleteStackIfPresent " + stackName);
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult result = cfnClient.describeStacks();
		if (result.getStacks().size()==0) {
			return;
		}
		
		deleteStack(cfnClient, stackName, true);
	}

	public static StackId createTemporarySimpleStack(AmazonCloudFormationClient cfnClient, String vpcId, String arn) throws IOException {
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setStackName(TEMPORARY_STACK);
		File file = new File(EnvironmentSetupForTests.SIMPLE_STACK_FILE);
		createStackRequest.setTemplateBody(FileUtils.readFileToString(file , Charset.defaultCharset()));
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		parameters.add(createParam("env", EnvironmentSetupForTests.ENV));
		parameters.add(createParam("vpc", vpcId));
		if (!arn.isEmpty()) {
			Collection<String> notificationARNs = new LinkedList<String>();
			notificationARNs.add(arn);
			logger.debug("Adding arn subscription "+ arn);
			createStackRequest.setNotificationARNs(notificationARNs);
		}
		createStackRequest.setParameters(parameters);
		CreateStackResult result = cfnClient.createStack(createStackRequest);
		return new StackId(TEMPORARY_STACK, result.getStackId());
	}
	
	private static Parameter createParam(String key, String value) {
		Parameter p = new Parameter();
		p.setParameterKey(key);
		p.setParameterValue(value);
		return p;
	}

	public static void validatedDelete(StackId stackId, AwsProvider provider)
			throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus {
		provider.deleteStack(stackId);
	}

	public static AmazonSNSClient createSNSClient(
			DefaultAWSCredentialsProviderChain credentialsProvider) {
		AmazonSNSClient amazonSNSClient = new AmazonSNSClient(credentialsProvider);
		amazonSNSClient.setRegion(getRegion());
		return amazonSNSClient;
	}
	
	public static AmazonSQSClient createSQSClient(
			DefaultAWSCredentialsProviderChain credentialsProvider) {
		AmazonSQSClient sqsClient = new AmazonSQSClient(credentialsProvider);
		sqsClient.setRegion(getRegion());
		return sqsClient;
	}

	public static AmazonElasticLoadBalancingClient createELBClient(
			DefaultAWSCredentialsProviderChain credentialsProvider) {
		AmazonElasticLoadBalancingClient client = new AmazonElasticLoadBalancingClient(credentialsProvider);
		client.setRegion(getRegion());
		return client;
	}

}
