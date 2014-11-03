package tw.com.integration;

import static org.junit.Assert.*;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.Vpc;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.providers.CloudClient;

public class TestCloudClient {
	
	private CloudClient cloudClient;
	private AmazonEC2Client ec2Client;
	private String VpcId = EnvironmentSetupForTests.VPC_ID_FOR_ALT_ENV;
	private Tag expectedTag = EnvironmentSetupForTests.createEc2Tag(AwsFacade.ENVIRONMENT_TAG, EnvironmentSetupForTests.ALT_ENV);
	private com.amazonaws.services.ec2.model.Instance instance;
	
	@Before
	public void beforeEachTestIsRun() {
		ec2Client = EnvironmentSetupForTests.createEC2Client(new DefaultAWSCredentialsProviderChain());
		cloudClient = new CloudClient(ec2Client);
	}
	
	@After
	public void afterEachTestIsRun() {
		if (instance!=null) {
			ec2Client.terminateInstances(new TerminateInstancesRequest().withInstanceIds(instance.getInstanceId()));
		}
	}
		
	@Test
	public void testCanSetAnDeleteTagsForResource() throws CannotFindVpcException {
			
		List<Tag> tags = new LinkedList<Tag>();
		Tag tagA = EnvironmentSetupForTests.createEc2Tag("T1", "Value1");
		Tag tagB = EnvironmentSetupForTests.createEc2Tag("T2", "Value2");
		tags.add(tagA);
		tags.add(tagB);
		
		List<String> resources = new LinkedList<String>();
		resources.add(VpcId);
		cloudClient.addTagsToResources(resources, tags);
		
		DescribeVpcsResult queryResult = ec2Client.describeVpcs(new DescribeVpcsRequest().withVpcIds(VpcId));
		List<Tag> results = queryResult.getVpcs().get(0).getTags();
		
		assertTrue(results.contains(tagA));
		assertTrue(results.contains(tagB));	
		
		// Now delete
		
		cloudClient.deleteTagsFromResources(resources, tagA);
		cloudClient.deleteTagsFromResources(resources, tagB);
		
		queryResult = ec2Client.describeVpcs(new DescribeVpcsRequest().withVpcIds(VpcId));
		List<Tag> deleteResults = queryResult.getVpcs().get(0).getTags();
		
		assertFalse(deleteResults.contains(tagA));
		assertFalse(deleteResults.contains(tagB));	
	}
	
	@Test
	public void testCanQueryVpcById() {
		Vpc result = cloudClient.describeVpc(VpcId);
		List<Tag> tags = result.getTags();
		assertTrue(tags.contains(expectedTag));		
	}
	
	@Test 
	public void testCanGetAllVpcs() {
		List<Vpc> results = cloudClient.describeVpcs();
		
		boolean matched = false;
		for(Vpc candidate : results) {
			matched = candidate.getTags().contains(expectedTag);
			if (matched) break;
		}
		assertTrue(matched);
	}
	
	@Test
	public void testShouldBeAbleToGetTagsForAnInstances() throws WrongNumberOfInstancesException {
		instance = EnvironmentSetupForTests.createSimpleInstance(ec2Client);

		String instanceId = instance.getInstanceId();
		Tag tag = new Tag().withKey("testTag").withValue("tagValue");
		ec2Client.createTags(new CreateTagsRequest().withResources(instanceId).withTags(tag));
		
		List<Tag> result = cloudClient.getTagsForInstance(instanceId);
		assertTrue(result.contains(tag));
	}

}
