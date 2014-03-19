package tw.com;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

public interface MonitorStackEvents {

	String waitForCreateFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed, NotReadyException, WrongStackStatus;
	String waitForDeleteFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus;
	String waitForRollbackComplete(StackId id) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException;
	void init() throws MissingArgumentException;
	
}
