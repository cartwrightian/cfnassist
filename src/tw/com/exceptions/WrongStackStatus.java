package tw.com.exceptions;

import tw.com.entity.StackNameAndId;

@SuppressWarnings("serial")
public class WrongStackStatus extends CfnAssistException {

	private StackNameAndId stackId;

	public WrongStackStatus(String msg) {
		super(msg);
		// TODO Auto-generated constructor stub
	}

	public WrongStackStatus(StackNameAndId stackId, String requiredStatus, String actual) {
		super(String.format("Got an unexpected stack status for %s, expected %s and got %s", stackId, requiredStatus, actual));
		this.stackId = stackId;
	}

	public StackNameAndId getStackId() {
		return stackId;
	}

}
