package tw.com;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.entity.EnvironmentTag;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.entity.StackResources;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CloudFormationClient;

import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.Tag;

public class StackCache {
	private static final Logger logger = LoggerFactory.getLogger(StackCache.class);

	private List<StackEntry> theEntries;
	CloudFormationClient formationClient;
	private StackResources stackResources;
	private String project;
	
	public StackCache(CloudFormationClient formationClient, String project) {
		this.formationClient = formationClient;
		this.project = project;
		stackResources = new StackResources();
		theEntries = new LinkedList<StackEntry>();
	}

	public List<StackEntry> getEntries() {
		getAllStacksForProject();
		return theEntries;
	}
	
	private void getAllStacksForProject() {
		// TODO handle "next token"?
		if (theEntries.size() == 0) {
			logger.info("No cached stacks, loading all stacks");
	
			List<Stack> stacks = formationClient.describeAllStacks();
			populateEntriesIfProjectMatches(stacks);
			logger.info(String.format("Loaded %s stacks", theEntries.size()));
		} else {
			logger.info("Cache hit on stacks");
		}
	}
	
	private void populateEntriesIfProjectMatches(List<Stack> stacks) {
		logger.info(String.format("Populating stack entries for %s stacks",stacks.size()));
		for(Stack stack : stacks) {

			logger.info(String.format("Checking stack %s for tag", stack.getStackName()));
		
			List<Tag> tags = stack.getTags();
			int count = 3;
			String env = "";
			String proj = "";
			String build = "";
			for(Tag tag : tags) {
				String key = tag.getKey();
				String value = tag.getValue();
				if (key.equals(AwsFacade.ENVIRONMENT_TAG)) {
					env = value;
					count--;
				} else if (key.equals(AwsFacade.PROJECT_TAG)) {
					proj = value;
					count--;
				} else if (key.equals(AwsFacade.BUILD_TAG)) {
					build = value;
					count--;
				}
				if (count==0) break; // small optimisation 
			}
			addEntryIfProjectAndEnvMatches(stack, env, proj, build);
		}		
	}
	
	private void addEntryIfProjectAndEnvMatches(Stack stack, String env, String proj, String build) {
		String stackName = stack.getStackName();
		if (!proj.equals(project) || (env.isEmpty())) {
			logger.warn(String.format("Could not match expected tags (%s and %s) for project '%s' and stackname %s", 
					AwsFacade.ENVIRONMENT_TAG, AwsFacade.PROJECT_TAG, proj, stackName));
			return;
		}
			
		logger.info(String.format("Stack %s matched %s and %s", stackName, env, proj));
		EnvironmentTag envTag = new EnvironmentTag(env);
		StackEntry entry = new StackEntry(proj, envTag, stack);
		if (!build.isEmpty()) {
			logger.info(String.format("Saving associated build number (%s) into stack %s", build, stackName));
			entry.setBuildNumber(build);
		}
		if (theEntries.contains(entry)) {
			theEntries.remove(entry);
			logger.info("Replacing or Removing entry for stack " + stackName);
		}
		String stackStatus = stack.getStackStatus();
		theEntries.add(entry);
		stackResources.removeResources(stackName);
		logger.info(String.format("Added stack %s matched, environment is %s, status was %s", stackName, envTag, stackStatus));			 
	}
	
	public Stack updateRepositoryFor(StackNameAndId id) throws WrongNumberOfStacksException {
		logger.info("Update stack repository for stack: " + id);
		Stack stack = formationClient.describeStack(id.getStackName());
		
		populateEntriesIfProjectMatches(stack);
		
		return stack;
	}
	
	private void populateEntriesIfProjectMatches(Stack stack) {
		LinkedList<Stack> list = new LinkedList<Stack>();
		list.add(stack);
		this.populateEntriesIfProjectMatches(list);
	}

	public List<StackResource> getResourcesForStack(String stackName) {

		List<StackResource> resources = null;
		if (stackResources.containsStack(stackName)) {
			logger.info("Cache hit on stack resources for stack " + stackName);
			resources = stackResources.getStackResources(stackName);
		} else {
			logger.info("Cache miss, loading resources for stack " + stackName);
			resources = formationClient.describeStackResources(stackName);
		
			stackResources.addStackResources(stackName, resources);
		}
		return resources;
	}

}
