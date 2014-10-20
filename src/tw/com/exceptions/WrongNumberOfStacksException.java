package tw.com.exceptions;


@SuppressWarnings("serial")
public class WrongNumberOfStacksException extends CfnAssistException {

	private int actualNumber;

	public WrongNumberOfStacksException(int expectedNumber, int actualNumber) {
		super("Expected " + expectedNumber + " stacks, but got " + actualNumber);
		this.actualNumber = actualNumber;
	}

	public int getNumber() {
		return actualNumber;
	}
	
}
