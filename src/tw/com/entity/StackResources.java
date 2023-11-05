package tw.com.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.cloudformation.model.StackResource;

public class StackResources {
	private static final Logger logger = LoggerFactory.getLogger(StackResources.class);

	// (StackName) -> [Stack Resources]
	private final Map<String,List<StackResource>> theResources;
	
	public StackResources() {
		theResources = new HashMap<>();
	}

	public boolean containsStack(String stackName) {
		return theResources.containsKey(stackName);
	}

	public List<StackResource> getStackResources(String stackName) {
        return theResources.get(stackName);
	}

	public void addStackResources(String stackName,
			List<StackResource> resources) {
		logger.debug("Adding resources for stack: " + stackName);
		theResources.put(stackName, resources);		
	}

	public void removeResources(String stackName) {
		if (theResources.containsKey(stackName)) {
			logger.info("Removing resources for stack: " + stackName);
			theResources.remove(stackName);
		}		
	}

}
