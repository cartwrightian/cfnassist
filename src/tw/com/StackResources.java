package tw.com;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.cloudformation.model.StackResource;

public class StackResources {
	// (StackName) -> [Stack Resources]
	private Map<String,List<StackResource>> theResources;
	
	public StackResources() {
		theResources = new HashMap<String,List<StackResource>>(); 
	}

	public boolean containsStack(String stackName) {
		return theResources.containsKey(stackName);
	}

	public List<StackResource> getStackResources(String stackName) {
		return theResources.get(stackName);
	}

	public void addStackResources(String stackName,
			List<StackResource> resources) {
		theResources.put(stackName, resources);
		
	}

}
