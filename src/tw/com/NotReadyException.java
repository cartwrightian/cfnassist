package tw.com;

import tw.com.exceptions.CfnAssistException;

@SuppressWarnings("serial")
public class NotReadyException extends CfnAssistException {

	public NotReadyException(String msg) {
		super(msg);

	}

}
