package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.Option;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.CfnAssistException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.StackCreateFailed;
import tw.com.WrongNumberOfStacksException;

public interface CommandLineAction {
	
	Option getOption();

	String getArgName();

	void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv, String argument, Collection<Parameter> cfnParams) throws InvalidParameterException, FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, StackCreateFailed, CfnAssistException;

	void validate(AwsFacade aws, ProjectAndEnv projectAndEnv,
			String argumentForAction, Collection<Parameter> cfnParams) throws CommandLineException;

}
