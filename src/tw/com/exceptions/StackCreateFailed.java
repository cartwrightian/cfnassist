package tw.com.exceptions;


@SuppressWarnings("serial")
public class StackCreateFailed extends CfnAssistException {

	public StackCreateFailed(String msg) {
		super("Stack Create Failed, please check log or use cfn-describe-stack-events for reason [stackName=" + msg + "]");
	}

}
