package tw.com.exceptions;


@SuppressWarnings("serial")
public class WrongNumberOfStacksException extends CfnAssistException {

	private int expectedNumber;
	private int actualNumber;

	public WrongNumberOfStacksException(int expectedNumber, int actualNumber) {
		this.expectedNumber = expectedNumber;
		this.actualNumber = actualNumber;
	}
	
	@Override
	public String toString() {
		return "Expected " + expectedNumber + " stacks, but got " + actualNumber;	
	}
	
}
