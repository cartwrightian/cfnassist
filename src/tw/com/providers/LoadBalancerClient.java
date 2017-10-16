package tw.com.providers;

import java.util.List;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.DescribeTagsRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeTagsResult;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.Tag;
import com.amazonaws.services.elasticloadbalancing.model.TagDescription;

public class LoadBalancerClient {
	private static final Logger logger = LoggerFactory.getLogger(LoadBalancerClient.class);

	private AmazonElasticLoadBalancing elbClient;

	public LoadBalancerClient(AmazonElasticLoadBalancing elbClient) {
		this.elbClient = elbClient;
	}

	public List<LoadBalancerDescription> describeLoadBalancers() {
		DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest();
		DescribeLoadBalancersResult result = elbClient.describeLoadBalancers(request);
		List<LoadBalancerDescription> descriptions = result.getLoadBalancerDescriptions();
		logger.info(String.format("Found %s load balancers %s", descriptions.size(), descriptions));
		return descriptions;
	}

	public void registerInstances(List<Instance> instances, String lbName) {
		logger.info(String.format("Registering instances %s with loadbalancer %s", instances, lbName));
		RegisterInstancesWithLoadBalancerRequest regInstances = new RegisterInstancesWithLoadBalancerRequest();
		regInstances.setInstances(instances);
		regInstances.setLoadBalancerName(lbName);
		RegisterInstancesWithLoadBalancerResult result = elbClient.registerInstancesWithLoadBalancer(regInstances);
		
		logger.info("ELB Add instance call result: " + result.toString());	
	}

	public List<Instance> degisterInstancesFromLB(List<Instance> toRemove,
			String loadBalancerName) {
		
		DeregisterInstancesFromLoadBalancerRequest request= new DeregisterInstancesFromLoadBalancerRequest();
		request.setInstances(toRemove);
		
		request.setLoadBalancerName(loadBalancerName);
		DeregisterInstancesFromLoadBalancerResult result = elbClient.deregisterInstancesFromLoadBalancer(request);
		List<Instance> remaining = result.getInstances();
		logger.info(String.format("ELB %s now has %s instances registered", loadBalancerName, remaining.size()));
		return remaining;
	}

	public List<Tag> getTagsFor(String loadBalancerName) {
		DescribeTagsRequest describeTagsRequest = new DescribeTagsRequest().withLoadBalancerNames(loadBalancerName);
		DescribeTagsResult result = elbClient.describeTags(describeTagsRequest);
		List<TagDescription> descriptions = result.getTagDescriptions();
		logger.info(String.format("Fetching %s tags for LB %s ", descriptions.size(), loadBalancerName));
		return descriptions.get(0).getTags();
	}

}
