package tw.com.ant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.tools.ant.BuildException;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.CfnAssistException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.StackCreateFailed;
import tw.com.WrongNumberOfStacksException;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.DirAction;
import tw.com.commandline.FileAction;

public class TemplatesElement {
	
	private File target;
	
	public TemplatesElement() {

	}

	public void setTarget(File target) {
		this.target = target;
	}
	
	public void execute(AwsFacade aws, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams) throws FileNotFoundException, IOException, InvalidParameterException, InterruptedException, CfnAssistException, CommandLineException {
		String absolutePath = target.getAbsolutePath();
		CommandLineAction actionToInvoke = null;
		if (target.isDirectory()) {
			actionToInvoke = new DirAction();
		} else if (target.isFile()) {
			actionToInvoke = new FileAction();
		} 
		
		if (actionToInvoke==null) {
			throw new BuildException("Unable to action on path, expect file or folder, path was: " + absolutePath);
		} 
		actionToInvoke.validate(aws, projectAndEnv, absolutePath, cfnParams);
		actionToInvoke.invoke(aws, projectAndEnv, absolutePath,cfnParams);		
	}
}
