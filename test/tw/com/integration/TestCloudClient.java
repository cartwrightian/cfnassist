package tw.com.integration;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.model.Tag;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.providers.CloudClient;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.StringEndsWith.endsWith;

class TestCloudClient {
	
	private CloudClient cloudClient;
	private Ec2Client ec2Client;
	private String VpcId = EnvironmentSetupForTests.VPC_ID_FOR_ALT_ENV;
	private Tag expectedTag = EnvironmentSetupForTests.createEc2Tag(AwsFacade.ENVIRONMENT_TAG, EnvironmentSetupForTests.ALT_ENV);
	private software.amazon.awssdk.services.ec2.model.Instance instance;
	
	@BeforeEach
	public void beforeEachTestIsRun() {
		ec2Client = EnvironmentSetupForTests.createEC2Client();
		cloudClient = new CloudClient(ec2Client, new DefaultAwsRegionProviderChain());
	}
	
	@AfterEach
	public void afterEachTestIsRun() {
		if (instance!=null) {
			ec2Client.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instance.instanceId()).build());
		}
	}

	@Test
    void testCanSetAnDeleteTagsForResource() {
			
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
		
		Assertions.assertTrue(results.contains(tagA));
		Assertions.assertTrue(results.contains(tagB));
		
		// Now delete
		
		cloudClient.deleteTagsFromResources(resources, tagA);
		cloudClient.deleteTagsFromResources(resources, tagB);
		
		queryResult = ec2Client.describeVpcs(DescribeVpcsRequest.builder().vpcIds(VpcId).build());
		List<Tag> deleteResults = queryResult.vpcs().get(0).tags();
		
		Assertions.assertFalse(deleteResults.contains(tagA));
		Assertions.assertFalse(deleteResults.contains(tagB));
	}
	
	@Test
    void testCanQueryVpcById() {
		Vpc result = cloudClient.describeVpc(VpcId);
		List<Tag> tags = result.tags();
		Assertions.assertTrue(tags.contains(expectedTag));
	}
	
	@Test
    void testCanGetAllVpcs() {
		List<Vpc> results = cloudClient.describeVpcs();
		
		boolean matched = false;
		for(Vpc candidate : results) {
			matched = candidate.tags().contains(expectedTag);
			if (matched) break;
		}
		Assertions.assertTrue(matched, "Did not find " + expectedTag + " in " + results);
	}

	@Test
    void shouldGetAvailabilityZones() {
		Region region = new DefaultAwsRegionProviderChain().getRegion();
		Map<String, AvailabilityZone> zones = cloudClient.getAvailabilityZones();

		Assertions.assertEquals(3, zones.size());
		zones.forEach((name, zone) -> Assertions.assertEquals(region.id(), zone.regionName()));
        Assertions.assertTrue(zones.containsKey("a"));
        Assertions.assertTrue(zones.containsKey("b"));
	}

	@Test
    void testShouldBeAbleToGetInstanceById() throws WrongNumberOfInstancesException {
		instance = EnvironmentSetupForTests.createSimpleInstance(ec2Client);

		String instanceId = instance.instanceId();
		
		Instance result = cloudClient.getInstanceById(instanceId);
		Assertions.assertEquals(instanceId, result.instanceId());
	}

	@Test
    void shouldCreateKeyPair() {
        String testKeypairName = "testKeyPairName";

		CloudClient.AWSPrivateKey keyPair = cloudClient.createKeyPair(testKeypairName);

        DeleteKeyPairRequest deleteRequest = DeleteKeyPairRequest.builder().keyName(testKeypairName).build();
        ec2Client.deleteKeyPair(deleteRequest);

        Assertions.assertEquals(testKeypairName, keyPair.getKeyName());
        MatcherAssert.assertThat(keyPair.getMaterial(), startsWith("-----BEGIN RSA PRIVATE KEY-----"));
        MatcherAssert.assertThat(keyPair.getMaterial(), endsWith("-----END RSA PRIVATE KEY-----"));
	}

}
