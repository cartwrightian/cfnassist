package tw.com.integration;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.exceptions.CannotFindVpcException;
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
			
		List<Tag> tags = new LinkedList<>();
		Tag tagA = EnvironmentSetupForTests.createEc2Tag("T1", "Value1");
		Tag tagB = EnvironmentSetupForTests.createEc2Tag("T2", "Value2");
		tags.add(tagA);
		tags.add(tagB);
		
		List<String> resources = new LinkedList<>();
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
	public void shouldGetAvailabilityZones() {
		Region region = EnvironmentSetupForTests.getRegion();
		String regionName = EnvironmentSetupForTests.getRegion().getName();
		Map<String, AvailabilityZone> zones = cloudClient.getAvailabilityZones(regionName);

		assertEquals(3, zones.size());
		zones.forEach((name, zone) -> assertEquals(region.getName(), zone.getRegionName()));
        assertTrue(zones.containsKey("a"));
        assertTrue(zones.containsKey("b"));
	}
	
	@Test
	public void testShouldBeAbleToGetInstanceById() throws WrongNumberOfInstancesException {
		instance = EnvironmentSetupForTests.createSimpleInstance(ec2Client);

		String instanceId = instance.getInstanceId();
		
		Instance result = cloudClient.getInstanceById(instanceId);
		assertEquals(instanceId, result.getInstanceId());
	}

	@Test
	public void shouldCreateKeyPair() {
        String testKeypairName = "testKeyPairName";

        KeyPair keyPair = cloudClient.createKeyPair(testKeypairName);

        DeleteKeyPairRequest deleteRequest = new DeleteKeyPairRequest().withKeyName(testKeypairName);
        ec2Client.deleteKeyPair(deleteRequest);

        assertEquals(testKeypairName, keyPair.getKeyName());
        assertThat(keyPair.getKeyMaterial(), startsWith("-----BEGIN RSA PRIVATE KEY-----"));
        assertThat(keyPair.getKeyMaterial(), endsWith("-----END RSA PRIVATE KEY-----"));
	}

}
