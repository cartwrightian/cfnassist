package tw.com.entity;

import java.util.LinkedList;
import java.util.List;

public class SearchCriteria {
	
	private String env = "";
	private Integer buildNumber = -1;
	private String project = "";
	
	public SearchCriteria() {	
	}
	
	public SearchCriteria(ProjectAndEnv projectAndEnv) {
		if (projectAndEnv.hasEnv()) {
			env = projectAndEnv.getEnv();
		}
		if (projectAndEnv.hasProject()) {
			project = projectAndEnv.getProject();
		}
		if (projectAndEnv.hasBuildNumber()) {
			buildNumber = projectAndEnv.getBuildNumber();
		}
	}

	public SearchCriteria withEnv(String env) {
		this.env = env;		
		return this;
	}

	public boolean matches(StackEntry entry) {
		if (haveEnv()) {
			if (!env.equals(entry.getEnvTag().getEnv())) {
				return false;
			}
		}
		if (haveBuild()) {
			if (!entry.hasBuildNumber()) {
				return false; // can't match if entry has no build number
			}
			if (!buildNumber.equals(entry.getBuildNumber())) {
				return false;
			}
		}
		if (haveProject()) {
			if (!project.equals(entry.getProject())) {
				return false;
			}
		}
		return true;
	}

	public SearchCriteria withBuild(int buildNumber) {
		this.buildNumber = buildNumber;	
		return this;
	}

	public SearchCriteria withProject(String project) {
		this.project = project;
		return this;
	}
	
	private boolean haveProject() {
		return !project.isEmpty();
	}

	public boolean haveBuild() {
		return buildNumber >= 0;
	}

	private boolean haveEnv() {
		return !env.isEmpty();
	}

	public List<StackEntry> matches(List<StackEntry> entries) {
		List<StackEntry> matched = new LinkedList<>();
		for(StackEntry entry : entries) {
			if (matches(entry)) {
				matched.add(entry);
			}
		}
		return matched;
	}
	
	@Override
	public String toString() {
		return "SearchCriteria [env=" + env + ", buildNumber=" + buildNumber
				+ ", project=" + project + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((buildNumber == null) ? 0 : buildNumber.hashCode());
		result = prime * result + ((env == null) ? 0 : env.hashCode());
		result = prime * result + ((project == null) ? 0 : project.hashCode());
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
		SearchCriteria other = (SearchCriteria) obj;
		if (buildNumber == null) {
			if (other.buildNumber != null)
				return false;
		} else if (!buildNumber.equals(other.buildNumber))
			return false;
		if (env == null) {
			if (other.env != null)
				return false;
		} else if (!env.equals(other.env))
			return false;
		if (project == null) {
			if (other.project != null)
				return false;
		} else if (!project.equals(other.project))
			return false;
		return true;
	}

}
