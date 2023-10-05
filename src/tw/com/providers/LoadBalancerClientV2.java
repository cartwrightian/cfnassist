package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;
import tw.com.exceptions.CfnAssistException;

import java.util.Collection;
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

	public List<Tag> getTagsFor(TargetGroup targetGroup) {
		DescribeTagsRequest describeTagsRequest = DescribeTagsRequest.builder().
				resourceArns(targetGroup.targetGroupArn()).
				build();
		DescribeTagsResponse result = elbClient.describeTags(describeTagsRequest);
		List<TagDescription> descriptions = result.tagDescriptions();
		logger.info(String.format("Fetching %s tags for LB ARN %s ", descriptions.size(), targetGroup));
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

	public void registerInstances(TargetGroup targetGroup, Collection<String> instancesToAdd, int port) {
		logger.info("Register " + instancesToAdd.size() + " instances for " + targetGroup + " on port " + port);
		instancesToAdd.forEach(instanceId -> registerInstance(targetGroup, instanceId, port));
	}

	public Set<String> deregisterInstances(TargetGroup targetGroup, Collection<String> toRemoveIds, int port) throws CfnAssistException {
		logger.info("Deregister " + toRemoveIds.size() + " instances for " + targetGroup + " on port " + port);
		toRemoveIds.forEach(instanceId -> registerInstance(targetGroup, instanceId, port));

		// returns remaining instances
		return getInstancesFor(targetGroup);
	}

	public Set<String> getInstancesFor(TargetGroup targetGroup) throws CfnAssistException {
		guardForInstanceTargetType(targetGroup);
		List<TargetDescription> targets = this.describeTargets(targetGroup);
		return targets.stream().map(TargetDescription::id).collect(Collectors.toSet());
	}

	private void guardForInstanceTargetType(TargetGroup targetGroup) throws CfnAssistException {
		if (targetGroup.targetType()!=TargetTypeEnum.INSTANCE) {
			throw new CfnAssistException("Unsupported target type, need instance, got " + targetGroup);
		}
	}
}
