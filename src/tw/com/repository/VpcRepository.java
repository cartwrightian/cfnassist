package tw.com.repository;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.SetsDeltaIndex;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.providers.CloudClient;

import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;

public class VpcRepository {
	private static final Logger logger = LoggerFactory.getLogger(VpcRepository.class);
	private CloudClient cloudClient;
	private HashMap<ProjectAndEnv, String> idCache; // used to avoid search by tag unless needed
	
	public VpcRepository(CloudClient cloudClient) {
		this.cloudClient = cloudClient;
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
				idCache.put(projectAndEnv, result.vpcId());
			}
			return result;
		}	
	}

	public Vpc getCopyOfVpc(String vpcId) {
		return getVpcById(vpcId);
	}

	private Vpc getVpcById(String vpcId) {
		return cloudClient.describeVpc(vpcId);
	}

	private Vpc findVpcUsingProjectAndEnv(ProjectAndEnv key) {
		List<Vpc> vpcs = cloudClient.describeVpcs();

		for(Vpc vpc : vpcs) {
			String vpcId = vpc.vpcId();
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
		List<Tag> tags = vpc.tags();
		for(Tag tag : tags) {	
			if (tag.key().equals(tagName)) {
				return tag.value();
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
		List<Tag> tags = new LinkedList<>();
		Tag newTag = Tag.builder().key(key).value(value).build();
		tags.add(newTag);
		String vpcId = vpc.vpcId();
		
		logger.info(String.format("Set Tag Key:'%s' Value:'%s' on VPC %s", key, value, vpcId));
		setTags(vpcId, tags);
	}
	
	public void setVpcTag(ProjectAndEnv projAndEnv, String key,
			String value) {
		Vpc vpc = getCopyOfVpc(projAndEnv);
		setTag(vpc, key, value);	
	}

	private void setTags(String vpcId, List<Tag> tags) {
		cloudClient.addTagsToResources(createResourceList(vpcId), tags);
	}

	private List<String> createResourceList(String vpcId) {
		List<String> resources = new LinkedList<String>();	
		resources.add(vpcId);
		return resources;
	}
	
	public void deleteVpcTag(ProjectAndEnv projectAndEnv, String key) {
		Vpc vpc = getCopyOfVpc(projectAndEnv);
		List<String> resources = createResourceList(vpc.vpcId());
		Tag tag = Tag.builder().key(key).build();
		cloudClient.deleteTagsFromResources(resources, tag);
				
		logger.info(String.format("Delete Tag Key'%s' on VPC:%s", key, vpc.vpcId()));
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
		List<Tag> tags = new LinkedList<>();
		Tag indexTag = Tag.builder().key(AwsFacade.INDEX_TAG).value("0").build();
		Tag projectTag = Tag.builder().key(AwsFacade.PROJECT_TAG).value(projectAndEnv.getProject()).build();
		Tag envTag = Tag.builder().key(AwsFacade.ENVIRONMENT_TAG).value(projectAndEnv.getEnv()).build();
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

	public SetsDeltaIndex getSetsDeltaIndexFor(ProjectAndEnv projAndEnv) {
		return new SetDeltaIndexForProjectAndEnv(projAndEnv, this);
	}
	
}
