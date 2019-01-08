package tw.com.exceptions;

import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import tw.com.entity.StackNameAndId;

@SuppressWarnings("serial")
public class WrongStackStatus extends CfnAssistException {

	private StackNameAndId stackId;

	public WrongStackStatus(StackNameAndId stackId, StackStatus requiredStatus, StackStatus actual) {
		super(String.format("Got an unexpected stack status for %s, expected %s and got %s", stackId, requiredStatus, actual));
		this.stackId = stackId;
	}

	public StackNameAndId getStackId() {
		return stackId;
	}

}
