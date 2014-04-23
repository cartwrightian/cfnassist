package tw.com.ant;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.ProjectAndEnv;
import tw.com.commandline.CommandLineException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;

public interface ActionElement {

	public abstract void execute(AwsFacade aws, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, ELBRepository repository)
			throws FileNotFoundException, IOException,
			InvalidParameterException, InterruptedException,
			CfnAssistException, CommandLineException;

}