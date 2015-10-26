package tw.com.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class SearchCriteria {
    private static final Logger logger = LoggerFactory.getLogger(SearchCriteria.class);

    private String env = "";
	private Optional<Integer> buildNumber = Optional.empty();
	private String project = "";
    private Optional<Integer> index = Optional.empty();

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
			buildNumber = Optional.of(projectAndEnv.getBuildNumber());
		}
	}

	public SearchCriteria withEnv(String env) {
		this.env = env;		
		return this;
	}

	public boolean matches(StackEntry entry) {
        logger.debug("checking " + entry + " against " + this);
		if (haveEnv()) {
			if (!env.equals(entry.getEnvTag().getEnv())) {
				return false;
			}
		}
		if (haveBuild()) {
			if (!entry.hasBuildNumber()) {
				return false; // can't match if entry has no build number
			}
			if (!buildNumber.get().equals(entry.getBuildNumber())) {
				return false;
			}
		}
		if (haveProject()) {
			if (!project.equals(entry.getProject())) {
				return false;
			}
		}
        if (index.isPresent()) {
            if (!entry.hasIndex()) {
                return false;
            }
            if (!index.get().equals(entry.getIndex())) {
                return false;
            }
        }
		return true;
	}

	public SearchCriteria withBuild(int buildNumber) {
		this.buildNumber = Optional.of(buildNumber);
		return this;
	}

	public SearchCriteria withIndex(Integer index) {
		this.index = Optional.of(index);
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
		return buildNumber.isPresent();
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
        return "SearchCriteria{" +
                "env='" + env + '\'' +
                ", buildNumber=" + buildNumber +
                ", project='" + project + '\'' +
                ", index=" + index +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchCriteria criteria = (SearchCriteria) o;

        if (env != null ? !env.equals(criteria.env) : criteria.env != null) return false;
        if (buildNumber != null ? !buildNumber.equals(criteria.buildNumber) : criteria.buildNumber != null)
            return false;
        if (project != null ? !project.equals(criteria.project) : criteria.project != null) return false;
        return !(index != null ? !index.equals(criteria.index) : criteria.index != null);

    }

    @Override
    public int hashCode() {
        int result = env != null ? env.hashCode() : 0;
        result = 31 * result + (buildNumber != null ? buildNumber.hashCode() : 0);
        result = 31 * result + (project != null ? project.hashCode() : 0);
        result = 31 * result + (index != null ? index.hashCode() : 0);
        return result;
    }
}
