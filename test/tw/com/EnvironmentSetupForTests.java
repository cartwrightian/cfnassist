package tw.com;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class EnvironmentSetupForTests {
	private static final Logger logger = LoggerFactory.getLogger(EnvironmentSetupForTests.class);

	///////////////
	// User/Env specific constants, these will need to change for others running these tests!
	public static final String AVAILABILITY_ZONE = "eu-west-1c";
	public static final String AMI_FOR_INSTANCE = "ami-9c7ad8eb"; // eu amazon linux instance
	public static final String VPC_ID_FOR_ALT_ENV = "vpc-21e5ee43";
	public static final String BUCKET_NAME="cfnassists3testbucket";
	public static final String S3_PREFIX = "https://"+BUCKET_NAME+".s3-eu-west-1.amazonaws.com";
	private static final Regions AWS_REGION = Regions.EU_WEST_1;
	//
	///////////////
	
	public static final String PROJECT = "CfnAssist";
	public static final String ENV = "Test";
	public static String ALT_ENV = "AdditionalTest";
	
	public static final String TEMPORARY_STACK = "temporaryStack";

	public static final long DELETE_RETRY_MAX_TIMEOUT_MS = 5000; 
	public static final int DELETE_RETRY_LIMIT = (5*60000) / 5000; // Try for 5 minutes
	
	public static List<Subnet> getSubnetFors(AmazonEC2Client ec2Client, Vpc vpc) {
		DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest();
		Collection<Filter> filters = new HashSet<>();
		Filter vpcFilter = new Filter().withName("vpc-id").withValues(vpc.getVpcId());
		filters.add(vpcFilter);
		describeSubnetsRequest.setFilters(filters);
		
		DescribeSubnetsResult results = ec2Client.describeSubnets(describeSubnetsRequest );
		return results.getSubnets();
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
	

	public static AmazonRDSClient createRDSClient(AWSCredentialsProvider credentialsProvider) {
		AmazonRDSClient rdsClient = new AmazonRDSClient(credentialsProvider);
		rdsClient.setRegion(EnvironmentSetupForTests.getRegion());
		return rdsClient;
	}

	public static Vpc findAltVpc(VpcRepository repository) {
		return repository.getCopyOfVpc(VPC_ID_FOR_ALT_ENV);
	}

	public static Region getRegion() {
		return Region.getRegion(AWS_REGION);
	}

	public static void clearVpcTags(AmazonEC2Client directClient, Vpc vpc) throws InterruptedException {
		List<String> resources = new LinkedList<>();
		resources.add(vpc.getVpcId());
		List<Tag> existingTags = vpc.getTags();
		
		DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest(resources);
		deleteTagsRequest.setTags(existingTags);
		directClient.deleteTags(deleteTagsRequest);

        // cannot filter this by vpc id, api doesn't support it

//		DescribeTagsRequest describeTagsRequest = new DescribeTagsRequest();
//		Collection<Filter> filters = new LinkedList<>();
//		Filter vpcFilter = new Filter().withName("vpc").withValues(vpc.getVpcId());
//		filters.add(vpcFilter);
//		describeTagsRequest.setFilters(filters);
//		boolean deleted = false;
//		while(!deleted) {
//			DescribeTagsResult result = directClient.describeTags(describeTagsRequest);
//			deleted = result.getTags().isEmpty();
//			Thread.sleep(DELETE_RETRY_MAX_TIMEOUT_MS);
//			logger.debug("waiting for tags to clear on vpc :" + vpc.getVpcId());
//		}
	}

	public static ProjectAndEnv getMainProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);	
	}
	
	public static ProjectAndEnv getAltProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	}
	
	public static StackNameAndId createTemporarySimpleStack(AmazonCloudFormationClient cfnClient, String vpcId, String arn) throws IOException {
		CreateStackRequest createStackRequest = new CreateStackRequest();
		createStackRequest.setStackName(TEMPORARY_STACK);
		File file = new File(FilesForTesting.SIMPLE_STACK);
		createStackRequest.setTemplateBody(FileUtils.readFileToString(file , Charset.defaultCharset()));
		Collection<Parameter> parameters = new LinkedList<>();
		parameters.add(createParam("env", EnvironmentSetupForTests.ENV));
		parameters.add(createParam("vpc", vpcId));
		if (!arn.isEmpty()) {
			Collection<String> notificationARNs = new LinkedList<>();
			notificationARNs.add(arn);
			logger.debug("Adding arn subscription "+ arn);
			createStackRequest.setNotificationARNs(notificationARNs);
		}
		createStackRequest.setParameters(parameters);
		CreateStackResult result = cfnClient.createStack(createStackRequest);
		return new StackNameAndId(TEMPORARY_STACK, result.getStackId());
	}
	
	private static Parameter createParam(String key, String value) {
		Parameter p = new Parameter();
		p.setParameterKey(key);
		p.setParameterValue(value);
		return p;
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
			AWSCredentialsProvider credentialsProvider) {
		AmazonElasticLoadBalancingClient client = new AmazonElasticLoadBalancingClient(credentialsProvider);
		client.setRegion(getRegion());
		return client;
	}

	public static AmazonS3Client createS3Client(DefaultAWSCredentialsProviderChain credentialsProvider) {
		AmazonS3Client client = new AmazonS3Client(credentialsProvider);
		client.setRegion(getRegion());
		return client;
	}
	
	public static AmazonIdentityManagementClient createIamClient(DefaultAWSCredentialsProviderChain credentialsProvider) {
		AmazonIdentityManagementClient client = new AmazonIdentityManagementClient(credentialsProvider);
		client.setRegion(getRegion());
		return client;
	}
	
	public static Boolean isContainedIn(List<S3ObjectSummary> objectSummaries,
			String key) {
		for(S3ObjectSummary summary : objectSummaries) {
			if (summary.getBucketName().equals(EnvironmentSetupForTests.BUCKET_NAME) &&
					summary.getKey().equals(key)) {
				return true;
			}
		}
		return false;
	}

	public static final int FAILURE_STATUS = -1;


	public static List<S3ObjectSummary> getBucketObjects(AmazonS3Client s3Client) {
		ObjectListing requestResult = s3Client.listObjects(EnvironmentSetupForTests.BUCKET_NAME);
		return requestResult.getObjectSummaries();
	}

	public static List<com.amazonaws.services.cloudformation.model.Tag> createExpectedStackTags(String comment, Integer build) {
		List<com.amazonaws.services.cloudformation.model.Tag> expectedTags = new LinkedList<>();
		expectedTags.add(createCfnStackTAG("CFN_ASSIST_ENV", "Test"));
		expectedTags.add(createCfnStackTAG("CFN_ASSIST_PROJECT", "CfnAssist"));
		if (!comment.isEmpty()) {
			expectedTags.add(createCfnStackTAG("CFN_COMMENT", comment));
		}
		if (build>=0) {
			expectedTags.add(createCfnStackTAG("CFN_ASSIST_BUILD_NUMBER", build.toString()));
		}
		return expectedTags;
	}

	public static com.amazonaws.services.cloudformation.model.Tag createCfnStackTAG(String key, String value) {
		com.amazonaws.services.cloudformation.model.Tag tag = new com.amazonaws.services.cloudformation.model.Tag();
		tag.setKey(key);
		tag.setValue(value);
		return tag;
	}

	public static List<com.amazonaws.services.ec2.model.Tag> createExpectedEc2Tags(ProjectAndEnv projAndEnv, String comment) {
		List<com.amazonaws.services.ec2.model.Tag> tags = new LinkedList<>();
		tags.add(createEc2Tag("TagEnv",projAndEnv.getEnv()));
		tags.add(createEc2Tag("Name", "testSubnet"));
		// stack tags appear to be inherited
		tags.add(createEc2Tag("CFN_ASSIST_ENV", projAndEnv.getEnv()));
		tags.add(createEc2Tag("CFN_ASSIST_PROJECT", projAndEnv.getProject()));
		if (!comment.isEmpty()) {
			tags.add(createEc2Tag("CFN_COMMENT", comment));
		}
		return tags;
	}

	public static com.amazonaws.services.ec2.model.Tag createEc2Tag(String key, String value) {
		return new com.amazonaws.services.ec2.model.Tag().withKey(key).withValue(value);
	}

	public static String loadFile(String filename) throws IOException {
		return FileUtils.readFileToString(new File(filename), Charset.defaultCharset());
	}

	public static Instance createSimpleInstance(AmazonEC2Client ec2Client) {		
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest(EnvironmentSetupForTests.AMI_FOR_INSTANCE, 1, 1).
				withInstanceType(InstanceType.T1Micro);
		RunInstancesResult instancesResults = ec2Client.runInstances(runInstancesRequest);
		List<Instance> instances = instancesResults.getReservation().getInstances();	
		return instances.get(0);
	}

}
