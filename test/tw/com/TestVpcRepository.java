package tw.com;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import tw.com.exceptions.CannotFindVpcException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestVpcRepository {

	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	private ProjectAndEnv altProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);

	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private VpcRepository repository;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		repository = new VpcRepository(EnvironmentSetupForTests.createEC2Client(credentialsProvider));
	}
	
	@Test
	public void testFindMainVpcForTests() {
		Vpc vpc = repository.getCopyOfVpc(mainProjectAndEnv);
		
		assertNotNull(vpc);
		
		List<Tag> tags = vpc.getTags();	
		List<Tag> expectedTags = createExpectedEc2TagList("Test");		
		assertTrue(tags.containsAll(expectedTags));
	}

	@Test
	public void testFindOtherVpcForTests() {
		Vpc vpc = repository.getCopyOfVpc(altProjectAndEnv);
		
		assertNotNull(vpc);
		List<Tag> tags = vpc.getTags();	

		List<Tag> expectedTags = createExpectedEc2TagList("AdditionalTest");		
		assertTrue(tags.containsAll(expectedTags));
	}
	
	@Test
	public void testCanSetAndResetIndexTagForVpc() throws CannotFindVpcException {
		repository.setVpcIndexTag(mainProjectAndEnv, "TESTVALUE");
		String result = repository.getVpcIndexTag(mainProjectAndEnv);	
		assertEquals("TESTVALUE", result);
		
		repository.setVpcIndexTag(mainProjectAndEnv, "ANOTHERVALUE");
		result = repository.getVpcIndexTag(mainProjectAndEnv);	
		assertEquals("ANOTHERVALUE", result);
	}
	
	private List<Tag> createExpectedEc2TagList(String env) {
		List<Tag> expectedTags = new ArrayList<Tag>();
		expectedTags.add(new Tag("CFN_ASSIST_ENV", env));
		expectedTags.add(new Tag("CFN_ASSIST_PROJECT", "CfnAssist"));
		return expectedTags;
	}


}
