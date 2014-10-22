package tw.com.entity;


public class DeletionPending implements Comparable<DeletionPending> {


	private int delta;
	private StackNameAndId stackId;

	public DeletionPending(int delta, StackNameAndId stackId) {
		this.delta = delta;
		this.stackId = stackId;
	}

	public Integer getDelta() {
		return delta;
	}

	public StackNameAndId getStackId() {
		return stackId;
	}
	
	@Override
	public String toString() {
		return String.format("DeletionPending(delta=%s,stackId=%s", delta, stackId);
	}

	@Override
	public int compareTo(DeletionPending o) {
		// when sorted we want them ordered by highest delta first
		if (o.delta==this.delta) return 0;
		if (o.delta>this.delta) return 1;
		return -1;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + delta;
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
		DeletionPending other = (DeletionPending) obj;
		if (delta != other.delta)
			return false;
		return true;
	}


}
