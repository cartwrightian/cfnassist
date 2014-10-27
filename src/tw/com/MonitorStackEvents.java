package tw.com;

import java.util.List;

import org.apache.commons.cli.MissingArgumentException;

import com.amazonaws.services.cloudformation.model.CreateStackRequest;

import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

public interface MonitorStackEvents {
	String waitForCreateFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus;
	String waitForDeleteFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus;
	String waitForRollbackComplete(StackNameAndId id) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException;
	String waitForUpdateFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, WrongStackStatus, NotReadyException;
	List<String> waitForDeleteFinished(DeletionsPending pending, SetsDeltaIndex setsDeltaIndex) throws CfnAssistException;
	
	void init() throws MissingArgumentException, CfnAssistException, InterruptedException;
	
	void addMonitoringTo(CreateStackRequest createStackRequest) throws NotReadyException;
}
