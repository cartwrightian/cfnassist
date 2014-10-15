package tw.com.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.repository.CfnRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestDeltaIndexTagging {
	private VpcRepository vpcRepos;
	private static AmazonEC2Client directClient;
	private static AmazonCloudFormationClient cfnClient;

	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	private ProjectAndEnv altProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ALT_ENV);
	private AwsFacade aws;
	private Vpc altVpc;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		directClient = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		cfnClient = EnvironmentSetupForTests.createCFNClient(credentialsProvider);		
	}

	@Rule public TestName test = new TestName();
	
	@Before
	public void beforeTestsRun() {
		CfnRepository cfnRepository = new CfnRepository(cfnClient, EnvironmentSetupForTests.PROJECT);
		MonitorStackEvents monitor = new PollingStackMonitor(cfnRepository);	
		vpcRepos = new VpcRepository(directClient);
		aws = new AwsFacade(monitor , cfnClient, cfnRepository, vpcRepos);	
		aws.setCommentTag(test.getMethodName());
		
		altVpc = EnvironmentSetupForTests.findAltVpc(vpcRepos);
	}
	
	@After
	public void afterAllTestsRun() {
	}
	
	@Test
	public void canSetAndResetTheDeltaIndexTagOnVpc() throws CfnAssistException {
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
	public void shouldInitTagsOnNewVpc() throws CfnAssistException, CannotFindVpcException, InterruptedException {
		EnvironmentSetupForTests.clearVpcTags(directClient, altVpc);
		aws.initEnvAndProjectForVPC(altVpc.getVpcId(), altProjectAndEnv);
		aws.setDeltaIndex(altProjectAndEnv, 42);
		assertEquals(42, aws.getDeltaIndex(altProjectAndEnv));
	}
	
	@Test
	public void shouldThrownOnInitTagsWhenAlreadyPresent() throws CfnAssistException, CannotFindVpcException, InterruptedException {
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
