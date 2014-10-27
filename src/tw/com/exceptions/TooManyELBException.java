package tw.com.exceptions;

@SuppressWarnings("serial")
public class TooManyELBException extends CfnAssistException {

	public TooManyELBException(int found, String msg) {
		super(String.format("Found too many loadbalancer, expected one and got %s. %s",found,msg));
	}

}
