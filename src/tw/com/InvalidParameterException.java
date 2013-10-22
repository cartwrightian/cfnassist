package tw.com;

@SuppressWarnings("serial")
public class InvalidParameterException extends Exception {

	public InvalidParameterException(String parameterName)  {
		super(parameterName + " is invalid");
	}

}
