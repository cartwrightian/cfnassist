package tw.com.exceptions;

@SuppressWarnings("serial")
public class BadVPCDeltaIndexException extends CfnAssistException {
	public BadVPCDeltaIndexException(String tag) {
		super("Cannot understand tag:" +tag);
	}
}
