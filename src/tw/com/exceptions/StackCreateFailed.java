package tw.com.exceptions;

import tw.com.StackId;


@SuppressWarnings("serial")
public class StackCreateFailed extends CfnAssistException {

	private StackId stackId;

	public StackCreateFailed(StackId id) {
		super("Stack Create Failed, please check log or use cfn-describe-stack-events for reason [stackId=" + id.toString() + "]");
		this.stackId = id;
	}

	public StackId getStackId() {
		return stackId;
	}

}
