package tw.com;

import static org.junit.Assert.assertEquals;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

public class TestBuildInParameterInjection {
	private AwsProvider awsProvider;
	private String env = EnvironmentSetupForTests.ENV;
	private String project = EnvironmentSetupForTests.PROJECT;
	private ProjectAndEnv mainProjectAndEnv;

	private AmazonCloudFormationClient cfnClient;
	private AmazonEC2Client ec2Client;
	private Vpc vpc;

	@Before
	public void beforeTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		awsProvider = new AwsFacade(credentialsProvider, EnvironmentSetupForTests.getRegion());
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider); 
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		mainProjectAndEnv = new ProjectAndEnv(project, env);
		vpc = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		
		tidyStacksIfNeeded();
	}
	
	@After
	public void afterEachTestHasRun() {
		tidyStacksIfNeeded();
	}

	private void tidyStacksIfNeeded() {
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestsubnet");
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestsubnetWithParam");
		EnvironmentSetupForTests.deleteStackIfPresent(cfnClient, "CfnAssistTestsubnetWithBuild");
	}
	
	@Test
	public void canBuildAndDeleteSimpleStackWithCorrectTags() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, InvalidParameterException, StackCreateFailed {	
		File templateFile = new File(EnvironmentSetupForTests.SUBNET_FILENAME);
		String stackName = awsProvider.applyTemplate(templateFile, mainProjectAndEnv);
		
		validateCreateAndDeleteWorks(stackName, createExpectedStackTags(), createExpectedTags());
	}

	@Test
	public void canPassInSimpleParameter() throws FileNotFoundException, IOException, InvalidParameterException, 
			WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		File templateFile = new File(EnvironmentSetupForTests.SUBNET_WITH_PARAM_FILENAME);
		
		Collection<Parameter> params = new LinkedList<Parameter>();
		params.add(new Parameter().withParameterKey("zoneA").withParameterValue("eu-west-1a"));
		String stackName = awsProvider.applyTemplate(templateFile, mainProjectAndEnv, params);
				
		validateCreateAndDeleteWorks(stackName, createExpectedStackTags(), createExpectedTags());
	}

	@Test
	public void createsAndDeleteSubnetFromTemplateWithBuildNumber() throws FileNotFoundException, IOException, CfnAssistException, InvalidParameterException, InterruptedException {
		String buildNumber = "42";
		
		mainProjectAndEnv.addBuildNumber(buildNumber);
		String stackName = awsProvider.applyTemplate(new File(EnvironmentSetupForTests.SUBNET_FILENAME_WITH_BUILD), mainProjectAndEnv);	
		
		String status = awsProvider.waitForCreateFinished(stackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
			
		validateCreateAndDeleteWorks(stackName, 
				createCfnExpectedTagListWithBuild(buildNumber), 
				createExpectedTagsWithBuild(buildNumber));
		
		EnvironmentSetupForTests.validatedDelete(stackName, awsProvider);
	}

	private void validateCreateAndDeleteWorks(String stackName, List<Tag> expectedStackTags, 
			List<com.amazonaws.services.ec2.model.Tag> expectedEc2Tags)
			throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		String status = awsProvider.waitForCreateFinished(stackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		List<Stack> stacks = getStack(stackName);
	    
		List<Tag> stackTags = stacks.get(0).getTags(); 
	    assertEquals(expectedStackTags.size(), stackTags.size());
	    assert(stackTags.containsAll(expectedStackTags));
	    
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		assertEquals(1, subnets.size());
		Subnet subnet = subnets.get(0);
		List<com.amazonaws.services.ec2.model.Tag> subnetTags = subnet.getTags();
		assertEquals(expectedEc2Tags.size(), subnetTags.size()-EnvironmentSetupForTests.NUMBER_AWS_TAGS);
		assert(subnetTags.containsAll(expectedEc2Tags));
	    
		awsProvider.deleteStack(stackName);
		
		status = awsProvider.waitForDeleteFinished(stackName);
		assertEquals(StackStatus.DELETE_COMPLETE.toString(), status);
	}

	private List<Stack> getStack(String stackName) {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult stackResults = cfnClient.describeStacks(describeStacksRequest);
		
		List<Stack> stacks = stackResults.getStacks();
		assertEquals(1,stacks.size());
		return stacks;
	}
	
	private List<Tag> createExpectedStackTags() {
		List<Tag> expectedTags = new LinkedList<Tag>();
		expectedTags.add(createCfnTag("CFN_ASSIST_ENV", "Test"));
		expectedTags.add(createCfnTag("CFN_ASSIST_PROJECT", "CfnAssist"));
		return expectedTags;
	}
	
	private List<com.amazonaws.services.ec2.model.Tag> createExpectedTags() {
		List<com.amazonaws.services.ec2.model.Tag> tags = new LinkedList<com.amazonaws.services.ec2.model.Tag>();
		tags.add(createEc2Tag("TagEnv",mainProjectAndEnv.getEnv()));
		tags.add(createEc2Tag("Name", "testSubnet"));
		// stack tags appear to be inherited
		tags.add(createEc2Tag("CFN_ASSIST_ENV", "Test"));
		tags.add(createEc2Tag("CFN_ASSIST_PROJECT", "CfnAssist"));
		return tags;
	}
	
	private com.amazonaws.services.ec2.model.Tag createEc2Tag(String key,
			String value) {
		return new com.amazonaws.services.ec2.model.Tag().withKey(key).withValue(value);
	}

	private List<com.amazonaws.services.ec2.model.Tag> createExpectedTagsWithBuild(String buildId) {
		List<com.amazonaws.services.ec2.model.Tag> expectedTags = createExpectedTags();
		expectedTags.add(createEc2Tag("TagBuild",buildId));
		// stack tags appear to be inherited
		expectedTags.add(createEc2Tag("CFN_ASSIST_BUILD", buildId));
		return expectedTags;
	}

	private List<Tag> createCfnExpectedTagListWithBuild(String value) {
		List<Tag> tags = createExpectedStackTags();
		tags.add(createCfnTag("CFN_ASSIST_BUILD", value));
		return tags;
	}

	private Tag createCfnTag(String key, String value) {
		Tag tag = new Tag();
		tag.setKey(key);
		tag.setValue(value);
		return tag;
	}

}
