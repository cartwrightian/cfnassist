package tw.com.exceptions;

import tw.com.StackId;

@SuppressWarnings("serial")
public class WrongStackStatus extends CfnAssistException {

	private StackId stackId;

	public WrongStackStatus(String msg) {
		super(msg);
		// TODO Auto-generated constructor stub
	}

	public WrongStackStatus(StackId stackId, String requiredStatus, String actual) {
		super(String.format("Got an unexpected stack status for %s, expected %s and got %s", stackId, requiredStatus, actual));
		this.stackId = stackId;
	}

	public StackId getStackId() {
		return stackId;
	}

}
