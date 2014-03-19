package tw.com.exceptions;

@SuppressWarnings("serial")
public class DuplicateStackException extends CfnAssistException {

	public DuplicateStackException(String stackname) {
		super("Attempted to create a duplicated stack, name was " + stackname);
	}

}
