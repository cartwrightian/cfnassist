package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.WrongNumberOfStacksException;

public interface CommandLineAction {
	
	Option getOption();

	String getArgName();

	void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, 
			Collection<Parameter> artifacts, String... argument) throws InvalidParameterException, FileNotFoundException, IOException, WrongNumberOfStacksException, InterruptedException, CfnAssistException, MissingArgumentException, TooManyELBException;

	void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException;

	boolean usesProject();
	boolean usesComment();
	boolean usesSNS();

}
