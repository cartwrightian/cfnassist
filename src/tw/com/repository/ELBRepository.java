package tw.com.repository;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.providers.LoadBalancerClient;

import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.Tag;

public class ELBRepository {
	private static final Logger logger = LoggerFactory.getLogger(ELBRepository.class);
	LoadBalancerClient elbClient;
	VpcRepository vpcRepository;
	ResourceRepository cfnRepository;
	
	public ELBRepository(LoadBalancerClient elbClient, VpcRepository vpcRepository, ResourceRepository cfnRepository) {
		this.elbClient = elbClient;
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
	}

	public LoadBalancerDescription findELBFor(ProjectAndEnv projAndEnv, String type) throws TooManyELBException {
		String vpcID = getVpcId(projAndEnv);
		List<LoadBalancerDescription> foundELBForVPC = new LinkedList<LoadBalancerDescription>();
		
		logger.info(String.format("Searching for load balancers for %s (will use tag %s:%s if >1 found)", vpcID, AwsFacade.TYPE_TAG,type));
		List<LoadBalancerDescription> elbs = elbClient.describeLoadBalancers();
		for (LoadBalancerDescription elb : elbs) {
			String dnsName = elb.getDNSName();
			logger.debug("Found an ELB: " + dnsName);
			String possible = elb.getVPCId();
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
		List<LoadBalancerDescription> found = new LinkedList<LoadBalancerDescription>();
		
		for(LoadBalancerDescription desc : descriptions) {
			String loadBalancerName = desc.getLoadBalancerName();
			logger.info(String.format("Checking LB for tag %s:%s, ELB name is %s", AwsFacade.TYPE_TAG, type, loadBalancerName));
			List<Tag> tags = elbClient.getTagsFor(loadBalancerName);
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
			if (tag.getKey().equals(AwsFacade.TYPE_TAG)) {
				return tag.getValue().equals(type);
			}
		}
		return false;
	}

	private String getVpcId(ProjectAndEnv projAndEnv) {
		Vpc vpc = vpcRepository.getCopyOfVpc(projAndEnv);
		String vpcID = vpc.getVpcId();
		return vpcID;
	}

	private List<Instance> addInstancesThatMatchBuildAndType(ProjectAndEnv projAndEnv, String typeTag) throws MustHaveBuildNumber, WrongNumberOfInstancesException, TooManyELBException {
		if (!projAndEnv.hasBuildNumber()) {
			throw new MustHaveBuildNumber();
		}
		LoadBalancerDescription elb = findELBFor(projAndEnv, typeTag);	
		List<Instance> currentInstances = elb.getInstances();
		String lbName = elb.getLoadBalancerName();
		List<Instance> allMatchingInstances = cfnRepository.getAllInstancesMatchingType(projAndEnv, typeTag);
		List<Instance> instancesToAdd = filterBy(currentInstances, allMatchingInstances);
		if (allMatchingInstances.size()==0) {
			logger.warn(String.format("No instances matched %s and type tag %s (%s)", projAndEnv, typeTag, AwsFacade.TYPE_TAG));
		} else {	
			logger.info(String.format("Regsister matching %s instances with the LB %s ", instancesToAdd.size(),lbName));
			elbClient.registerInstances(instancesToAdd, lbName);	
		}
		return instancesToAdd;
	}

	private List<Instance> filterBy(List<Instance> currentInstances,
			List<Instance> allMatchingInstances) {
		List<Instance> result = new LinkedList<Instance>();
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
		return elb.getInstances();
	}

	// returns remaining instances
	private List<Instance> removeInstancesNotMatching(ProjectAndEnv projAndEnv, List<Instance> matchingInstances, String typeTag) throws MustHaveBuildNumber, TooManyELBException {	
		LoadBalancerDescription elb = findELBFor(projAndEnv, typeTag);
		logger.info("Checking if instances should be removed from ELB " + elb.getLoadBalancerName());
		List<Instance> currentInstances = elb.getInstances();	
				
		List<Instance> toRemove = new LinkedList<Instance>();
		for(Instance current : currentInstances) {
			String instanceId = current.getInstanceId();
			if (matchingInstances.contains(current)) {
				logger.info("Instance matched project/env/build/type, will not be removed " + instanceId);
			} else {
				logger.info("Instance did not match, will be removed from ELB " +instanceId);
				toRemove.add(new Instance(instanceId));
			}
		}
		
		if (toRemove.isEmpty()) {
			logger.info("No instances to remove from ELB " + elb.getLoadBalancerName());
			return new LinkedList<Instance>();
		}
		return removeInstances(elb,toRemove);	
	}

	private List<Instance> removeInstances(LoadBalancerDescription elb,
			List<Instance> toRemove) {
		String loadBalancerName = elb.getLoadBalancerName();
		logger.info("Removing instances from ELB " + loadBalancerName);

		return elbClient.degisterInstancesFromLB(toRemove,loadBalancerName);

	}

	public List<Instance> updateInstancesMatchingBuild(ProjectAndEnv projAndEnv, String typeTag) throws MustHaveBuildNumber, WrongNumberOfInstancesException, TooManyELBException {
		List<Instance> matchinginstances = addInstancesThatMatchBuildAndType(projAndEnv, typeTag); 
		return removeInstancesNotMatching(projAndEnv, matchinginstances, typeTag);	
	}
}
