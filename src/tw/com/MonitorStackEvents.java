package tw.com;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.exceptions.StackCreateFailed;
import tw.com.exceptions.WrongNumberOfStacksException;

public interface MonitorStackEvents {

	String waitForCreateFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, StackCreateFailed, NotReadyException;
	String waitForDeleteFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException;
	void init() throws MissingArgumentException;
}
