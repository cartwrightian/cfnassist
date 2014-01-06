package tw.com;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class VpcRepository {
	private static final Logger logger = LoggerFactory.getLogger(VpcRepository.class);
	private static AmazonEC2Client ec2Client;
	private HashMap<ProjectAndEnv, String> idCache;
	
	public VpcRepository(AWSCredentialsProvider credentialsProvider, Region region) {
		ec2Client = new AmazonEC2Client(credentialsProvider);
		ec2Client.setRegion(region);
		idCache = new HashMap<ProjectAndEnv, String>();
	}
	
	public Vpc getCopyOfVpc(String project, String env) {
		ProjectAndEnv key = new ProjectAndEnv(project,env);
		if (idCache.containsKey(key)) {
			String vpcId = idCache.get(key);
			logger.info(String.format("Cache hit for %s, found VPC ID %s", key, vpcId));		
			return getVpcById(vpcId);
		} else 
		{
			logger.info(String.format("Checking for TAGs %s:%s and %s:%s to find VPC", AwsFacade.PROJECT_TAG, project, AwsFacade.ENVIRONMENT_TAG, env));
			Vpc result = findVpcUsingProjectAndEnv(key);
			if (result==null) {	
				logger.error("Could not find VPC for " + key);
			} else {
				idCache.put(key, result.getVpcId());
			}
			return result;
		}	
	}

	private Vpc getVpcById(String vpcId) {
		logger.info("Get VPC by ID " + vpcId);
		DescribeVpcsRequest describeVpcsRequest = new DescribeVpcsRequest();
		Collection<String> vpcIds = new LinkedList<String>();
		vpcIds.add(vpcId);
		describeVpcsRequest.setVpcIds(vpcIds);
		DescribeVpcsResult results = ec2Client.describeVpcs(describeVpcsRequest);
		return results.getVpcs().get(0);
	}

	private Vpc findVpcUsingProjectAndEnv(ProjectAndEnv key) {
		DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
		List<Vpc> vpcs = describeVpcsResults.getVpcs();

		for(Vpc vpc : vpcs) {
			String vpcId = vpc.getVpcId();
			String possibleProject = getTagByName(vpc, AwsFacade.PROJECT_TAG);
			if (key.getProject().equals(possibleProject)) {	
				logger.debug(String.format("Found Possible VPC with %s:%s ID is %s", AwsFacade.PROJECT_TAG, possibleProject, vpcId));
				String possibleEnv = getTagByName(vpc, AwsFacade.ENVIRONMENT_TAG);
				logger.debug(String.format("Found Possible VPC with %s:%s ID is %s", AwsFacade.ENVIRONMENT_TAG, possibleEnv, vpcId));
				if (key.getEnv().equals(possibleEnv)) {
					logger.info("Matched tags, vpc id is " + vpcId);
					return vpc;
				}
			}
		}
		return null;
	}

	private String getTagByName(Vpc vpc, String tagName) {
		List<Tag> tags = vpc.getTags();
		for(Tag tag : tags) {	
			if (tag.getKey().equals(tagName)) {
				return tag.getValue();
			}
		}
		return null;
	}

	public void setVpcIndexTag(String project, String env, String value) {
		logger.info(String.format("Attempt to set %s tag to %s for %s and %s",AwsFacade.INDEX_TAG,value,project,env));
		Vpc vpc = getCopyOfVpc(project, env);
		
		List<Tag> tags = new LinkedList<Tag>();
		List<String> resources = new LinkedList<String>();
		
		Tag newTag = new Tag(AwsFacade.INDEX_TAG, value);
		tags.add(newTag);
		
		resources.add(vpc.getVpcId());
		CreateTagsRequest createTagsRequest = new CreateTagsRequest(resources , tags);
		
		ec2Client.createTags(createTagsRequest);
	}

	public String getVpcIndexTag(String project, String env) {
		Vpc vpc = getCopyOfVpc(project, env);
		return getTagByName(vpc, AwsFacade.INDEX_TAG);
	}

}
