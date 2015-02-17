package tw.com.entity;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class StackEntry {
	private static final Logger logger = LoggerFactory.getLogger(StackEntry.class);

	private EnvironmentTag environmentTag;
	private Stack stack;
	private String buildNumber = "";
	private String project;

	public StackEntry(String project, EnvironmentTag environmentTag, Stack stack) {
		this.environmentTag = environmentTag;
		this.stack = stack;
		this.project = project;
	}
	
	@Override
	public String toString() {
		return String.format("StackEntry [env=%s, project=%s, stackName=%s, buildNumber='%s']", 
				environmentTag.getEnv(), project, stack.getStackName(), buildNumber);
	}

	public EnvironmentTag getEnvTag() {
		return environmentTag;
	}

	public Stack getStack() {
		return stack;
	}

	public String getBuildNumber() {
		return buildNumber;
	}
	
	public StackEntry setBuildNumber(String buildNumber) {
		this.buildNumber = buildNumber;	
		return this;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((environmentTag == null) ? 0 : environmentTag.hashCode());
		result = prime * result + ((stack == null) ? 0 : stack.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StackEntry other = (StackEntry) obj;
		if (environmentTag == null) {
			if (other.environmentTag != null)
				return false;
		} else if (!environmentTag.equals(other.environmentTag))
			return false;
		if (stack == null) {
			if (other.stack != null)
				return false;
		} else if (!stack.equals(other.stack))
			return false;
		return true;
	}

	public boolean isLive() {
		return stack.getStackStatus().equals(StackStatus.CREATE_COMPLETE.toString());
	}

	public String getStackName() {
		return stack.getStackName();
	}

	public String getProject() {
		return project;
	}

	public String getBaseName() {
		String fullName = stack.getStackName();	
		String withoutProject = fullName.replace(project, "");
		
		String withoutBuild = withoutProject;
		if (!buildNumber.isEmpty()) {
			withoutBuild =  withoutProject.replace(buildNumber, "");
		}
		
		String basename = withoutBuild.replace(environmentTag.getEnv(), "");
		logger.debug("basename is " + basename);
		return basename;
	}


}
