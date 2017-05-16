package tw.com;

import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.entity.StackResources;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CloudFormationClient;

import java.util.*;

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
		theEntries = new LinkedList<>();
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
		logger.info(String.format("Populating stack entries for %s stacks", stacks.size()));
		for(Stack stack : stacks) {

			logger.info(String.format("Checking stack %s for tag", stack.getStackName()));
		
			List<Tag> tags = stack.getTags();
            Map<String, String> keyValues = convertToMap(tags);
			int count = 3;
			String env = "";
			String proj = "";
			Integer build = null;
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
					build = Integer.parseInt(value);
					count--;
				}
				if (count==0) break; // small optimisation 
			}
            //String index = keyValues.get(AwsFacade.INDEX_TAG);
            addEntryIfProjectAndEnvMatches(stack, env, proj, build, keyValues);
		}		
	}

    private HashMap<String, String> convertToMap(List<Tag> tags) {
        HashMap<String, String> result = new HashMap<>();
        tags.forEach(tag -> result.put(tag.getKey(), tag.getValue()));
        return result;
    }

    private void addEntryIfProjectAndEnvMatches(Stack stack, String env, String proj, Integer build, Map<String, String> keyValues) {
		String stackName = stack.getStackName();
		if (!proj.equals(project) || (env.isEmpty())) {
			logger.warn(String.format("Could not match expected tags (%s and %s) for project '%s' and stackname %s", 
					AwsFacade.ENVIRONMENT_TAG, AwsFacade.PROJECT_TAG, proj, stackName));
			return;
		}
			
		logger.info(String.format("Stack %s matched %s and %s", stackName, env, proj));
		EnvironmentTag envTag = new EnvironmentTag(env);
		StackEntry entry = new StackEntry(proj, envTag, stack);
		if (build!=null) {
			logger.info(String.format("Saving associated build number (%s) into stack %s", build, stackName));
			entry.setBuildNumber(build);
		}
        if (keyValues.containsKey(AwsFacade.INDEX_TAG)) {
            addIndexTag(keyValues, stackName, entry);
        }
		if (keyValues.containsKey(AwsFacade.UPDATE_INDEX_TAG)) {
            addUpdateIndexTag(keyValues, entry);
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

    private void addUpdateIndexTag(Map<String, String> keyValues, StackEntry entry) {
        String raw = keyValues.get(AwsFacade.UPDATE_INDEX_TAG);
        String[] values = raw.split(",");
        Set<Integer> updateIndexs = new HashSet<>();
        for (String value : values) {
            updateIndexs.add(Integer.parseInt(value));
        }
        entry.setUpdateIndex(updateIndexs);
    }

    private void addIndexTag(Map<String, String> keyValues, String stackName, StackEntry entry) {
        String index = keyValues.get(AwsFacade.INDEX_TAG);
        int number = Integer.parseInt(index);
        logger.info(String.format("Saving associated index (%s) into stack %s", number, stackName));
        entry.setIndex(number);
    }

    public Stack updateRepositoryFor(StackNameAndId id) throws WrongNumberOfStacksException {
		logger.info("Update stack repository for stack: " + id);
		Stack stack = formationClient.describeStack(id.getStackName());
		
		populateEntriesIfProjectMatches(stack);
		
		return stack;
	}
	
	private void populateEntriesIfProjectMatches(Stack stack) {
		LinkedList<Stack> list = new LinkedList<>();
		list.add(stack);
		this.populateEntriesIfProjectMatches(list);
	}

	public List<StackResource> getResourcesForStack(String stackName) {

		List<StackResource> resources;
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
