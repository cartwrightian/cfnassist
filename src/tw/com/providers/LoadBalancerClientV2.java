package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// V2 following AWS convention for the name of the API

public class LoadBalancerClientV2 {
	private static final Logger logger = LoggerFactory.getLogger(LoadBalancerClientV2.class);

	private final ElasticLoadBalancingV2Client elbClient;

	public LoadBalancerClientV2(ElasticLoadBalancingV2Client elbClient) {
		this.elbClient = elbClient;
	}

	public List<LoadBalancer> describeLoadBalancers() {
		DescribeLoadBalancersRequest request = DescribeLoadBalancersRequest.builder().build();
		DescribeLoadBalancersResponse result = elbClient.describeLoadBalancers(request);
		List<LoadBalancer> descriptions = result.loadBalancers();
		logger.info(String.format("Found %s load balancers %s", descriptions.size(), descriptions));
		return descriptions;
	}

//	public void registerInstances(List<Instance> instances, String lbName) {
//		logger.info(String.format("Registering instances %s with loadbalancer %s", instances, lbName));
//		RegisterInstancesWithLoadBalancerRequest.Builder regInstances = RegisterInstancesWithLoadBalancerRequest.builder();
//		regInstances.instances(instances);
//		regInstances.loadBalancerName(lbName);
//		RegisterInstancesWithLoadBalancerResponse result = elbClient.registerInstancesWithLoadBalancer(regInstances.build());
//
//		logger.info("ELB Add instance call result: " + result.toString());
//	}
//
//	public List<Instance> deregisterInstancesFromLB(List<Instance> toRemove, String loadBalancerName) {
//
//		DeregisterInstancesFromLoadBalancerRequest.Builder request= DeregisterInstancesFromLoadBalancerRequest.builder();
//		request.instances(toRemove);
//
//		request.loadBalancerName(loadBalancerName);
//		DeregisterInstancesFromLoadBalancerResponse result = elbClient.deregisterInstancesFromLoadBalancer(request.build());
//		List<Instance> remaining = result.instances();
//		logger.info(String.format("ELB %s now has %s instances registered", loadBalancerName, remaining.size()));
//		return remaining;
//	}

	public List<Tag> getTagsFor(String loadBalancerARNs) {
		DescribeTagsRequest describeTagsRequest = DescribeTagsRequest.builder().
				resourceArns(loadBalancerARNs).
				//loadBalancerNames(loadBalancerName).
				build();
		DescribeTagsResponse result = elbClient.describeTags(describeTagsRequest);
		List<TagDescription> descriptions = result.tagDescriptions();
		logger.info(String.format("Fetching %s tags for LB ARN %s ", descriptions.size(), loadBalancerARNs));
		return descriptions.get(0).tags();
	}

	public List<TargetGroup> describerTargetGroups() {
		DescribeTargetGroupsResponse result = elbClient.describeTargetGroups();
		List<TargetGroup> found = result.targetGroups();
		if (found.isEmpty()) {
			logger.warn("Found no target groups, response was " + result);
			return Collections.emptyList();
		}

		logger.info("Found " + found.size() + " target groups");
		return found;
	}

	public List<TargetDescription> describeTargets(TargetGroup targetGroup) {
		String targetGroupArn = targetGroup.targetGroupArn();
		String targetGroupName = targetGroup.targetGroupName();

		DescribeTargetHealthRequest request = DescribeTargetHealthRequest.builder().
				targetGroupArn(targetGroupArn)
				.build();
		DescribeTargetHealthResponse result = elbClient.describeTargetHealth(request);
		if (!result.hasTargetHealthDescriptions()) {
			logger.warn("No results for target health request for group " + targetGroupArn + " name " + targetGroupName);
			return Collections.emptyList();
		}

		List<TargetHealthDescription> descriptions = result.targetHealthDescriptions();
		List<TargetDescription> targets = descriptions.stream().map(TargetHealthDescription::target).toList();

		logger.info("Got " + targets.size() + " targets for " + targetGroupName);

		return targets;
	}

	public void deregisterInstance(TargetGroup targetGroup, String instanceId, int port) {

		// NOTE: needs id and port, just id on own silently fails to delete anything
		TargetDescription targets = TargetDescription.builder().
				id(instanceId).
				port(port).
				build();
		DeregisterTargetsRequest request = DeregisterTargetsRequest.builder().
				targetGroupArn(targetGroup.targetGroupArn()).
				targets(targets).
				build();
		logger.info("Request to delete " + instanceId + " port " + port + " from target group " + targetGroup);
		elbClient.deregisterTargets(request);
	}

	public void registerInstance(TargetGroup targetGroup, String instanceId, int instancePort) {
		logger.info("Register instance " + instanceId + " port " + instancePort + " into " + targetGroup);
		TargetDescription description = TargetDescription.builder().id(instanceId).port(instancePort).build();
		RegisterTargetsRequest request = RegisterTargetsRequest.builder().
				targets(description).
				targetGroupArn(targetGroup.targetGroupArn()).
				build();
		elbClient.registerTargets(request);
	}
}
