package tw.com;


import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.CreateStackResponse;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.User;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    public static final String S3_PREFIX = "https://s3.amazonaws.com/"+BUCKET_NAME;

    // TODO THIS Instance ID is *very* *very* out of date
    private static final String AMI_FOR_INSTANCE = "ami-9c7ad8eb"; // eu amazon linux instance
	public static final String VPC_ID_FOR_ALT_ENV = "vpc-21e5ee43";
    //
	///////////////
	
	public static final String PROJECT = "CfnAssist";
	public static final String ENV = "Test";
	public static String ALT_ENV = "AdditionalTest";
	
	public static final String TEMPORARY_STACK = "temporaryStack";

	static final long DELETE_RETRY_MAX_TIMEOUT_MS = 5000;
	static final int DELETE_RETRY_LIMIT = (5*60000) / 5000; // Try for 5 minutes
	
	public static List<Subnet> getSubnetFors(Ec2Client ec2Client, Vpc vpc) {
		Filter vpcFilter = Filter.builder().name("vpc-id").values(vpc.vpcId()).build();

		DescribeSubnetsRequest describeSubnetsRequest = DescribeSubnetsRequest.
				builder().filters(vpcFilter).build();


		DescribeSubnetsResponse results = ec2Client.describeSubnets(describeSubnetsRequest);
		return results.subnets();
	}

	public static Ec2Client createEC2Client() {
	    return Ec2Client.create();
	}
		
	public static CloudFormationClient createCFNClient() {
        return CloudFormationClient.create();
	}

	public static RdsClient createRDSClient() {
        return RdsClient.create();
	}

	public static Vpc findAltVpc(VpcRepository repository) {
		return repository.getCopyOfVpc(VPC_ID_FOR_ALT_ENV);
	}

	public static void clearVpcTags(Ec2Client directClient, Vpc vpc) {
		List<String> resources = new LinkedList<>();
		resources.add(vpc.vpcId());
		List<Tag> existingTags = vpc.tags();
		
		DeleteTagsRequest deleteTagsRequest = DeleteTagsRequest.builder().
				resources(resources).tags(existingTags).build();
		directClient.deleteTags(deleteTagsRequest);
	}

	public static ProjectAndEnv getMainProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);	
	}
	
	public static ProjectAndEnv getAltProjectAndEnv() {
		return new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	}
	
	public static StackNameAndId createTemporarySimpleStack(CloudFormationClient cfnClient, String vpcId, String arn) throws IOException {
		File file = new File(FilesForTesting.SIMPLE_STACK);

		CreateStackRequest.Builder createStackRequestBuilder = CreateStackRequest.builder().
				stackName(TEMPORARY_STACK).templateBody(FileUtils.readFileToString(file , Charset.defaultCharset()));

		Collection<Parameter> parameters = new LinkedList<>();
		parameters.add(createParam("env", EnvironmentSetupForTests.ENV));
		parameters.add(createParam("vpc", vpcId));
		if (!arn.isEmpty()) {
			Collection<String> notificationARNs = new LinkedList<>();
			notificationARNs.add(arn);
			logger.debug("Adding arn subscription "+ arn);
			createStackRequestBuilder.notificationARNs(notificationARNs);
		}
		createStackRequestBuilder.parameters(parameters);
		CreateStackResponse result = cfnClient.createStack(createStackRequestBuilder.build());
		return new StackNameAndId(TEMPORARY_STACK, result.stackId());
	}
	
	private static Parameter createParam(String key, String value) {
		return Parameter.builder().parameterKey(key).parameterValue(value).build();
	}

	public static SnsClient createSNSClient() {
        return SnsClient.create();
	}
	
	public static SqsClient createSQSClient() {
        return SqsClient.create();
	}


	public static CloudWatchLogsClient createAWSLogsClient() {
		return CloudWatchLogsClient.create();
	}

	public static ElasticLoadBalancingClient createELBClient() {
        return ElasticLoadBalancingClient.create();
	}

	public static S3Client createS3Client() {
        return S3Client.create();
	}
	
	public static IamClient createIamClient() {
		return IamClient.builder().
				credentialsProvider(DefaultCredentialsProvider.create())
				.region(Region.AWS_GLOBAL).
				build();
    }
	
	public static Boolean isContainedIn(List<S3Object> objectSummaries, String key) {
		for(S3Object summary : objectSummaries) {
			if (summary.key().equals(key)) {
				return true;
			}
		}
		return false;
	}

	public static final int FAILURE_STATUS = -1;


	public static List<S3Object> getBucketObjects(S3Client s3Client) {
		ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(EnvironmentSetupForTests.BUCKET_NAME).build();
		ListObjectsV2Response requestResult = s3Client.listObjectsV2(request);
		return requestResult.contents();
	}

	public static List<software.amazon.awssdk.services.cloudformation.model.Tag> createExpectedStackTags(String comment, Integer build, String project) {
		List<software.amazon.awssdk.services.cloudformation.model.Tag> expectedTags = new LinkedList<>();
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

	public static software.amazon.awssdk.services.cloudformation.model.Tag createCfnStackTAG(String key, String value) {
		software.amazon.awssdk.services.cloudformation.model.Tag tag
				= software.amazon.awssdk.services.cloudformation.model.Tag.builder().key(key).value(value).build();
		return tag;
	}

	public static List<Tag> createExpectedEc2Tags(ProjectAndEnv projAndEnv, String comment) {
		List<Tag> tags = new LinkedList<>();
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

	public static Tag createEc2Tag(String key, String value) {
		return Tag.builder().key(key).value(value).build();
	}

	public static String loadFile(String filename) throws IOException {
		return FileUtils.readFileToString(new File(filename), Charset.defaultCharset());
	}

	public static Instance createSimpleInstance(Ec2Client ec2Client) {
		RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder().
				instanceType(InstanceType.T1_MICRO).imageId(AMI_FOR_INSTANCE).
				minCount(1).maxCount(1).build();

		RunInstancesResponse instancesResults = ec2Client.runInstances(runInstancesRequest);
		List<Instance> instances = instancesResults.instances();
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

	public static TemplateParameter createTemplate(String key) {
		return TemplateParameter.builder().parameterKey(key).build();
	}

	public static TemplateParameter createTemplateWithDefault(String key, String def) {
		return TemplateParameter.builder().parameterKey(key).defaultValue(def).build();
	}

	public static User createUser() {
		return User.builder().path("path").userName("userName").userId("userId").arn("arn").createDate(Instant.now()).build();
	}

	public static Long asMillis(ZonedDateTime zonedDateTime) {
		return Instant.from(zonedDateTime).toEpochMilli();
	}
}
