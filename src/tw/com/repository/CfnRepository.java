package tw.com.repository;

import com.amazonaws.AmazonServiceException;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.ec2.model.Tag;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.MonitorStackEvents;
import tw.com.StackCache;
import tw.com.entity.*;
import tw.com.exceptions.*;
import tw.com.providers.CFNClient;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class CfnRepository implements CloudFormRepository {
	private static final Logger logger = LoggerFactory.getLogger(CfnRepository.class);

	private static final String AWS_EC2_INSTANCE_TYPE = "AWS::EC2::Instance";
	private static final long STATUS_CHECK_INTERVAL_MILLIS = 1000;
	private static final long MAX_CHECK_INTERVAL_MILLIS = 5000;

    private final String project;
    private CFNClient formationClient;
	private CloudRepository cloudRepository;
	private StackCache stackCache;
	
	public CfnRepository(CFNClient formationClient, CloudRepository cloudRepository, String project) {
		this.formationClient = formationClient;
		this.cloudRepository = cloudRepository;
        this.project = project;
		stackCache = new StackCache(formationClient, project);
	}
	
	@Override
	public List<StackEntry> getStacks(EnvironmentTag envTag) {
		List<StackEntry> results = new LinkedList<>();
		for(StackEntry entry : stackCache.getEntries()) {
			if (entry.getEnvTag().equals(envTag)) {
				results.add(entry);
			}
		}
		return results;
	}

	public List<StackEntry> getStacksMatching(EnvironmentTag envTag, String name) {
		logger.info(String.format("Find stacks matching env %s and name %s", envTag, name));
		List<StackEntry> results = new LinkedList<>();
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
				String candidateId = resource.logicalResourceId();
				String physicalResourceId = resource.physicalResourceId();
				logger.debug(String
						.format("Checking for match against resource phyId=%s logId=%s",
								physicalResourceId,
								resource.logicalResourceId()));
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
	public StackStatus waitForStatusToChangeFrom(String stackName,
											StackStatus currentStatus, List<StackStatus> aborts)
			throws WrongNumberOfStacksException, InterruptedException {
		
		long pause = STATUS_CHECK_INTERVAL_MILLIS;

		logger.info(String.format("Waiting for stack %s to change FROM status %s", stackName, currentStatus));
		StackStatus status = currentStatus;
		while (status.equals(currentStatus)) {
			Thread.sleep(pause);

			status = formationClient.currentStatus(stackName);
			logger.debug(String.format("Waiting for status of stack %s, status was %s, pause was %s",
							stackName, status, pause));
			if (pause < MAX_CHECK_INTERVAL_MILLIS) {
				pause = pause + STATUS_CHECK_INTERVAL_MILLIS;
			}
			if (aborts.contains(status)) {
				logger.error("Matched an abort status");
				break;
			}
		}
		logger.info(String.format("Stack status changed, status is now %s", status));
		return status;
	}

	@Override
	public List<StackEvent> getStackEvents(String stackName) {
		return formationClient.describeStackEvents(stackName); 
	}

	@Override
	public boolean stackExists(String stackName) {
		logger.info("Check if stack exists for " + stackName);
		return formationClient.stackExists(stackName);
	}
	
	@Override
	public StackStatus getStackStatus(String stackName) throws WrongNumberOfStacksException {
		logger.info("Getting stack status for " + stackName);
		for (StackEntry entry : stackCache.getEntries()) {
			Stack stack = entry.getStack();
			if (stack.stackName().equals(stackName)) {
				// get latest status
				try {
					return getStackCurrentStatus(stackName);
				} catch (WrongNumberOfStacksException e) {
					logger.warn("Mismatch on stack status", e);
					return StackStatus.UNKNOWN_TO_SDK_VERSION;
				} catch (AmazonServiceException e) {
					logger.warn("Could not check status of stack " +stackName,e);
					if (e.getStatusCode()==400) {
						return StackStatus.UNKNOWN_TO_SDK_VERSION; // stack does not exist, perhaps a delete was in progress
					}
				}
			}
		}
		logger.warn("Failed to find stack status for :" + stackName);
		throw new WrongNumberOfStacksException(1,0);
	}

	private StackStatus getStackCurrentStatus(String stackName)
			throws WrongNumberOfStacksException {
		Stack stack = formationClient.describeStack(stackName);
		StackStatus stackStatus = stack.stackStatus();
		logger.info(String.format("Got status %s for stack %s", stackStatus, stackName));
		return stackStatus;
	}

	@Override
	public StackNameAndId getStackNameAndId(String stackName)
			throws WrongNumberOfStacksException {
		Stack stack = formationClient.describeStack(stackName);
		String id = stack.stackId();
		return new StackNameAndId(stackName, id);
	}

	@Override
	public List<String> getAllInstancesFor(SearchCriteria criteria) {
		logger.info("Finding instances for " + criteria);
		
		List<StackEntry> stacks = criteria.matches(stackCache.getEntries());
		
		List<String> instanceIds = new LinkedList<>();
		for (StackEntry entry : stacks) {
			String stackName = entry.getStackName();
			if (entry.isLive()) {
				logger.info("Found stack :" + stackName);
				instanceIds.addAll(getInstancesFor(stackName));
			} else {
				logger.info("Not adding stack :" + stackName);
			}
		}
		return instanceIds;
	}

	public List<String> getInstancesFor(String stackname) {
		List<String> instanceIds = new LinkedList<>();
		List<StackResource> resources = stackCache.getResourcesForStack(stackname);
		for (StackResource resource : resources) {
			String type = resource.resourceType();
			if (type.equals(AWS_EC2_INSTANCE_TYPE)) {
				logger.info("Matched instance: "+ resource.physicalResourceId());
				instanceIds.add(resource.physicalResourceId());
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
    public StackEntry getStacknameByIndex(EnvironmentTag envTag, Integer index) throws WrongNumberOfStacksException {
        SearchCriteria criteriaForIndex = new SearchCriteria(new ProjectAndEnv(project, envTag.getEnv())).withIndex(index);

        List<StackEntry> stacks = criteriaForIndex.matches(stackCache.getEntries());
        if (stacks.isEmpty()) {
            SearchCriteria criteria = new SearchCriteria(new ProjectAndEnv(project, envTag.getEnv())).
                    withUpdateIndex(index);
            stacks = criteria.matches(stackCache.getEntries());
        }
        if (stacks.size()!=1) {
            throw new WrongNumberOfStacksException(1, stacks.size());
        }
        StackEntry stack = stacks.get(0);
        logger.info(String.format("Found stack %s for %s and index %s", stack, envTag, index));
        return stack;
    }

	@Override
	public List<StackEntry> getStacks() {
		return stackCache.getEntries();
	}

	public List<TemplateParameter> validateStackTemplate(String templateBody) {
		return formationClient.validateTemplate(templateBody);
	}

	@Override
	public void deleteStack(String stackName) {
		formationClient.deleteStack(stackName);	
	}

	@Override
	public StackNameAndId createStack(ProjectAndEnv projAndEnv,
			String contents, String stackName, Collection<Parameter> parameters, MonitorStackEvents monitor, Tagging tagging) throws CfnAssistException {
		return formationClient.createStack(projAndEnv,contents, stackName, parameters, monitor, tagging);
	}

	@Override
	public StackNameAndId updateStack(String contents,
                                      Collection<Parameter> parameters, MonitorStackEvents monitor, String stackName) throws CfnAssistException {
		return formationClient.updateStack(contents, parameters, monitor, stackName);
	}

	@Override
	public List<Instance> getAllInstancesMatchingType(SearchCriteria criteria, String typeTag) throws CfnAssistException {
		Collection<String> instancesIds = getAllInstancesFor(criteria);
		
		List<Instance> instances = new LinkedList<Instance>();
		for (String id : instancesIds) {
			if (instanceHasCorrectType(typeTag, id)) {
				logger.info(String.format("Adding instance %s as it matched %s %s",id, AwsFacade.TYPE_TAG, typeTag));
				instances.add(new Instance(id));
			} else {
				logger.info(String.format("Not adding instance %s as did not match %s %s",id, AwsFacade.TYPE_TAG, typeTag));
			}	
		}
		logger.info(String.format("Found %s instances matching %s and type: %s", instances.size(), criteria, typeTag));
		return instances;
	}
	
	private boolean instanceHasCorrectType(String type, String id) throws WrongNumberOfInstancesException {
		List<Tag> tags = cloudRepository.getTagsForInstance(id);
		for(Tag tag : tags) {
			if (tag.key().equals(AwsFacade.TYPE_TAG)) {
				return tag.value().equals(type);
			}
		}
		return false;
	}
	
	private Stack updateRepositoryFor(StackNameAndId id) throws WrongNumberOfStacksException {
		return stackCache.updateRepositoryFor(id);		
	}

	@Override
	public void createFail(StackNameAndId id) throws WrongNumberOfStacksException {
		updateRepositoryFor(id);
	}

	@Override
	public Stack createSuccess(StackNameAndId id) throws WrongNumberOfStacksException {
		return updateRepositoryFor(id);
	}

	@Override
	public void updateFail(StackNameAndId id) throws WrongNumberOfStacksException {
		updateRepositoryFor(id);
	}

	@Override
	public Stack updateSuccess(StackNameAndId id) throws WrongNumberOfStacksException {
		return updateRepositoryFor(id);
	}
	
}
