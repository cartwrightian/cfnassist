package tw.com.exceptions;

@SuppressWarnings("serial")
public class WrongStackStatus extends CfnAssistException {

	public WrongStackStatus(String msg) {
		super(msg);
		// TODO Auto-generated constructor stub
	}

	public WrongStackStatus(String requiredStatus, String actual) {
		super(String.format("Got an unexpected stack status, expected %s and got %s", requiredStatus, actual));
	}

}
