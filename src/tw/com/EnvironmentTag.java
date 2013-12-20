package tw.com;

public class EnvironmentTag {

	private String env;
	
	@Override
	public String toString() {
		return "EnvironmentTag [env=" + env + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((env == null) ? 0 : env.hashCode());
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
		EnvironmentTag other = (EnvironmentTag) obj;
		if (env == null) {
			if (other.env != null)
				return false;
		} else if (!env.equals(other.env))
			return false;
		return true;
	}

	public EnvironmentTag(String env) {
		this.env = env;
	}

}
