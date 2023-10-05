package tw.com.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ec2.model.Vpc;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Tag;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;
import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.exceptions.TooManyELBException;
import tw.com.providers.LoadBalancerClientV2;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class TargetGroupRepository {
	private static final Logger logger = LoggerFactory.getLogger(TargetGroupRepository.class);

	private final LoadBalancerClientV2 loadBalancerClient;
	private final VpcRepository vpcRepository;
	private final ResourceRepository cfnRepository;

	public TargetGroupRepository(LoadBalancerClientV2 elbClient, VpcRepository vpcRepository, ResourceRepository cfnRepository) {
		this.loadBalancerClient = elbClient;
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
	}

	public TargetGroup findTargetGroupFor(ProjectAndEnv projAndEnv, String type) throws TooManyELBException {
		String vpcID = getVpcId(projAndEnv);
		List<TargetGroup> foundForVPC = new LinkedList<>();
		
		logger.info(format("Searching for load balancers for %s (will use tag %s:%s if >1 found)", vpcID, AwsFacade.TYPE_TAG,type));
		List<TargetGroup> loadBalancers = loadBalancerClient.describerTargetGroups();
		for (TargetGroup targetGroup : loadBalancers) {
			String diagName = "Name:" + targetGroup.targetGroupName() + " ARN:"+targetGroup.targetGroupArn();
			logger.debug("Found an ELB: " + diagName);
			String possible = targetGroup.vpcId();
			if (possible!=null) {
				if (possible.equals(vpcID)) {
					logger.info(format("Matched ELB %s for VPC: %s", diagName, vpcID));
					foundForVPC.add(targetGroup);
				} else {
					logger.info(format("Not matched ELB %s as VPC id %s does not match: %s", diagName, possible, vpcID));
				}
			} else {
				logger.debug("No VPC ID for ELB " + diagName);
			}
		}
		if (foundForVPC.size()>1) {
			return checkForMatchingTag(foundForVPC, vpcID, type);
		}
		if (foundForVPC.size()==1) {
			return foundForVPC.get(0);
		}
		logger.error("No matching ELB found for " + projAndEnv);
		return null; // ugly but preserves current api
	}

	private TargetGroup checkForMatchingTag(List<TargetGroup> descriptions, String vpcID, String type) throws TooManyELBException {
		List<TargetGroup> found = new LinkedList<>();
		
		for(TargetGroup targetGroup : descriptions) {
			String targetGroupName = targetGroup.targetGroupName();
			logger.info(format("Checking LB for tag %s:%s, ELB name is %s", AwsFacade.TYPE_TAG, type, targetGroupName));
			List<software.amazon.awssdk.services.elasticloadbalancingv2.model.Tag> tags = loadBalancerClient.getTagsFor(targetGroup);
			if (containsCorrectTag(tags, type)) {
				logger.info("LB matched " + targetGroupName);
				found.add(targetGroup);
			}
		}
		if (found.size()==1) {
			return found.get(0);
		}
	
		throw new TooManyELBException(found.size(), format("Found too many elbs for vpc (%s) that matched tag %s",
				vpcID,
				AwsFacade.TYPE_TAG));
	}

	private boolean containsCorrectTag(List<Tag> tags, String type) {
		for(Tag tag : tags) {
			if (tag.key().equals(AwsFacade.TYPE_TAG)) {
				return tag.value().equals(type);
			}
		}
		return false;
	}

	private String getVpcId(ProjectAndEnv projAndEnv) {
		Vpc vpc = vpcRepository.getCopyOfVpc(projAndEnv);
		return vpc.vpcId();
	}

	private Set<String> addInstancesThatMatchBuildAndType(ProjectAndEnv projAndEnv, String typeTag, int port) throws CfnAssistException {
		if (!projAndEnv.hasBuildNumber()) {
			throw new MustHaveBuildNumber();
		}
		TargetGroup targetGroup = findTargetGroupFor(projAndEnv, typeTag);
		if (targetGroup==null) {
			String msg = "Found no target group for " + projAndEnv + " and tag " + typeTag;
			logger.warn(msg);
			throw new CfnAssistException(msg);
		}
		Set<String> currentInstances = loadBalancerClient.getInstancesFor(targetGroup);

		SearchCriteria criteria = new SearchCriteria(projAndEnv);
		List<Instance> allMatchingInstances = cfnRepository.getAllInstancesMatchingType(criteria, typeTag);
		Set<String> allMatchingInstanceIds = allMatchingInstances.stream().map(Instance::instanceId).collect(Collectors.toSet());
		Set<String> instancesToAdd = filterBy(currentInstances, allMatchingInstanceIds);
		if (allMatchingInstances.isEmpty()) {
			logger.warn(format("%s No instances matched %s and type tag %s (%s)", targetGroup, projAndEnv, typeTag, AwsFacade.TYPE_TAG));
		} else {	
			logger.info(format("Register matching %s instances with the targetgroup %s ", instancesToAdd.size(), targetGroup));
			loadBalancerClient.registerInstances(targetGroup, instancesToAdd, port);
		}
		return instancesToAdd;
	}

	private Set<String> filterBy(Set<String> currentInstances, Set<String> allMatchingInstances) {
		Set<String> result = new HashSet<>();
		for(String candidate : allMatchingInstances) {
			if (!currentInstances.contains(candidate)) {
				result.add(candidate);
			}
		}
		return result;
	}

	public Set<String> findInstancesAssociatedWithTargetGroup(ProjectAndEnv projAndEnv, String typeTag) throws CfnAssistException {
		TargetGroup targetGroup = findTargetGroupFor(projAndEnv, typeTag);
		return loadBalancerClient.getInstancesFor(targetGroup);
	}

	// returns remaining instances
	private Set<String> removeInstancesNotMatching(ProjectAndEnv projAndEnv, Set<String> matchingInstancesIds, String typeTag,
												   int port) throws CfnAssistException {
		TargetGroup targetGroup = findTargetGroupFor(projAndEnv, typeTag);
		logger.info("Checking if instances should be removed from target group " + targetGroup);
		Set<String> currentInstances = loadBalancerClient.getInstancesFor(targetGroup);

		Set<String> toRemove = new HashSet<>();
		for(String instanceId : currentInstances) {
			if (matchingInstancesIds.contains(instanceId)) {
				logger.info(format("Instance %s matched %s, will not be removed from %s ", instanceId, projAndEnv, targetGroup));
			} else {
				logger.info(format("Instance %s did not match %s, will be removed from %s ", instanceId, projAndEnv, targetGroup));
				toRemove.add(instanceId);
			}
		}
		
		if (toRemove.isEmpty()) {
			logger.info("No instances to remove from ELB " + targetGroup);
			return Collections.emptySet();
		}
		return removeInstances(targetGroup, toRemove, port);
	}

	private Set<String> removeInstances(TargetGroup targetGroup, Collection<String> toRemove, int port) throws CfnAssistException {
		logger.info("Removing instances from " + targetGroup);

		return loadBalancerClient.deregisterInstances(targetGroup, toRemove, port);

	}

	public Set<String> updateInstancesMatchingBuild(ProjectAndEnv projAndEnv, String typeTag, int port) throws CfnAssistException {
		Set<String> matchinginstances = addInstancesThatMatchBuildAndType(projAndEnv, typeTag, port);
		return removeInstancesNotMatching(projAndEnv, matchinginstances, typeTag, port);
	}

	// TODO filter on the request, but api does not seeem to support that currently
	public List<TargetGroup> findELBForVPC(String vpcId) {
		List<TargetGroup> result = loadBalancerClient.describerTargetGroups(); // seems to be no filter for vpc on elbs
		
		List<TargetGroup> filtered = new LinkedList<>();
		for(TargetGroup targetGroup : result) {
			if (targetGroup.vpcId()!=null) {
				if (targetGroup.vpcId().equals(vpcId)) {
					filtered.add(targetGroup);
				}
			}
		}
		return filtered;
	}
}
