package tw.com.entity;

public class StackNameAndId {

	private String stackName;
	private String stackId;

	public StackNameAndId(String stackName, String stackId) {
		this.stackName = stackName;
		this.stackId = stackId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((stackId == null) ? 0 : stackId.hashCode());
		result = prime * result
				+ ((stackName == null) ? 0 : stackName.hashCode());
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
		StackNameAndId other = (StackNameAndId) obj;
		if (stackId == null) {
			if (other.stackId != null)
				return false;
		} else if (!stackId.equals(other.stackId))
			return false;
		if (stackName == null) {
			if (other.stackName != null)
				return false;
		} else if (!stackName.equals(other.stackName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "StackId [stackName=" + stackName + ", stackId=" + stackId + "]";
	}

	public String getStackName() {
		return stackName;
	}

	public String getStackId() {
		return stackId;
	}

}
