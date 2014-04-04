package tw.com;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.MustHaveBuildNumber;

import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerResult;

public class ELBRepository {
	private static final Logger logger = LoggerFactory.getLogger(ELBRepository.class);
	AmazonElasticLoadBalancingClient elbClient;
	VpcRepository vpcRepository;
	CfnRepository cfnRepository;
	
	public ELBRepository(AmazonElasticLoadBalancingClient elbClient, VpcRepository vpcRepository, CfnRepository cfnRepository) {
		this.elbClient = elbClient;
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
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

	public void updateELBInstancesThatMatchBuild(ProjectAndEnv projAndEnv) throws MustHaveBuildNumber {
		if (!projAndEnv.hasBuildNumber()) {
			throw new MustHaveBuildNumber();
		}
		LoadBalancerDescription elb = findELBFor(projAndEnv);
		
		Collection<String> instancesIds = cfnRepository.getInstancesFor(projAndEnv);
	
		List<Instance> instances = new LinkedList<Instance>();
		for (String id : instancesIds) {
			instances.add(new Instance(id));
		}
		
		String lbName = elb.getLoadBalancerName();
		logger.info("Regsister matching instances with the LB " + lbName);
		RegisterInstancesWithLoadBalancerRequest regInstances = new RegisterInstancesWithLoadBalancerRequest();
		regInstances.setInstances(instances);
		regInstances.setLoadBalancerName(lbName);
		RegisterInstancesWithLoadBalancerResult result = elbClient.registerInstancesWithLoadBalancer(regInstances);
		
		logger.info("Call result: " + result.toString());
		
	}

}
