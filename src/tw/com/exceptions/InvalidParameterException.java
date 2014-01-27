package tw.com.exceptions;

@SuppressWarnings("serial")
public class InvalidParameterException extends Exception {

	public InvalidParameterException(String parameterName)  {
		super(parameterName + " is invalid");
	}

}
