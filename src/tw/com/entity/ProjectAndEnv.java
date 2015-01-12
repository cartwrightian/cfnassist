package tw.com.entity;


public class ProjectAndEnv {
	
	private String project;
	private String env;
	private String buildNumber = null;
	private boolean useSns;
	private String s3Bucket;
	private boolean useCapabilityIAM;

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
				+ ", buildNumber=" + buildNumber + ", useArn=" + useSns + ", useCapabilityIAM = " + useCapabilityIAM + "]";
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
	
	

//	@Override
//	public int hashCode() {
//		final int prime = 31;
//		int result = 1;
//		result = prime * result
//				+ ((buildNumber == null) ? 0 : buildNumber.hashCode());
//		result = prime * result + ((env == null) ? 0 : env.hashCode());
//		result = prime * result + ((project == null) ? 0 : project.hashCode());
//		result = prime * result + (useSns ? 1231 : 1237);
//		return result;
//	}
//
//	@Override
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		ProjectAndEnv other = (ProjectAndEnv) obj;
//		if (buildNumber == null) {
//			if (other.buildNumber != null)
//				return false;
//		} else if (!buildNumber.equals(other.buildNumber))
//			return false;
//		if (env == null) {
//			if (other.env != null)
//				return false;
//		} else if (!env.equals(other.env))
//			return false;
//		if (project == null) {
//			if (other.project != null)
//				return false;
//		} else if (!project.equals(other.project))
//			return false;
//		if (useSns != other.useSns)
//			return false;
//		return true;
//	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((buildNumber == null) ? 0 : buildNumber.hashCode());
		result = prime * result + ((env == null) ? 0 : env.hashCode());
		result = prime * result + ((project == null) ? 0 : project.hashCode());
		result = prime * result + (useCapabilityIAM ? 1231 : 1237);
		result = prime * result + (useSns ? 1231 : 1237);
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
		if (useCapabilityIAM != other.useCapabilityIAM)
			return false;
		if (useSns != other.useSns)
			return false;
		return true;
	}

	public void setS3Bucket(String s3Bucket) {
		this.s3Bucket = s3Bucket;
	}

	public String getS3Bucket() {
		return s3Bucket;
	}

	public void setUseCapabilityIAM() {
		useCapabilityIAM = true;	
	}

	public boolean useCapabilityIAM() {
		return useCapabilityIAM;
	}

}
