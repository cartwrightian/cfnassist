package tw.com.exceptions;


@SuppressWarnings("serial")
public class WrongNumberOfStacksException extends CfnAssistException {

	public WrongNumberOfStacksException(int expectedNumber, int actualNumber) {
		super("Expected " + expectedNumber + " stacks, but got " + actualNumber);
	}
	
}
