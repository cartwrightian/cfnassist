package tw.com;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.TagsAlreadyInit;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestDeltaIndexTagging {

	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	private ProjectAndEnv altProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private AwsProvider aws;
	private VpcRepository vpcRepos;
	private AmazonEC2Client directClient;
	private Vpc altVpc;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, EnvironmentSetupForTests.getRegion());
		vpcRepos = new VpcRepository(EnvironmentSetupForTests.createEC2Client(credentialsProvider));
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);

		altVpc = vpcRepos.getCopyOfVpc(altProjectAndEnv);
		
	}
	
	@After
	public void afterAllTestsRun() {
	}
	
	@Test
	public void canSetAndResetTheDeltaIndexTagOnVpc() throws CannotFindVpcException {
		aws.resetDeltaIndex(mainProjectAndEnv);	
		aws.setDeltaIndex(mainProjectAndEnv, 42);
		int tagValue = getValueOfTag();
		assertEquals(42, tagValue);	
		int result = aws.getDeltaIndex(mainProjectAndEnv);
		assertEquals(42, result);
		
		aws.resetDeltaIndex(mainProjectAndEnv);
		tagValue = getValueOfTag();
		assertEquals(0, tagValue);
		result = aws.getDeltaIndex(mainProjectAndEnv);
		assertEquals(0, result);
	}
	
	@Test
	public void shouldThrowForUnknownProjectAndEnvCombinationOnDeltaReset() {
		ProjectAndEnv dummyProjAndEnv = new ProjectAndEnv("9XXX", "91234");
		try {
			aws.resetDeltaIndex(dummyProjAndEnv);
			fail("Should have thrown exception");
		}
		catch(CannotFindVpcException expected) {
			// expected
		}
	}
	
	@Test
	public void shouldThrowForUnknownProjectAndEnvCombinationOnDeltaSet() {
		ProjectAndEnv dummyProjAndEnv = new ProjectAndEnv("9XXX", "91234");
		try {
			aws.setDeltaIndex(dummyProjAndEnv, 99);
			fail("Should have thrown exception");
		}
		catch(CannotFindVpcException expected) {
			// expected
		}
	}
	
	@Test
	public void shouldInitTagsOnNewVpc() throws CfnAssistException, CannotFindVpcException {
		EnvironmentSetupForTests.clearVpcTags(directClient, altVpc);
		aws.initEnvAndProjectForVPC(altVpc.getVpcId(), altProjectAndEnv);
		aws.setDeltaIndex(altProjectAndEnv, 42);
		assertEquals(42, aws.getDeltaIndex(altProjectAndEnv));
	}
	
	@Test
	public void shouldThrownOnInitTagsWhenAlreadyPresent() throws CfnAssistException, CannotFindVpcException {
		EnvironmentSetupForTests.clearVpcTags(directClient, altVpc);
		aws.initEnvAndProjectForVPC(altVpc.getVpcId(), altProjectAndEnv);
		try {
			aws.initEnvAndProjectForVPC(altVpc.getVpcId(), altProjectAndEnv);
			fail("Should have thrown due to attempt to re-init tags");
		}
		catch(TagsAlreadyInit exception) {
			// expected
		}
	}

	private int getValueOfTag() {
		Vpc vpc = vpcRepos.getCopyOfVpc(mainProjectAndEnv);
		List<Tag> tags = vpc.getTags();
		for(Tag tag : tags) {
			if (tag.getKey().equals(AwsFacade.INDEX_TAG)) {
				return Integer.parseInt(tag.getValue());
			}
		}
		return -1;
	}

}
