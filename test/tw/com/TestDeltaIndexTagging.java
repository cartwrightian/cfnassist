package tw.com;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestDeltaIndexTagging {

	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(TestAwsFacade.PROJECT, TestAwsFacade.ENV);
	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private AwsFacade aws;
	private VpcRepository vpcRepos;
	private AmazonEC2Client directClient;
	private Vpc tempVpc;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, TestAwsFacade.getRegion());
		vpcRepos = new VpcRepository(credentialsProvider, TestAwsFacade.getRegion());
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		
		createVpc();
	}
	
	@After
	public void afterAllTestsRun() {
		DeleteVpcRequest deleteVpcRequest = new DeleteVpcRequest();
		deleteVpcRequest.setVpcId(tempVpc.getVpcId());
		directClient.deleteVpc(deleteVpcRequest);
	}

	private void createVpc() {
		CreateVpcRequest createVpcRequest = new CreateVpcRequest();
		createVpcRequest.setCidrBlock("10.0.10.0/24");
		CreateVpcResult result = directClient.createVpc(createVpcRequest );
		tempVpc = result.getVpc();
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
	public void shouldInitTagsOnNewVpc() throws TagsAlreadyInit, CannotFindVpcException {		
		ProjectAndEnv altProjectAndEnv = new ProjectAndEnv(TestAwsFacade.PROJECT, "temp");
		aws.initTags(altProjectAndEnv, tempVpc.getVpcId());
		aws.setDeltaIndex(altProjectAndEnv, 42);
		assertEquals(42, aws.getDeltaIndex(altProjectAndEnv));
	}
	
	@Test
	public void shouldThrownOnInitTagsWhenAlreadyPresent() throws TagsAlreadyInit, CannotFindVpcException {
		ProjectAndEnv altProjectAndEnv = new ProjectAndEnv(TestAwsFacade.PROJECT, "anotherTemp");
		aws.initTags(altProjectAndEnv, tempVpc.getVpcId());
		try {
			aws.initTags(altProjectAndEnv, tempVpc.getVpcId());
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
