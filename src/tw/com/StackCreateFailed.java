package tw.com;

@SuppressWarnings("serial")
public class StackCreateFailed extends CfnAssistException {

	private String stackName;

	@Override
	public String toString() {
		return "Stack Create Failed, please check log or use cfn-describe-stack-events for reason [stackName=" + stackName + "]";
	}

	public StackCreateFailed(String stackName) {
		this.stackName = stackName;
	}

}
