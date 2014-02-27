package tw.com;

public class StackNotification {

	private String status;
	private String stackName;
	private String stackId;

	public StackNotification(String stackName, String status, String stackId) {
		this.status = status;
		this.stackName = stackName;
		this.stackId = stackId;
	}

	public String getStatus() {
		return status;
	}

	public String getStackName() {
		return stackName;
	}

	public String getStackId() {
		return stackId;
	}

}
