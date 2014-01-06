package tw.com;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestVpcRepository {

	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private VpcRepository repository;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		repository = new VpcRepository(credentialsProvider, TestAwsFacade.getRegion());
	}
	
	@Test
	public void testFindMainVpcForTests() {
		Vpc vpc = repository.getCopyOfVpc(TestAwsFacade.PROJECT, TestAwsFacade.ENV);
		
		assertNotNull(vpc);
		
		List<Tag> tags = vpc.getTags();	
		List<Tag> expectedTags = createExpectedEc2TagList("Test");		
		assertTrue(tags.containsAll(expectedTags));
	}

	@Test
	public void testFindOtherVpcForTests() {
		Vpc vpc = repository.getCopyOfVpc(TestAwsFacade.PROJECT, EnvironmentSetupForTests.ALT_ENV);
		
		assertNotNull(vpc);
		List<Tag> tags = vpc.getTags();	

		List<Tag> expectedTags = createExpectedEc2TagList("AdditionalTest");		
		assertTrue(tags.containsAll(expectedTags));
	}
	
	@Test
	public void testCanSetAndResetIndexTagForVpc() {
		repository.setVpcIndexTag(TestAwsFacade.PROJECT, TestAwsFacade.ENV, "TESTVALUE");
		String result = repository.getVpcIndexTag(TestAwsFacade.PROJECT, TestAwsFacade.ENV);	
		assertEquals("TESTVALUE", result);
		
		repository.setVpcIndexTag(TestAwsFacade.PROJECT, TestAwsFacade.ENV, "ANOTHERVALUE");
		result = repository.getVpcIndexTag(TestAwsFacade.PROJECT, TestAwsFacade.ENV);	
		assertEquals("ANOTHERVALUE", result);
	}
	
	private List<Tag> createExpectedEc2TagList(String env) {
		List<Tag> expectedTags = new ArrayList<Tag>();
		expectedTags.add(new Tag("CFN_ASSIST_ENV", env));
		expectedTags.add(new Tag("CFN_ASSIST_PROJECT", "CfnAssist"));
		return expectedTags;
	}


}
