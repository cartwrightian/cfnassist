package tw.com;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.CannotFindVpcException;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DeleteTagsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class VpcRepository {
	private static final Logger logger = LoggerFactory.getLogger(VpcRepository.class);
	private AmazonEC2Client ec2Client;
	private HashMap<ProjectAndEnv, String> idCache; // used to avoid search by tag unless needed
	
	public VpcRepository(AmazonEC2Client ec2Client) {
		this.ec2Client = ec2Client;
		idCache = new HashMap<ProjectAndEnv, String>();
	}
	
	public Vpc getCopyOfVpc(ProjectAndEnv projectAndEnv) {
		if (idCache.containsKey(projectAndEnv)) {
			String vpcId = idCache.get(projectAndEnv);
			logger.info(String.format("Cache hit for %s, found VPC ID %s", projectAndEnv, vpcId));		
			return getVpcById(vpcId);
		} else 
		{
			logger.info(String.format("Checking for TAGs %s:%s and %s:%s to find VPC", AwsFacade.PROJECT_TAG, 
					projectAndEnv.getProject(), AwsFacade.ENVIRONMENT_TAG, projectAndEnv.getEnv()));
			Vpc result = findVpcUsingProjectAndEnv(projectAndEnv);
			if (result==null) {	
				logger.error("Could not find VPC for " + projectAndEnv);
			} else {
				idCache.put(projectAndEnv, result.getVpcId());
			}
			return result;
		}	
	}

	public Vpc getCopyOfVpc(String vpcId) {
		return getVpcById(vpcId);
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

	public void setVpcIndexTag(ProjectAndEnv projAndEnv, String value) throws CannotFindVpcException {
		logger.info(String.format("Attempt to set %s tag to %s for%s",AwsFacade.INDEX_TAG, value, projAndEnv));
		Vpc vpc = getCopyOfVpc(projAndEnv);
		
		if (vpc==null) {
			throw new CannotFindVpcException(projAndEnv);
		}
		
		String key = AwsFacade.INDEX_TAG;
		
		setTag(vpc, key, value);
	}

	private void setTag(Vpc vpc, String key, String value) {
		List<Tag> tags = new LinkedList<Tag>();	
		Tag newTag = new Tag(key, value);
		tags.add(newTag);
		String vpcId = vpc.getVpcId();
		
		logger.info(String.format("Set Tag Key:'%s' Value:'%s' on VPC %s", key, value, vpcId));
		setTags(vpcId, tags);
	}
	
	public void setVpcTag(ProjectAndEnv projAndEnv, String key,
			String value) {
		Vpc vpc = getCopyOfVpc(projAndEnv);
		setTag(vpc, key, value);	
	}

	private void setTags(String vpcId, List<Tag> tags) {
		List<String> resources = createResources(vpcId);
		CreateTagsRequest createTagsRequest = new CreateTagsRequest(resources , tags);
		
		ec2Client.createTags(createTagsRequest);
	}

	private List<String> createResources(String vpcId) {
		List<String> resources = new LinkedList<String>();	
		resources.add(vpcId);
		return resources;
	}
	
	public void deleteVpcTag(ProjectAndEnv projectAndEnv, String key) {
		Vpc vpc = getCopyOfVpc(projectAndEnv);
		List<String> resources = createResources(vpc.getVpcId());
		
		Tag tag = new Tag().withKey(key);
		DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest().withResources(resources).withTags(tag);
		logger.info(String.format("Delete Tag Key'%s' on VPC:%s", key, vpc.getVpcId()));
		ec2Client.deleteTags(deleteTagsRequest);	
	}

	public String getVpcIndexTag(ProjectAndEnv projAndEnv) throws CannotFindVpcException {
		Vpc vpc = getCopyOfVpc(projAndEnv);
		if (vpc==null) {
			throw new CannotFindVpcException(projAndEnv);
		}
		return getTagByName(vpc, AwsFacade.INDEX_TAG);
	}

	public void initAllTags(String vpcId, ProjectAndEnv projectAndEnv) throws CannotFindVpcException {	
		Vpc vpc = getVpcById(vpcId);
		if (vpc==null) {
			throw new CannotFindVpcException(vpcId);
		}
		logger.info("Initialise tags for VPC " + vpcId);
		List<Tag> tags = new LinkedList<Tag>();	
		Tag indexTag = new Tag(AwsFacade.INDEX_TAG, "0");
		Tag projectTag = new Tag(AwsFacade.PROJECT_TAG, projectAndEnv.getProject());
		Tag envTag = new Tag(AwsFacade.ENVIRONMENT_TAG, projectAndEnv.getEnv());
		tags.add(indexTag);
		tags.add(projectTag);
		tags.add(envTag);
		
		setTags(vpcId, tags);	
	}

	public String getVpcTag(String tagName, ProjectAndEnv projAndEnv) throws CannotFindVpcException {
		Vpc vpc = getCopyOfVpc(projAndEnv);
		if (vpc==null) {
			throw new CannotFindVpcException(projAndEnv);
		}
		return getTagByName(vpc, tagName);
	}



}
