package tw.com.providers;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.elasticloadbalancing.model.*;

public class LoadBalancerClassicClient {
	private static final Logger logger = LoggerFactory.getLogger(LoadBalancerClassicClient.class);

	private final ElasticLoadBalancingClient elbClient;

	public LoadBalancerClassicClient(ElasticLoadBalancingClient elbClient) {
		this.elbClient = elbClient;
	}

	public List<LoadBalancerDescription> describeLoadBalancers() {
		DescribeLoadBalancersRequest request = DescribeLoadBalancersRequest.builder().build();
		DescribeLoadBalancersResponse result = elbClient.describeLoadBalancers(request);
		if (result==null) {
			logger.warn("Found no ELBs");
			return Collections.emptyList();
		}
		List<LoadBalancerDescription> descriptions = result.loadBalancerDescriptions();
		logger.info(String.format("Found %s classic load balancers %s", descriptions.size(), descriptions));
		return descriptions;
	}

	public void registerInstances(List<Instance> instances, String lbName) {
		logger.info(String.format("Registering instances %s with loadbalancer %s", instances, lbName));
		RegisterInstancesWithLoadBalancerRequest.Builder regInstances = RegisterInstancesWithLoadBalancerRequest.builder();
		regInstances.instances(instances);
		regInstances.loadBalancerName(lbName);
		RegisterInstancesWithLoadBalancerResponse result = elbClient.registerInstancesWithLoadBalancer(regInstances.build());
		
		logger.info("ELB Add instance call result: " + result.toString());	
	}

	public List<Instance> deregisterInstancesFromLB(List<Instance> toRemove, String loadBalancerName) {
		
		DeregisterInstancesFromLoadBalancerRequest.Builder request= DeregisterInstancesFromLoadBalancerRequest.builder();
		request.instances(toRemove);
		
		request.loadBalancerName(loadBalancerName);
		DeregisterInstancesFromLoadBalancerResponse result = elbClient.deregisterInstancesFromLoadBalancer(request.build());
		List<Instance> remaining = result.instances();
		logger.info(String.format("ELB %s now has %s instances registered", loadBalancerName, remaining.size()));
		return remaining;
	}

	public List<Tag> getTagsFor(String loadBalancerName) {
		DescribeTagsRequest describeTagsRequest = DescribeTagsRequest.builder().loadBalancerNames(loadBalancerName).build();
		DescribeTagsResponse result = elbClient.describeTags(describeTagsRequest);
		List<TagDescription> descriptions = result.tagDescriptions();
		logger.info(String.format("Fetching %s tags for LB %s ", descriptions.size(), loadBalancerName));
		return descriptions.get(0).tags();
	}

	public List<Instance> getInstancesFor(LoadBalancerDescription balancerDescription) {
		return balancerDescription.instances();
	}
}
