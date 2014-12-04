package tw.com.repository;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.MonitorStackEvents;
import tw.com.StackCache;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CloudClient;
import tw.com.providers.CloudFormationClient;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.elasticloadbalancing.model.Instance;

public class CfnRepository implements CloudFormRepository {
	private static final Logger logger = LoggerFactory.getLogger(CfnRepository.class);

	private static final String AWS_EC2_INSTANCE_TYPE = "AWS::EC2::Instance";
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 1000;
	private static final long MAX_CHECK_INTERVAL_MILLIS = 5000;
	private CloudFormationClient formationClient;
	
	// TODO Use CloudRepository instead
	private CloudClient cloudClient;

	private StackCache stackCache;
	public CfnRepository(CloudFormationClient formationClient, CloudClient cloudClient, String project) {
		this.formationClient = formationClient;
		this.cloudClient = cloudClient;
		stackCache = new StackCache(formationClient, project);
	}
	
	@Override
	public List<StackEntry> stacksMatchingEnvAndBuild(EnvironmentTag envTag, String buildNumber) {
		List<StackEntry> stacks = new LinkedList<StackEntry>();
		
		for (StackEntry entry : stackCache.getEntries()) {
			if (entry.getEnvTag().equals(envTag) && entry.getBuildNumber().equals(buildNumber)) {
				stacks.add(entry);
			}
		}
		return stacks;
	}

	@Override
	public List<StackEntry> getStacks(EnvironmentTag envTag) {
		List<StackEntry> results = new LinkedList<StackEntry>();
		for(StackEntry entry : stackCache.getEntries()) {
			if (entry.getEnvTag().equals(envTag)) {
				results.add(entry);
			}
		}
		return results;	
	}
	

	public List<StackEntry> getStacksMatching(EnvironmentTag envTag, String name) {
		logger.info(String.format("Find stacks matching env %s and name %s", envTag, name));
		List<StackEntry> results = new LinkedList<StackEntry>();
		for(StackEntry entry : stackCache.getEntries()) {
			logger.debug("Check if entry matches " + entry);
			if (entry.getEnvTag().equals(envTag) && entry.getBaseName().equals(name)) {
				results.add(entry);		
			}
		}
		
		return results;
	}

	@Override
	public String findPhysicalIdByLogicalId(EnvironmentTag envTag, String logicalId) {
		logger.info(String.format("Looking for resource matching logicalID: %s for %s",logicalId, envTag));
		List<StackEntry> stacks = getStacks(envTag);
		for (StackEntry stackEntry : stacks) {
			String stackName = stackEntry.getStackName();
			logger.debug(String.format("Checking stack %s for logical id %s", stackName, logicalId));
			String maybeHaveId = findPhysicalIdByLogicalId(envTag, stackName, logicalId);
			if (maybeHaveId != null) {
				logger.info(String.format(
						"Found physicalID: %s for logical ID: %s in stack %s", maybeHaveId, logicalId, stackName));
				return maybeHaveId;
			}
		}
		logger.warn("No match for logical ID was found");
		return null;
	}

	private String findPhysicalIdByLogicalId(EnvironmentTag envTag, String stackName, String logicalId) {
		logger.info(String.format(
				"Check Env %s and stack %s for logical ID %s", envTag,
				stackName, logicalId));

		try {
			List<StackResource> resources = stackCache.getResourcesForStack(stackName);
			logger.debug(String.format("Found %s resources for stack %s",
					resources.size(), stackName));
			for (StackResource resource : resources) {
				String candidateId = resource.getLogicalResourceId();
				String physicalResourceId = resource.getPhysicalResourceId();
				logger.debug(String
						.format("Checking for match against resource phyId=%s logId=%s",
								physicalResourceId,
								resource.getLogicalResourceId()));
				if (candidateId.equals(logicalId)) {
					return physicalResourceId;
				}
			}
		} catch (AmazonServiceException exception) {
			logger.warn("Unable to check for resources, stack name: "
					+ stackName, exception);
		}
		return null;
	}

	@Override
	public String waitForStatusToChangeFrom(String stackName,
			StackStatus currentStatus, List<String> aborts)
			throws WrongNumberOfStacksException, InterruptedException {
		
		long pause = STATUS_CHECK_INTERVAL_MILLIS;

		logger.info(String.format("Waiting for stack %s to change FROM status %s", stackName, currentStatus));
		String status = currentStatus.toString();
		Stack stack = null;
		while (status.equals(currentStatus.toString())) {
			Thread.sleep(pause);
		
			stack = formationClient.describeStack(stackName);
			status = stack.getStackStatus();
			logger.debug(String
					.format("Waiting for status of stack %s, status was %s, pause was %s",
							stackName, status, pause));
			if (pause < MAX_CHECK_INTERVAL_MILLIS) {
				pause = pause + STATUS_CHECK_INTERVAL_MILLIS;
			}
			if (aborts.contains(status)) {
				logger.error("Matched an abort status");
				break;
			}
		}
		logger.info(String
				.format("Stack status changed, status is now %s and reason (if any) was: '%s' ",
						status, stack.getStackStatusReason()));
		return status;
	}

	@Override
	public List<StackEvent> getStackEvents(String stackName) {
		return formationClient.describeStackEvents(stackName); 
	}

	@Override
	public boolean stackExists(String stackName) throws WrongNumberOfStacksException {
		logger.info("Check if stack exists for " + stackName);

		try { //  throws if stack does not exist
			formationClient.describeStack(stackName);
			return true;
		}
		catch (AmazonServiceException exception) { 
			if (exception.getStatusCode()==400) {
				return false;
			}
			throw exception;
		} catch (WrongNumberOfStacksException wrongNumberException) {
			if (wrongNumberException.getNumber()!=0) {
				throw wrongNumberException;
			} else 
			{
				return false;
			}
		}
	}
	
	@Override
	public String getStackStatus(String stackName) {
		logger.info("Getting stack status for " + stackName);
		for (StackEntry entry : stackCache.getEntries()) {
			Stack stack = entry.getStack();
			if (stack.getStackName().equals(stackName)) {
				// get latest status
				try {
					return getStackCurrentStatus(stackName);
				} catch (WrongNumberOfStacksException e) {
					logger.warn("Mismatch on stack status", e);
					return "";
				} catch (AmazonServiceException e) {
					logger.warn("Could not check status of stack " +stackName,e);
					if (e.getStatusCode()==400) {
						return ""; // stack does not exist, perhaps a delete was in progress
					}
				}
			}
		}
		logger.warn("Failed to find stack status for :" + stackName);
		return "";
	}

	private String getStackCurrentStatus(String stackName)
			throws WrongNumberOfStacksException {
		Stack stack = formationClient.describeStack(stackName);
		String stackStatus = stack.getStackStatus();
		logger.info(String.format("Got status %s for stack %s", stackStatus, stackName));
		return stackStatus;
	}

	@Override
	public StackNameAndId getStackNameAndId(String stackName)
			throws WrongNumberOfStacksException {
		Stack stack = formationClient.describeStack(stackName);
		String id = stack.getStackId();
		return new StackNameAndId(stackName, id);
	}

	@Override
	public List<String> getAllInstancesFor(ProjectAndEnv projAndEnv) {
		logger.info("Finding instances for " + projAndEnv);
		String buildNumber = "";
		if (projAndEnv.hasBuildNumber()) {
			buildNumber = projAndEnv.getBuildNumber();
			logger.info("Matching also on build number: " + buildNumber);
		}
		// fetch stacks that match proj, env and build
		List<StackEntry> stacks = stacksMatchingEnvAndBuild(projAndEnv.getEnvTag(), buildNumber);
		List<String> instanceIds = new LinkedList<String>();
		for (StackEntry entry : stacks) {
			if (entry.isLive()) {
				logger.info("Found stack :" + entry.getStackName());
				instanceIds.addAll(getInstancesFor(entry.getStackName()));
			} else {
				logger.info("Not adding stack :" + entry.getStackName());
			}
		}
		return instanceIds;
	}

	public List<String> getInstancesFor(String stackname) {
		List<String> instanceIds = new LinkedList<String>();
		List<StackResource> resources = stackCache.getResourcesForStack(stackname);
		for (StackResource resource : resources) {
			String type = resource.getResourceType();
			if (type.equals(AWS_EC2_INSTANCE_TYPE)) {
				logger.info("Matched instance: "+ resource.getPhysicalResourceId());
				instanceIds.add(resource.getPhysicalResourceId());
			}
		}
		return instanceIds;
	}

	@Override
	public Stack getStack(String stackName) throws WrongNumberOfStacksException {
		for(StackEntry entry : stackCache.getEntries()) {
			if (entry.getStackName().equals(stackName)) {
				return entry.getStack();
			}
		}
		// TODO we can only get here if stack is not tagged by cfn assist managed, should we throw?
		return formationClient.describeStack(stackName);
	}
	
	@Override
	public List<StackEntry> getStacks() {
		return stackCache.getEntries();
	}

	// TODO make private, the cache updates should be moved into this class
	@Override
	public Stack updateRepositoryFor(StackNameAndId id) throws WrongNumberOfStacksException {
		return stackCache.updateRepositoryFor(id);		
	}

	public List<TemplateParameter> validateStackTemplate(String templateBody) {
		return formationClient.validateTemplate(templateBody);
	}

	// TODO cache updates into this class
	@Override
	public void deleteStack(String stackName) {
		formationClient.deleteStack(stackName);	
	}

	// TODO cache updates into this class
	@Override
	public StackNameAndId createStack(ProjectAndEnv projAndEnv,
			String contents, String stackName, Collection<Parameter> parameters, MonitorStackEvents monitor, String commentTag) throws NotReadyException {
		return formationClient.createStack(projAndEnv,contents, stackName, parameters, monitor, commentTag);		
	}

	// TODO cache updates into this class
	@Override
	public StackNameAndId updateStack(String contents,
			Collection<Parameter> parameters, MonitorStackEvents monitor, String stackName) throws InvalidParameterException, WrongNumberOfStacksException, NotReadyException {			
		return formationClient.updateStack(contents, parameters, monitor, stackName);
	}

	@Override
	public List<Instance> getAllInstancesMatchingType(ProjectAndEnv projAndEnv,
			String typeTag) throws WrongNumberOfInstancesException {
		Collection<String> instancesIds = getAllInstancesFor(projAndEnv);
		
		List<Instance> instances = new LinkedList<Instance>();
		for (String id : instancesIds) {
			if (instanceHasCorrectType(typeTag, id)) {
				logger.info(String.format("Adding instance %s as it matched %s %s",id, AwsFacade.TYPE_TAG, typeTag));
				instances.add(new Instance(id));
			} else {
				logger.info(String.format("Not adding instance %s as did not match %s %s",id, AwsFacade.TYPE_TAG, typeTag));
			}	
		}
		logger.info(String.format("Found %s instances matching %s and type: %s", instances.size(), projAndEnv, typeTag));
		return instances;
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
		return cloudClient.getTagsForInstance(id);
	}


	
}
