package tw.com.integration;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.elasticloadbalancing.model.*;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.LoadBalancerClassicClient;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static tw.com.EnvironmentSetupForTests.TEST_SUBNET_FOR_MAIN_VPC;

public class TestLoadBalancerClient  {
	
	private static final String LB_NAME = "cfnAssistTest";
	private LoadBalancerClassicClient client;
	private static ElasticLoadBalancingClient elbClient;
	private static Ec2Client ec2Client;
	private static Instance instance;
	
	@BeforeClass
	public static void onceBeforeSuiteOfTestsRun() {
		elbClient = EnvironmentSetupForTests.createELBClient();
		ec2Client = EnvironmentSetupForTests.createEC2Client();
		
		createLoadBalancer();
		instance = EnvironmentSetupForTests.createSimpleInstance(ec2Client);
	}
	
	@AfterClass
	public static void onceAfterAllTestsHaveRun() {
		deleteLoadBalancer();
		deleteInstance();
	}

	@Before
	public void beforeEachTestRuns() {
		client = new LoadBalancerClassicClient(elbClient);
	}
	
	@Test
	public void shouldDescribeELBs() {		
		List<LoadBalancerDescription> results = client.describeLoadBalancers();
		
		List<Tag> tags = null;
		boolean found = false;
		for(LoadBalancerDescription candidate : results) {
			if (candidate.loadBalancerName().equals(LB_NAME)) {
				tags = client.getTagsFor(LB_NAME);
				found = true;
				break;
			}
		}
		assertTrue(found);
		assertEquals(1, tags.size());
		Tag theTag = tags.get(0);
		assertEquals(AwsFacade.TYPE_TAG, theTag.key());
		assertEquals("tagValue", theTag.value());
	}
	
	@Test
	public void shouldRegisterAndDeregisterInstances() {
		List<software.amazon.awssdk.services.elasticloadbalancing.model.Instance> instances = new LinkedList<>();
		instances.add(software.amazon.awssdk.services.elasticloadbalancing.model.Instance.builder().instanceId(instance.instanceId()).build());
		
		client.registerInstances(instances, LB_NAME);
		LoadBalancerDescription lbDescription = getUpToDateLBDescription();
		
		boolean found = false;
		List<software.amazon.awssdk.services.elasticloadbalancing.model.Instance> results = lbDescription.instances();
		for(software.amazon.awssdk.services.elasticloadbalancing.model.Instance candidate : results) {
			if (candidate.instanceId().equals(instance.instanceId())) {
				found = true;
				break;
			}
		}
		assertTrue(found);
		
		client.deregisterInstancesFromLB(instances, LB_NAME);
		
		lbDescription = getUpToDateLBDescription();
		results = lbDescription.instances();
		assertEquals(0, results.size());
	}

	private LoadBalancerDescription getUpToDateLBDescription() {
		DescribeLoadBalancersResponse result = elbClient.describeLoadBalancers();
		List<LoadBalancerDescription> lbDesc = result.loadBalancerDescriptions();
		for(LoadBalancerDescription candidate : lbDesc) {
			if (candidate.loadBalancerName().equals(LB_NAME)) {
				return candidate;
			}
		}
		fail("could not find instance");
		return null;
	}
	
	private static void createLoadBalancer() {
		Listener listener = Listener.builder().protocol("HTTP").instancePort(8000).loadBalancerPort(8000).build();
		CreateLoadBalancerRequest createLoadBalancerRequest = CreateLoadBalancerRequest.builder().
				loadBalancerName(LB_NAME).
				listeners(listener).
				subnets(Collections.singleton(TEST_SUBNET_FOR_MAIN_VPC)).
				//availabilityZones(EnvironmentSetupForTests.AVAILABILITY_ZONE).
				tags(createTags()).build();
		elbClient.createLoadBalancer(createLoadBalancerRequest);
	}
	
	private static Tag createTags() {
		return Tag.builder().key(AwsFacade.TYPE_TAG).value("tagValue").build();
	}

	private static void deleteLoadBalancer() {
		elbClient.deleteLoadBalancer(
				DeleteLoadBalancerRequest.builder().loadBalancerName(LB_NAME).build());
	}

	private static void deleteInstance() {
		if (instance!=null) {
			ec2Client.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instance.instanceId()).build());
		}	
	}
	
}
