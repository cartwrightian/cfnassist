package tw.com.repository;

import software.amazon.awssdk.services.cloudformation.model.*;
import tw.com.MonitorStackEvents;
import tw.com.entity.*;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.WrongNumberOfStacksException;

import java.util.Collection;
import java.util.List;

public interface StackRepository {

	List<StackEntry> getStacks();
	List<StackEntry> getStacks(EnvironmentTag envTag);
	List<StackEntry> getStacksMatching(EnvironmentTag envTag, String name);
	StackEntry getStacknameByIndex(EnvironmentTag envTag, Integer index) throws WrongNumberOfStacksException;

	StackStatus waitForStatusToChangeFrom(String stackName,
									 StackStatus currentStatus, List<StackStatus> aborts)
			throws WrongNumberOfStacksException, InterruptedException;

	List<StackEvent> getStackEvents(String stackName);
	StackStatus getStackStatus(String stackName) throws WrongNumberOfStacksException;

	StackNameAndId getStackNameAndId(String stackName) throws WrongNumberOfStacksException;
	Stack getStack(String stackName) throws WrongNumberOfStacksException;

	void createFail(StackNameAndId id) throws WrongNumberOfStacksException;
	Stack createSuccess(StackNameAndId id) throws WrongNumberOfStacksException;
	void updateFail(StackNameAndId id) throws WrongNumberOfStacksException;
	Stack updateSuccess(StackNameAndId id) throws WrongNumberOfStacksException;

	StackNameAndId updateStack(String contents, Collection<Parameter> parameters, MonitorStackEvents monitor,
							   String stackName) throws CfnAssistException;

	StackNameAndId createStack(ProjectAndEnv projAndEnv, String contents, String stackName,
			Collection<Parameter> parameters, MonitorStackEvents monitor, Tagging tagging) throws CfnAssistException;

	void deleteStack(String stackName);
	
	List<TemplateParameter> validateStackTemplate(String templateContents);


}