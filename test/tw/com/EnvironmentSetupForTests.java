package tw.com;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static java.nio.file.attribute.PosixFilePermission.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvironmentSetupForTests {
	private static final Logger logger = LoggerFactory.getLogger(EnvironmentSetupForTests.class);

	///////////////
	// User/Env specific constants, these will need to change for others running these tests!
    public static final String BUCKET_NAME="cfnassists3testbucket";
    public static final String AVAILABILITY_ZONE = "eu-west-1c";
    public static final String S3_PREFIX = "https://"+BUCKET_NAME+".s3.eu-west-1.amazonaws.com";

    public static final String AMI_FOR_INSTANCE = "ami-9c7ad8eb"; // eu amazon linux instance
	public static final String VPC_ID_FOR_ALT_ENV = "vpc-21e5ee43";
    //
	///////////////
	
	public static final String PROJECT = "CfnAssist";
	public static final String ENV = "Test";
	public static String ALT_ENV = "AdditionalTest";
	
	public static final String TEMPORARY_STACK = "temporaryStack";

	public static final long DELETE_RETRY_MAX_TIMEOUT_MS = 5000; 
	public static final int DELETE_RETRY_LIMIT = (5*60000) / 5000; // Try for 5 minutes
	
	public static List<Subnet> getSubnetFors(AmazonEC2 ec2Client, Vpc vpc) {
		DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest();
		Collection<Filter> filters = new HashSet<>();
		Filter vpcFilter = new Filter().withName("vpc-id").withValues(vpc.getVpcId());
		filters.add(vpcFilter);
		describeSubnetsRequest.setFilters(filters);
		
		DescribeSubnetsResult results = ec2Client.describeSubnets(describeSubnetsRequest );
		return results.getSubnets();
	}

	public static AmazonEC2 createEC2Client() {
	    return AmazonEC2ClientBuilder.defaultClient();
	}
		
	public static AmazonCloudFormation createCFNClient() {
        return AmazonCloudFormationClientBuilder.defaultClient();
	}

	public static AmazonRDS createRDSClient() {
        return AmazonRDSClientBuilder.defaultClient();
	}

	public static Vpc findAltVpc(VpcRepository repository) {
		return repository.getCopyOfVpc(VPC_ID_FOR_ALT_ENV);
	}

	public static void clearVpcTags(AmazonEC2 directClient, Vpc vpc) throws InterruptedException {
		List<String> resources = new LinkedList<>();
		resources.add(vpc.getVpcId());
		List<Tag> existingTags = vpc.getTags();
		
		DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest(resources);
		deleteTagsRequest.setTags(existingTags);
		directClient.deleteTags(deleteTagsRequest);
	}

	public static ProjectAndEnv getMainProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);	
	}
	
	public static ProjectAndEnv getAltProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	}
	
	public static StackNameAndId createTemporarySimpleStack(AmazonCloudFormation cfnClient, String vpcId, String arn) throws IOException {
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

	public static AmazonSNS createSNSClient() {
        return AmazonSNSClientBuilder.defaultClient();
	}
	
	public static AmazonSQS createSQSClient() {
        return AmazonSQSClientBuilder.defaultClient();
	}


	public static AWSLogs createAWSLogsClient() {
		return AWSLogsClientBuilder.defaultClient();
	}

	public static AmazonElasticLoadBalancing createELBClient() {
        return AmazonElasticLoadBalancingClientBuilder.defaultClient();

	}

	public static AmazonS3 createS3Client() {
        return AmazonS3ClientBuilder.defaultClient();
	}
	
	public static AmazonIdentityManagement createIamClient(DefaultAWSCredentialsProviderChain credentialsProvider) {
        return AmazonIdentityManagementClientBuilder.defaultClient();
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


	public static List<S3ObjectSummary> getBucketObjects(AmazonS3 s3Client) {
		ObjectListing requestResult = s3Client.listObjects(EnvironmentSetupForTests.BUCKET_NAME);
		return requestResult.getObjectSummaries();
	}

	public static List<com.amazonaws.services.cloudformation.model.Tag> createExpectedStackTags(String comment, Integer build, String project) {
		List<com.amazonaws.services.cloudformation.model.Tag> expectedTags = new LinkedList<>();
		expectedTags.add(createCfnStackTAG("CFN_ASSIST_ENV", "Test"));
		expectedTags.add(createCfnStackTAG("CFN_ASSIST_PROJECT", project));
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

	public static Instance createSimpleInstance(AmazonEC2 ec2Client) {
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest(EnvironmentSetupForTests.AMI_FOR_INSTANCE, 1, 1).
				withInstanceType(InstanceType.T1Micro);
		RunInstancesResult instancesResults = ec2Client.runInstances(runInstancesRequest);
		List<Instance> instances = instancesResults.getReservation().getInstances();	
		return instances.get(0);
	}

	public static void checkKeyPairFilePermissions(Set<PosixFilePermission> permissions) {
		assertTrue(permissions.contains(OWNER_READ));
		assertTrue(permissions.contains(OWNER_WRITE));
		// no exec
		assertFalse(permissions.contains(OWNER_EXECUTE));
		// no group
		assertFalse(permissions.contains(GROUP_EXECUTE));
		assertFalse(permissions.contains(GROUP_READ));
		assertFalse(permissions.contains(GROUP_WRITE));
		// no other
		assertFalse(permissions.contains(OTHERS_EXECUTE));
		assertFalse(permissions.contains(OTHERS_READ));
		assertFalse(permissions.contains(OTHERS_WRITE));
	}

}
