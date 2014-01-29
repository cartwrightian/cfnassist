package tw.com;

import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

public interface MonitorStackEvents {

	String waitForCreateFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed;
	String waitForDeleteFinished(String stackName) throws WrongNumberOfStacksException, InterruptedException;
}
