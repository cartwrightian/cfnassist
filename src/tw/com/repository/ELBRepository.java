package tw.com.repository;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.exceptions.MustHaveBuildNumber;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerResult;

public class ELBRepository {
	private static final Logger logger = LoggerFactory.getLogger(ELBRepository.class);
	AmazonElasticLoadBalancingClient elbClient;
	AmazonEC2Client ec2Client;
	VpcRepository vpcRepository;
	ResourceRepository cfnRepository;
	
	public ELBRepository(AmazonElasticLoadBalancingClient elbClient, AmazonEC2Client ec2Client, VpcRepository vpcRepository, ResourceRepository cfnRepository) {
		this.elbClient = elbClient;
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
		this.ec2Client = ec2Client;
	}

	public LoadBalancerDescription findELBFor(ProjectAndEnv projAndEnv) {
		String vpcID = getVpcId(projAndEnv);
		
		logger.info("Searching for load balancers for " + vpcID);
		DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest();
		DescribeLoadBalancersResult result = elbClient.describeLoadBalancers(request);
		List<LoadBalancerDescription> elbs = result.getLoadBalancerDescriptions();
		for (LoadBalancerDescription elb : elbs) {
			String dnsName = elb.getDNSName();
			logger.debug("Found an ELB: " + dnsName);
			String possible = elb.getVPCId();
			if (possible!=null) {
				if (possible.equals(vpcID)) {
					logger.info(String.format("Matched ELB %s for VPC: %s", dnsName, vpcID));
					return elb;
				} 
			} else {
				logger.debug("No VPC ID for ELB " + dnsName);
			}
		}
		return null;
	}

	private String getVpcId(ProjectAndEnv projAndEnv) {
		Vpc vpc = vpcRepository.getCopyOfVpc(projAndEnv);
		String vpcID = vpc.getVpcId();
		return vpcID;
	}

	private List<Instance> addInstancesThatMatchBuildAndType(ProjectAndEnv projAndEnv, String typeTag) throws MustHaveBuildNumber, WrongNumberOfInstancesException {
		if (!projAndEnv.hasBuildNumber()) {
			throw new MustHaveBuildNumber();
		}
		LoadBalancerDescription elb = findELBFor(projAndEnv);	
		List<Instance> instances = getMatchingInstances(projAndEnv, typeTag);
		if (instances.size()==0) {
			logger.warn(String.format("No instances matched %s and type tag %s (%s)", projAndEnv, typeTag, AwsFacade.TYPE_TAG));
		} else {	
			String lbName = elb.getLoadBalancerName();
			logger.info(String.format("Regsister matching %s instances with the LB %s ",instances.size(),lbName));
			RegisterInstancesWithLoadBalancerRequest regInstances = new RegisterInstancesWithLoadBalancerRequest();
			regInstances.setInstances(instances);
			regInstances.setLoadBalancerName(lbName);
			RegisterInstancesWithLoadBalancerResult result = elbClient.registerInstancesWithLoadBalancer(regInstances);
			
			logger.info("ELB Add instance call result: " + result.toString());
		}
		return instances;
	}

	private List<Instance> getMatchingInstances(ProjectAndEnv projAndEnv,
			String type) throws WrongNumberOfInstancesException {
		Collection<String> instancesIds = cfnRepository.getInstancesFor(projAndEnv);
	
		List<Instance> instances = new LinkedList<Instance>();
		for (String id : instancesIds) {
			if (instanceHasCorrectType(type, id)) {
				logger.info(String.format("Adding instance %s as it matched %s %s",id, AwsFacade.TYPE_TAG, type));
				instances.add(new Instance(id));
			} else {
				logger.info(String.format("Not adding instance %s as did not match %s %s",id, AwsFacade.TYPE_TAG, type));
			}	
		}
		logger.info(String.format("Found %s instances matching %s and type: %s", instances.size(), projAndEnv, type));
		return instances;
	}

	// returns remaining instances
	private List<Instance> removeInstancesNotMatching(ProjectAndEnv projAndEnv, List<Instance> matchingInstances) throws MustHaveBuildNumber {	
		LoadBalancerDescription elb = findELBFor(projAndEnv);
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
		
		DeregisterInstancesFromLoadBalancerRequest request= new DeregisterInstancesFromLoadBalancerRequest();
		request.setInstances(toRemove);
		
		request.setLoadBalancerName(loadBalancerName);
		DeregisterInstancesFromLoadBalancerResult result = elbClient.deregisterInstancesFromLoadBalancer(request);
		List<Instance> remaining = result.getInstances();
		logger.info(String.format("ELB %s now has %s instances registered", loadBalancerName, remaining.size()));
		return remaining;
	}
	
	private boolean instanceHasCorrectType(String type, String id) throws WrongNumberOfInstancesException {
		List<Tag> tags = getTagsForInstance(id);
		for(Tag tag : tags) {
			if (tag.getKey().equals(AwsFacade.TYPE_TAG)) {
				return tag.getValue().equals(type);
			}
		}
		return false;
	}

	private List<Tag> getTagsForInstance(String id)
			throws WrongNumberOfInstancesException {
		DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(id);
		DescribeInstancesResult result = ec2Client.describeInstances(request);
		List<Reservation> res = result.getReservations();
		if (res.size()!=1) {
			throw new WrongNumberOfInstancesException(id, res.size());
		}
		List<com.amazonaws.services.ec2.model.Instance> ins = res.get(0).getInstances();
		if (ins.size()!=1) {
			throw new WrongNumberOfInstancesException(id, ins.size());
		}
		com.amazonaws.services.ec2.model.Instance instance = ins.get(0);
		List<Tag> tags = instance.getTags();
		return tags;
	}

	public List<Instance> updateInstancesMatchingBuild(ProjectAndEnv projAndEnv, String type) throws MustHaveBuildNumber, WrongNumberOfInstancesException {
		List<Instance> matchinginstances = addInstancesThatMatchBuildAndType(projAndEnv, type); 
		return removeInstancesNotMatching(projAndEnv, matchinginstances);	
	}

}
