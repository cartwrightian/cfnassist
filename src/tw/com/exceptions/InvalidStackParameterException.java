package tw.com.exceptions;

@SuppressWarnings("serial")
public class InvalidStackParameterException extends CfnAssistException {

	public InvalidStackParameterException(String parameterName)  {
		super(parameterName + " is invalid");
	}

}
