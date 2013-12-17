package tw.com;

import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class CfnRepository {
	private static final Logger logger = LoggerFactory.getLogger(CfnRepository.class);
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 200;
	private AmazonCloudFormationClient cfnClient;
	
	private StackResources stackResources;
	List<Stack> stacks;
	
	public CfnRepository(AmazonCloudFormationClient cfnClient) {
		this.cfnClient = cfnClient;
		stackResources = new StackResources();
		stacks = new LinkedList<Stack>();
	}
	
	public String findPhysicalIdByLogicalId(String vpcId, String logicalId) {
		// we do need to disambiguate via the vpcID since we will not know the name of the stack where the resource was originally created
		// TODO
		
		logger.info(String.format("Looking for resource matching logicalID: %s for VPC: %s", logicalId, vpcId));
		List<Stack> stacks = getStacks();
		for(Stack stack : stacks) {
			String stackName = stack.getStackName();
			String maybeHaveId = findPhysicalIdByLogicalId(vpcId, stackName, logicalId);
			if (maybeHaveId!=null) {
				logger.info(String.format("Found physicalID: %s for logical ID: %s", maybeHaveId, logicalId));
				return maybeHaveId;
			}
		}
		logger.warn("No match for logical ID was found");
		return null;
	}
	
	private String findPhysicalIdByLogicalId(String vpcId, String stackName, String logicalId) {	
		logger.info(String.format("Check stack %s for logical ID %s", stackName, logicalId));

		List<StackResource> resources = getResourcesForStack(vpcId, stackName);
		for (StackResource resource : resources) {
			String candidateId = resource.getLogicalResourceId();
			if (candidateId.equals(logicalId)) {
				return resource.getPhysicalResourceId();
			}
		}
		return null;		
	}
	
	private List<StackResource> getResourcesForStack( String vpcId, String stackName) {
		

		List<StackResource> resources=null;
		if (stackResources.containsStack(stackName)) {
			logger.info("Cache hit on stack resources for stack " + stackName);
			resources = stackResources.getStackResources(stackName);
		} else {
			logger.info("Cache miss, loading resources for stack " + stackName);
			DescribeStackResourcesRequest request = new DescribeStackResourcesRequest();
			request.setStackName(stackName);
			DescribeStackResourcesResult results = cfnClient.describeStackResources(request);
			
			resources = results.getStackResources();
			stackResources.addStackResources(stackName, resources);
		}
		return resources;
	}
	
	private List<Stack> getStacks() {
		// TODO handle "next token"??
		if (stacks.size()==0) {
			logger.info("No cached stacks, loading all stacks");
			DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
			DescribeStacksResult results = cfnClient.describeStacks(describeStackRequest);
			stacks = results.getStacks();	
			logger.info(String.format("Loaded %s stacks", stacks.size()));
		} else {
			logger.info("Cache hit on stacks");
		}
		return stacks;
	}
	
	public void updateRepositoryFor(String stackName) {
		logger.info("Update stack repository for stack: " + stackName);
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		DescribeStacksResult results = cfnClient.describeStacks();
		stacks.addAll(results.getStacks());	
	}
	
	public String waitForStatusToChange(String stackName, StackStatus inProgressStatus) 
			throws WrongNumberOfStacksException, InterruptedException {
		DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
		describeStacksRequest.setStackName(stackName);
		
		String status = inProgressStatus.toString();
		while (status.equals(inProgressStatus.toString())) {
			Thread.sleep(STATUS_CHECK_INTERVAL_MILLIS);
			DescribeStacksResult result = cfnClient.describeStacks(describeStacksRequest);
			List<Stack> stacks = result.getStacks();
			if (stacks.size()!=1) {
				throw new WrongNumberOfStacksException(1, stacks.size());
			}
			status = stacks.get(0).getStackStatus();			
		}
		return status;
	}

}
