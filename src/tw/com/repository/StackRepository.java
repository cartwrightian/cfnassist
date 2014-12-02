package tw.com.repository;

import java.util.Collection;
import java.util.List;

import tw.com.MonitorStackEvents;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;

public interface StackRepository {

	public abstract List<StackEntry> stacksMatchingEnvAndBuild(EnvironmentTag envTag, String buildNumber);	
	public abstract List<StackEntry> getStacks();
	public abstract List<StackEntry> getStacks(EnvironmentTag envTag);
	public abstract List<StackEntry> getStacksMatching(EnvironmentTag envTag, String string);

	public abstract String waitForStatusToChangeFrom(String stackName,
			StackStatus currentStatus, List<String> aborts)
			throws WrongNumberOfStacksException, InterruptedException;

	public abstract List<StackEvent> getStackEvents(String stackName);
	public abstract String getStackStatus(String stackName);

	public abstract StackNameAndId getStackNameAndId(String stackName) throws WrongNumberOfStacksException;
	public abstract Stack getStack(String stackName) throws WrongNumberOfStacksException;

	public abstract Stack updateRepositoryFor(StackNameAndId id) throws WrongNumberOfStacksException;

	public abstract StackNameAndId updateStack(String contents, Collection<Parameter> parameters, MonitorStackEvents monitor,
			String stackName) throws InvalidParameterException,
			WrongNumberOfStacksException, NotReadyException;

	public abstract StackNameAndId createStack(ProjectAndEnv projAndEnv, String contents, String stackName,
			Collection<Parameter> parameters, MonitorStackEvents monitor, String commentTag) throws NotReadyException;

	public abstract void deleteStack(String stackName);
	
	List<TemplateParameter> validateStackTemplate(String templateContents);

}