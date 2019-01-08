package tw.com;

import java.util.List;

import org.apache.commons.cli.MissingArgumentException;

import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;

public interface MonitorStackEvents {
	StackStatus waitForCreateFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus;
	StackStatus waitForDeleteFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, NotReadyException, WrongStackStatus;
	StackStatus waitForRollbackComplete(StackNameAndId id) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException;
	StackStatus waitForUpdateFinished(StackNameAndId id) throws WrongNumberOfStacksException, InterruptedException, WrongStackStatus, NotReadyException;
	List<String> waitForDeleteFinished(DeletionsPending pending, SetsDeltaIndex setsDeltaIndex) throws CfnAssistException;
	
	void init() throws MissingArgumentException, CfnAssistException, InterruptedException;
	
	void addMonitoringTo(CreateStackRequest.Builder createStackRequest) throws NotReadyException;
	void addMonitoringTo(UpdateStackRequest.Builder updateStackRequest) throws NotReadyException;
}
