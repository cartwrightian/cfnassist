package tw.com.integration;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.providers.CloudClient;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.*;

public class TestCloudClient {
	
	private CloudClient cloudClient;
	private Ec2Client ec2Client;
	private String VpcId = EnvironmentSetupForTests.VPC_ID_FOR_ALT_ENV;
	private Tag expectedTag = EnvironmentSetupForTests.createEc2Tag(AwsFacade.ENVIRONMENT_TAG, EnvironmentSetupForTests.ALT_ENV);
	private software.amazon.awssdk.services.ec2.model.Instance instance;
	
	@Before
	public void beforeEachTestIsRun() {
		ec2Client = EnvironmentSetupForTests.createEC2Client();
		cloudClient = new CloudClient(ec2Client, new DefaultAwsRegionProviderChain());
	}
	
	@After
	public void afterEachTestIsRun() {
		if (instance!=null) {
			ec2Client.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instance.instanceId()).build());
		}
	}

	@Test
	public void testCanSetAnDeleteTagsForResource() {
			
		List<Tag> tags = new LinkedList<>();
		Tag tagA = EnvironmentSetupForTests.createEc2Tag("T1", "Value1");
		Tag tagB = EnvironmentSetupForTests.createEc2Tag("T2", "Value2");
		tags.add(tagA);
		tags.add(tagB);
		
		List<String> resources = new LinkedList<>();
		resources.add(VpcId);
		cloudClient.addTagsToResources(resources, tags);

		DescribeVpcsResponse queryResult = ec2Client.describeVpcs(DescribeVpcsRequest.builder().vpcIds(VpcId).build());
		List<Tag> results = queryResult.vpcs().get(0).tags();
		
		assertTrue(results.contains(tagA));
		assertTrue(results.contains(tagB));	
		
		// Now delete
		
		cloudClient.deleteTagsFromResources(resources, tagA);
		cloudClient.deleteTagsFromResources(resources, tagB);
		
		queryResult = ec2Client.describeVpcs(DescribeVpcsRequest.builder().vpcIds(VpcId).build());
		List<Tag> deleteResults = queryResult.vpcs().get(0).tags();
		
		assertFalse(deleteResults.contains(tagA));
		assertFalse(deleteResults.contains(tagB));	
	}
	
	@Test
	public void testCanQueryVpcById() {
		Vpc result = cloudClient.describeVpc(VpcId);
		List<Tag> tags = result.tags();
		assertTrue(tags.contains(expectedTag));		
	}
	
	@Test 
	public void testCanGetAllVpcs() {
		List<Vpc> results = cloudClient.describeVpcs();
		
		boolean matched = false;
		for(Vpc candidate : results) {
			matched = candidate.tags().contains(expectedTag);
			if (matched) break;
		}
		assertTrue(matched);
	}

	@Test
	public void shouldGetAvailabilityZones() {
		Region region = new DefaultAwsRegionProviderChain().getRegion();
		Map<String, AvailabilityZone> zones = cloudClient.getAvailabilityZones();

		assertEquals(3, zones.size());
		zones.forEach((name, zone) -> assertEquals(region.id(), zone.regionName()));
        assertTrue(zones.containsKey("a"));
        assertTrue(zones.containsKey("b"));
	}

	@Test
	public void testShouldBeAbleToGetInstanceById() throws WrongNumberOfInstancesException {
		instance = EnvironmentSetupForTests.createSimpleInstance(ec2Client);

		String instanceId = instance.instanceId();
		
		Instance result = cloudClient.getInstanceById(instanceId);
		assertEquals(instanceId, result.instanceId());
	}

	@Test
	public void shouldCreateKeyPair() {
        String testKeypairName = "testKeyPairName";

		CloudClient.AWSPrivateKey keyPair = cloudClient.createKeyPair(testKeypairName);

        DeleteKeyPairRequest deleteRequest = DeleteKeyPairRequest.builder().keyName(testKeypairName).build();
        ec2Client.deleteKeyPair(deleteRequest);

        assertEquals(testKeypairName, keyPair.getKeyName());
        assertThat(keyPair.getMaterial(), startsWith("-----BEGIN RSA PRIVATE KEY-----"));
        assertThat(keyPair.getMaterial(), endsWith("-----END RSA PRIVATE KEY-----"));
	}

}
