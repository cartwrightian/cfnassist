package tw.com;

import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

public interface MonitorStackEvents {

	String waitForCreateFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed;
	String waitForDeleteFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException;
}
