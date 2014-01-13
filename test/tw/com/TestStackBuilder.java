package tw.com;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.Tag;

public class TestStackBuilder {
	private AwsProvider awsProvider;
	private String env = EnvironmentSetupForTests.ENV;
	private String project = EnvironmentSetupForTests.PROJECT;
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(project, env);

	private AmazonCloudFormationClient cfnClient;

	@Before
	public void beforeTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		awsProvider = new AwsFacade(credentialsProvider, EnvironmentSetupForTests.getRegion());
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);
	}

	@Test
	public void canBuildAndDeleteSimpleStackWithCorrectTags() throws FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, InvalidParameterException, StackCreateFailed {	
		File templateFile = new File(EnvironmentSetupForTests.SUBNET_FILENAME);
		StackBuilder builder = new StackBuilder(awsProvider, mainProjectAndEnv, templateFile);
		String stackName = builder.createStack();
		
		validateCreateAndDeleteWorks(stackName);
	}
	
	@Test
	public void canPassInSimpleParameter() throws FileNotFoundException, IOException, InvalidParameterException, 
			WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		File templateFile = new File(EnvironmentSetupForTests.SUBNET_WITH_PARAM_FILENAME);
		StackBuilder builder = new StackBuilder(awsProvider, mainProjectAndEnv, templateFile);
		String stackName = builder.addParameter("zoneA", "eu-west-1a").createStack();
		
		validateCreateAndDeleteWorks(stackName);
	}

	private void validateCreateAndDeleteWorks(String stackName)
			throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed {
		String status = awsProvider.waitForCreateFinished(stackName);
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), status);
		
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult stackResults = cfnClient.describeStacks(describeStacksRequest);
		
		List<Stack> stacks = stackResults.getStacks();
		assertEquals(1,stacks.size());
	    
		List<Tag> tags = stacks.get(0).getTags(); 
	    List<Tag> expectedTags = createCfnExpectedTagList();
	    assertEquals(expectedTags.size(), tags.size());
	    assert(tags.containsAll(expectedTags));
	    
		awsProvider.deleteStack(stackName);
		
		status = awsProvider.waitForDeleteFinished(stackName);
		assertEquals(StackStatus.DELETE_COMPLETE.toString(), status);
	}
	
	private List<Tag> createCfnExpectedTagList() {
		List<Tag> expectedTags = new LinkedList<Tag>();
		expectedTags.add(createCfnTag("CFN_ASSIST_ENV", "Test"));
		expectedTags.add(createCfnTag("CFN_ASSIST_PROJECT", "CfnAssist"));
		return expectedTags;
	}

	private Tag createCfnTag(String key, String value) {
		Tag tag = new Tag();
		tag.setKey(key);
		tag.setValue(value);
		return tag;
	}

}
