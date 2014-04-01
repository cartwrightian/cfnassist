package tw.com;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

public class ELBRepository {
	private static final Logger logger = LoggerFactory.getLogger(ELBRepository.class);
	AmazonElasticLoadBalancingClient elbClient;
	
	public ELBRepository(AmazonElasticLoadBalancingClient elbClient) {
		this.elbClient = elbClient;
	}

	public LoadBalancerDescription findELBFor(String vpcID) {
		logger.info("Searching for load balancers for " + vpcID);
		DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest();
		DescribeLoadBalancersResult result = elbClient.describeLoadBalancers(request);
		List<LoadBalancerDescription> elbs = result.getLoadBalancerDescriptions();
		for (LoadBalancerDescription elb : elbs) {
			logger.debug("Found an ELB: " + elb.getDNSName());
			String possible = elb.getVPCId();
			if (possible!=null) {
				if (possible.equals(vpcID)) {
					logger.info("Matched ELB for VPC: " + vpcID);
					return elb;
				} 
			} else {
				logger.debug("No VPC ID for ELB");
			}
		}
		return null;
	}

}
