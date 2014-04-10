package tw.com.exceptions;

@SuppressWarnings("serial")
public class WrongNumberOfInstancesException extends CfnAssistException {

	private String id;
	private int number;

	public WrongNumberOfInstancesException(String id, int number) {
		super(String.format("Found wrong number of instances (%s) for instance id: %s", number, id));
		this.id = id;
		this.number = number;
	}

	public String getId() {
		return id;
	}

	public int getNumber() {
		return number;
	}

}
