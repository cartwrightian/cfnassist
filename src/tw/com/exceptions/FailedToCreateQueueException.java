package tw.com.exceptions;

@SuppressWarnings("serial")
public class FailedToCreateQueueException extends CfnAssistException {

	public FailedToCreateQueueException(String queueId) {
		super("Failed to create SQS queue: " + queueId);
	}

}
