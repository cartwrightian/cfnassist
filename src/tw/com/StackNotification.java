package tw.com;

public class StackNotification {

	private String status;
	private String stackName;

	public StackNotification(String stackName, String status) {
		this.status = status;
		this.stackName = stackName;
	}

	public String getStatus() {
		return status;
	}

	public String getStackName() {
		return stackName;
	}

}
