package tw.com;

import java.util.List;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

public interface MonitorStackEvents {

	String waitForCreateFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus;
	String waitForDeleteFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus;
	String waitForRollbackComplete(StackId id) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException;
	String waitForUpdateFinished(StackId id) throws WrongNumberOfStacksException, InterruptedException, WrongStackStatus, NotReadyException;
	void init() throws MissingArgumentException;
	List<String> waitForDeleteFinished(DeletionsPending pending, SetsDeltaIndex setsDeltaIndex) throws CfnAssistException;

}
