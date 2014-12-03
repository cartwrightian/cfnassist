package tw.com.integration;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.Tag;
import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.LoadBalancerClient;

public class TestLoadBalancerClient  {
	
	private static final String LB_NAME = "cfnAssistTest";
	LoadBalancerClient client;
	private static AmazonElasticLoadBalancingClient elbClient;
	private static AmazonEC2Client ec2Client;
	private static Instance instance;
	
	@BeforeClass
	public static void onceBeforeSuiteOfTestsRun() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		elbClient = EnvironmentSetupForTests.createELBClient(credentialsProvider);
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		
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
		client = new LoadBalancerClient(elbClient);
	}
	
	@Test
	public void shouldDescribeELBs() {		
		List<LoadBalancerDescription> results = client.describeLoadBalancers();
		
		List<Tag> tags = null;
		boolean found = false;
		for(LoadBalancerDescription candidate : results) {
			if (candidate.getLoadBalancerName().equals(LB_NAME)) {
				tags = client.getTagsFor(LB_NAME);
				found = true;
				break;
			}
		}
		assertTrue(found);
		assertEquals(1, tags.size());
		Tag theTag = tags.get(0);
		assertEquals(AwsFacade.TYPE_TAG, theTag.getKey());
		assertEquals("tagValue", theTag.getValue());
	}
	
	@Test
	public void shouldRegisterAndDeregisterInstances() throws InterruptedException {
		List<com.amazonaws.services.elasticloadbalancing.model.Instance> instances = new LinkedList<com.amazonaws.services.elasticloadbalancing.model.Instance>();
		instances.add(new com.amazonaws.services.elasticloadbalancing.model.Instance(instance.getInstanceId()));
		
		client.registerInstances(instances, LB_NAME);
		LoadBalancerDescription lbDescription = getUpToDateLBDescription();
		
		boolean found = false;
		List<com.amazonaws.services.elasticloadbalancing.model.Instance> results = lbDescription.getInstances();
		for(com.amazonaws.services.elasticloadbalancing.model.Instance candidate : results) {
			if (candidate.getInstanceId().equals(instance.getInstanceId())) {
				found = true;
				break;
			}
		}
		assertTrue(found);
		
		client.degisterInstancesFromLB(instances, LB_NAME);		
		
		lbDescription = getUpToDateLBDescription();
		results = lbDescription.getInstances();
		assertEquals(0, results.size());
	}

	private LoadBalancerDescription getUpToDateLBDescription() {
		DescribeLoadBalancersResult result = elbClient.describeLoadBalancers();
		List<LoadBalancerDescription> lbDesc = result.getLoadBalancerDescriptions();
		for(LoadBalancerDescription candidate : lbDesc) {
			if (candidate.getLoadBalancerName().equals(LB_NAME)) {
				return candidate;
			}
		}
		fail("could not find instance");
		return null;
	}
	
	private static void createLoadBalancer() {
		CreateLoadBalancerRequest createLoadBalancerRequest = new CreateLoadBalancerRequest().
				withLoadBalancerName(LB_NAME).
				withListeners(new Listener("HTTP",8000,8000)).
				withAvailabilityZones(EnvironmentSetupForTests.AVAILABILITY_ZONE).withTags(createTags());
		elbClient.createLoadBalancer(createLoadBalancerRequest);
	}
	
	private static Tag createTags() {
		return new Tag().withKey(AwsFacade.TYPE_TAG).withValue("tagValue");
	}

	private static void deleteLoadBalancer() {
		elbClient.deleteLoadBalancer(new DeleteLoadBalancerRequest(LB_NAME));
	}

	private static void deleteInstance() {
		if (instance!=null) {
			ec2Client.terminateInstances(new TerminateInstancesRequest().withInstanceIds(instance.getInstanceId()));
		}	
	}
	
}
