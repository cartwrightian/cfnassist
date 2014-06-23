package tw.com;

public class ProjectAndEnv {
	
	private String project;
	private String env;
	private String buildNumber = null;
	private boolean useSns;
	private String s3Bucket;

	public ProjectAndEnv(String project, String env) {
		useSns = false;
		// TODO guard clause on project and env being empty??
		this.project = project;
		this.env = env;
	}

	public String getProject() {
		return project;
	}

	public String getEnv() {
		return env;
	}

	@Override
	public String toString() {
		return "ProjectAndEnv [project=" + project + ", env=" + env
				+ ", buildNumber=" + buildNumber + ", useArn=" + useSns + "]";
	}

	public void addBuildNumber(String buildNumber) {
		this.buildNumber  = buildNumber;		
	}

	

	public String getBuildNumber() {
		return buildNumber;
	}
	
	public boolean useSNS() {
		return useSns;
	}

	public void setUseSNS() {
		useSns = true;		
	}

	public EnvironmentTag getEnvTag() {
		return new EnvironmentTag(env);
	}

	public boolean hasProject() {
		return project!=null && !project.isEmpty();
	}

	public boolean hasBucketName() {
		return s3Bucket!=null && !s3Bucket.isEmpty();
	}
	
	public boolean hasBuildNumber() {
		return buildNumber!=null && !buildNumber.isEmpty();
	}

	public boolean hasEnv() {
		return !env.isEmpty();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		ProjectAndEnv other = (ProjectAndEnv) obj;
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

	public void setS3Bucket(String s3Bucket) {
		this.s3Bucket = s3Bucket;
	}

	public String getS3Bucket() {
		return s3Bucket;
	}

	
}
