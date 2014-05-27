package tw.com.integration;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.AwsProvider;
import tw.com.CfnRepository;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.PollingStackMonitor;
import tw.com.ProjectAndEnv;
import tw.com.StackId;
import tw.com.VpcRepository;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

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

public class TestBuiltInParameterInjectionAndTagging {
	private AwsProvider awsProvider;
	private String env = EnvironmentSetupForTests.ENV;
	private String project = EnvironmentSetupForTests.PROJECT;
	private ProjectAndEnv mainProjectAndEnv;

	private static AmazonCloudFormationClient cfnClient;
	private static AmazonEC2Client ec2Client;
	private Vpc vpc;
	private PollingStackMonitor monitor;
	private DeletesStacks deletesStacks;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
	}
	
	private String testName;
	@Rule public TestName test = new TestName();
	

	@Before
	public void beforeTestsRun() {
		testName = test.getMethodName();
		CfnRepository cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		monitor = new PollingStackMonitor(cfnRepository);
		VpcRepository vpcRepository = new VpcRepository(ec2Client);
		
		awsProvider = new AwsFacade(monitor, cfnClient, cfnRepository, vpcRepository);
		awsProvider.setCommentTag(testName);

		mainProjectAndEnv = new ProjectAndEnv(project, env);
		vpc = vpcRepository.getCopyOfVpc(mainProjectAndEnv);
		
		deletesStacks = new DeletesStacks(cfnClient);
		deletesStacks.ifPresent("CfnAssistTestsubnet")
		.ifPresent("CfnAssistTestsubnetWithParam")
		.ifPresent("CfnAssistTestsubnetWithBuild")
		.ifPresent("CfnAssist456Testsubnet")
		.ifPresent("CfnAssist42TestsubnetWithBuild").act();
	}
	
	@After
	public void afterEachTestHasRun() {
		deletesStacks.act();
	}
	
	@Test
	public void setNoteAOnStack() throws FileNotFoundException, IOException, InvalidParameterException, InterruptedException, CfnAssistException {
		String theComment = "hereIsAComment";
		awsProvider.setCommentTag(theComment); // override for this test
		File templateFile = new File(FilesForTesting.SUBNET_STACK);
		StackId stackId = awsProvider.applyTemplate(templateFile, mainProjectAndEnv);
		
		List<Tag> expectedStackTags = createExpectedStackTags("");
		List<com.amazonaws.services.ec2.model.Tag> expectedEc2Tags = createExpectedEc2Tags("");
		
		expectedStackTags.add(createCfnTag("CFN_COMMENT", theComment));
		expectedEc2Tags.add(createEc2Tag("CFN_COMMENT", theComment));
		
		validateCreateAndDeleteWorks(stackId, expectedStackTags, expectedEc2Tags);
	}
	
	@Test
	public void canBuildAndDeleteSimpleStackWithCorrectTags() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException, InvalidParameterException {	
		File templateFile = new File(FilesForTesting.SUBNET_STACK);
		StackId stackId = awsProvider.applyTemplate(templateFile, mainProjectAndEnv);
		
		validateCreateAndDeleteWorks(stackId, createExpectedStackTags(testName), createExpectedEc2Tags(testName));
	}
	
	@Test
	public void canBuildAndDeleteSimpleStackThatDoesTakeNotBuildParam() throws FileNotFoundException, IOException, CfnAssistException, InterruptedException, InvalidParameterException {	
		// we should not try to populate any parameter NOT declared in the json, doing so will cause an exception
		File templateFile = new File(FilesForTesting.SUBNET_STACK);
		
		String buildNumber = "456";
		mainProjectAndEnv.addBuildNumber(buildNumber); 
		StackId stackName = awsProvider.applyTemplate(templateFile, mainProjectAndEnv);
		
		// tagging should still work even if json does not take build parameter
		List<com.amazonaws.services.ec2.model.Tag> expectedEC2Tags = createExpectedEc2Tags(testName);
		expectedEC2Tags.add(createEc2Tag("CFN_ASSIST_BUILD", buildNumber));
		validateCreateAndDeleteWorks(stackName, createCfnExpectedTagListWithBuild(buildNumber, testName), expectedEC2Tags);
	}

	@Test
	public void canPassInSimpleParameter() throws FileNotFoundException, IOException, InvalidParameterException, 
		CfnAssistException, InterruptedException {
		File templateFile = new File(FilesForTesting.SUBNET_WITH_PARAM);
		
		Collection<Parameter> params = new LinkedList<Parameter>();
		params.add(new Parameter().withParameterKey("zoneA").withParameterValue("eu-west-1a"));
		StackId stackName = awsProvider.applyTemplate(templateFile, mainProjectAndEnv, params);
				
		validateCreateAndDeleteWorks(stackName, createExpectedStackTags(testName), createExpectedEc2Tags(testName));
	}

	@Test
	public void createsAndDeleteSubnetFromTemplateWithBuildNumber() throws FileNotFoundException, IOException, CfnAssistException, InvalidParameterException, InterruptedException {
		String buildNumber = "42";
		
		mainProjectAndEnv.addBuildNumber(buildNumber);
		StackId stackName = awsProvider.applyTemplate(new File(FilesForTesting.SUBNET_WITH_BUILD), mainProjectAndEnv);	
	
		validateCreateAndDeleteWorks(stackName, 
				createCfnExpectedTagListWithBuild(buildNumber, testName), 
				createExpectedTagsWithBuild(buildNumber, testName));
		
		deletesStacks.ifPresent(stackName);
	}

	private void validateCreateAndDeleteWorks(StackId stackId, List<Tag> expectedStackTags, 
			List<com.amazonaws.services.ec2.model.Tag> expectedEc2Tags)
			throws CfnAssistException, InterruptedException {
		String status = monitor.waitForCreateFinished(stackId);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		List<Stack> stacks = getStack(stackId.getStackName());
	    
		List<Tag> stackTags = stacks.get(0).getTags(); 
	    assertEquals(expectedStackTags.size(), stackTags.size());
	    assert(stackTags.containsAll(expectedStackTags));
	    
		List<Subnet> subnets = EnvironmentSetupForTests.getSubnetFors(ec2Client, vpc);
		assertEquals(1, subnets.size());
		Subnet subnet = subnets.get(0);
		List<com.amazonaws.services.ec2.model.Tag> subnetTags = subnet.getTags();
		assertEquals(expectedEc2Tags.size(), subnetTags.size()-EnvironmentSetupForTests.NUMBER_AWS_TAGS);
		assert(subnetTags.containsAll(expectedEc2Tags));
	    
		deletesStacks.ifPresent(stackId);
	}

	private List<Stack> getStack(String stackName) {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult stackResults = cfnClient.describeStacks(describeStacksRequest);
		
		List<Stack> stacks = stackResults.getStacks();
		assertEquals(1,stacks.size());
		return stacks;
	}
	
	private List<Tag> createExpectedStackTags(String comment) {
		List<Tag> expectedTags = new LinkedList<Tag>();
		expectedTags.add(createCfnTag("CFN_ASSIST_ENV", "Test"));
		expectedTags.add(createCfnTag("CFN_ASSIST_PROJECT", "CfnAssist"));
		if (!comment.isEmpty()) {
			expectedTags.add(createCfnTag("CFN_COMMENT", comment));
		}
		return expectedTags;
	}
	
	private List<com.amazonaws.services.ec2.model.Tag> createExpectedEc2Tags(String comment) {
		List<com.amazonaws.services.ec2.model.Tag> tags = new LinkedList<com.amazonaws.services.ec2.model.Tag>();
		tags.add(createEc2Tag("TagEnv",mainProjectAndEnv.getEnv()));
		tags.add(createEc2Tag("Name", "testSubnet"));
		// stack tags appear to be inherited
		tags.add(createEc2Tag("CFN_ASSIST_ENV", "Test"));
		tags.add(createEc2Tag("CFN_ASSIST_PROJECT", "CfnAssist"));
		if (!comment.isEmpty()) {
			tags.add(createEc2Tag("CFN_COMMENT", comment));
		}
		return tags;
	}
	
	private com.amazonaws.services.ec2.model.Tag createEc2Tag(String key,
			String value) {
		return new com.amazonaws.services.ec2.model.Tag().withKey(key).withValue(value);
	}

	private List<com.amazonaws.services.ec2.model.Tag> createExpectedTagsWithBuild(String buildId, String comment) {
		List<com.amazonaws.services.ec2.model.Tag> expectedTags = createExpectedEc2Tags(comment);
		expectedTags.add(createEc2Tag("TagBuild",buildId));
		// stack tags appear to be inherited
		expectedTags.add(createEc2Tag("CFN_ASSIST_BUILD", buildId));
		return expectedTags;
	}

	private List<Tag> createCfnExpectedTagListWithBuild(String buildNumber, String comment) {
		List<Tag> tags = createExpectedStackTags(comment);
		tags.add(createCfnTag("CFN_ASSIST_BUILD", buildNumber));
		return tags;
	}

	private Tag createCfnTag(String key, String value) {
		Tag tag = new Tag();
		tag.setKey(key);
		tag.setValue(value);
		return tag;
	}

}
