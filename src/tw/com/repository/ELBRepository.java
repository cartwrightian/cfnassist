package tw.com.repository;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import software.amazon.awssdk.services.elasticloadbalancing.model.Tag;
import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.providers.LoadBalancerClassicClient;

import software.amazon.awssdk.services.ec2.model.Vpc;

public class ELBRepository {
	private static final Logger logger = LoggerFactory.getLogger(ELBRepository.class);

	private final LoadBalancerClassicClient classicClient;
	private final VpcRepository vpcRepository;
	private final ResourceRepository cfnRepository;
	
	public ELBRepository(LoadBalancerClassicClient elbClient, VpcRepository vpcRepository, ResourceRepository cfnRepository) {
		this.classicClient = elbClient;
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
	}

	public LoadBalancerDescription findELBFor(ProjectAndEnv projAndEnv, String type) throws TooManyELBException {
		String vpcID = getVpcId(projAndEnv);
		List<LoadBalancerDescription> foundELBForVPC = new LinkedList<>();
		
		logger.info(String.format("Searching for load balancers for %s (will use tag %s:%s if >1 found)", vpcID, AwsFacade.TYPE_TAG,type));
		List<LoadBalancerDescription> elbs = classicClient.describeLoadBalancers();
		for (LoadBalancerDescription elb : elbs) {
			String dnsName = elb.dnsName();
			logger.debug("Found an ELB: " + dnsName);
			String possible = elb.vpcId();
			if (possible!=null) {
				if (possible.equals(vpcID)) {
					logger.info(String.format("Matched ELB %s for VPC: %s", dnsName, vpcID));
					foundELBForVPC.add(elb);
				} else {
					logger.info(String.format("Not matched ELB %s as VPC id %s does not match: %s", dnsName, possible, vpcID));
				}
			} else {
				logger.debug("No VPC ID for ELB " + dnsName);
			}
		}
		if (foundELBForVPC.size()>1) {
			return checkForMatchingTag(foundELBForVPC, vpcID, type);
		}
		if (foundELBForVPC.size()==1) {
			return foundELBForVPC.get(0);
		}
		logger.error("No matching ELB found for " + projAndEnv);
		return null; // ugly but preserves current api
	}

	private LoadBalancerDescription checkForMatchingTag(
			List<LoadBalancerDescription> descriptions, String vpcID, String type) throws TooManyELBException {
		List<LoadBalancerDescription> found = new LinkedList<>();
		
		for(LoadBalancerDescription desc : descriptions) {
			String loadBalancerName = desc.loadBalancerName();
			logger.info(String.format("Checking LB for tag %s:%s, ELB name is %s", AwsFacade.TYPE_TAG, type, loadBalancerName));
			List<Tag> tags = classicClient.getTagsFor(loadBalancerName);
			if (containsCorrectTag(tags, type)) {
				logger.info("LB matched " + loadBalancerName);
				found.add(desc);
			}
		}
		if (found.size()==1) {
			return found.get(0);
		}
	
		throw new TooManyELBException(found.size(), String.format("Found too many elbs for vpc (%s) that matched tag %s",
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

	private List<Instance> addInstancesThatMatchBuildAndType(ProjectAndEnv projAndEnv, String typeTag) throws CfnAssistException {
		if (!projAndEnv.hasBuildNumber()) {
			throw new MustHaveBuildNumber();
		}
		LoadBalancerDescription elb = findELBFor(projAndEnv, typeTag);	
		List<Instance> currentInstances = classicClient.getInstancesFor(elb);
		String lbName = elb.loadBalancerName();
		
		SearchCriteria criteria = new SearchCriteria(projAndEnv);
		List<Instance> allMatchingInstances = cfnRepository.getAllInstancesMatchingType(criteria, typeTag);
		List<Instance> instancesToAdd = filterBy(currentInstances, allMatchingInstances);
		if (allMatchingInstances.size()==0) {
			logger.warn(String.format("No instances matched %s and type tag %s (%s)", projAndEnv, typeTag, AwsFacade.TYPE_TAG));
		} else {	
			logger.info(String.format("Register matching %s instances with the LB %s ", instancesToAdd.size(),lbName));
			classicClient.registerInstances(instancesToAdd, lbName);
		}
		return instancesToAdd;
	}

	private List<Instance> filterBy(List<Instance> currentInstances, List<Instance> allMatchingInstances) {
		List<Instance> result = new LinkedList<>();
		for(Instance candidate : allMatchingInstances) {
			if (!currentInstances.contains(candidate)) {
				result.add(candidate);
			}
		}
		return result;
	}

	public List<Instance> findInstancesAssociatedWithLB(
			ProjectAndEnv projAndEnv, String typeTag) throws TooManyELBException {
		LoadBalancerDescription elb = findELBFor(projAndEnv, typeTag);
		return classicClient.getInstancesFor(elb);
	}

	// returns remaining instances
	private List<Instance> removeInstancesNotMatching(ProjectAndEnv projAndEnv, List<Instance> matchingInstances, String typeTag) throws TooManyELBException {
		LoadBalancerDescription elb = findELBFor(projAndEnv, typeTag);
		logger.info("Checking if instances should be removed from ELB " + elb.loadBalancerName());
		List<Instance> currentInstances = classicClient.getInstancesFor(elb);
				
		List<Instance> toRemove = new LinkedList<>();
		for(Instance current : currentInstances) {
			String instanceId = current.instanceId();
			if (matchingInstances.contains(current)) {
				logger.info("Instance matched project/env/build/type, will not be removed " + instanceId);
			} else {
				logger.info("Instance did not match, will be removed from ELB " +instanceId);
				toRemove.add(Instance.builder().instanceId(instanceId).build());
			}
		}
		
		if (toRemove.isEmpty()) {
			logger.info("No instances to remove from ELB " + elb.loadBalancerName());
			return new LinkedList<>();
		}
		return removeInstances(elb,toRemove);	
	}

	private List<Instance> removeInstances(LoadBalancerDescription elb,
										   List<Instance> toRemove) {
		String loadBalancerName = elb.loadBalancerName();
		logger.info("Removing instances from ELB " + loadBalancerName);

		return classicClient.deregisterInstancesFromLB(toRemove,loadBalancerName);

	}

	public List<Instance> updateInstancesMatchingBuild(ProjectAndEnv projAndEnv, String typeTag) throws CfnAssistException {
		List<Instance> matchinginstances = addInstancesThatMatchBuildAndType(projAndEnv, typeTag); 
		return removeInstancesNotMatching(projAndEnv, matchinginstances, typeTag);	
	}

	// TODO filter on the request, but api does not seeem to support that currently
	public List<LoadBalancerDescription> findELBForVPC(String vpcId) {
		List<LoadBalancerDescription> result = classicClient.describeLoadBalancers(); // seems to be no filter for vpc on elbs
		
		List<LoadBalancerDescription> filtered = new LinkedList<>();
		for(LoadBalancerDescription lb : result) {
			if (lb.vpcId()!=null) {
				if (lb.vpcId().equals(vpcId)) {
					filtered.add(lb);
				}
			}
		}
		return filtered;
	}
}
