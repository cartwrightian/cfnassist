package tw.com.ant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.tools.ant.BuildException;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.FacadeFactory;
import tw.com.ProjectAndEnv;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.DirAction;
import tw.com.commandline.FileAction;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

public class TemplatesElement implements ActionElement {
	
	private File target;
	
	public TemplatesElement() {

	}

	public void setTarget(File target) {
		this.target = target;
	}
	

	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts) throws FileNotFoundException, IOException, InvalidParameterException, InterruptedException, CfnAssistException, CommandLineException, MissingArgumentException {
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
		actionToInvoke.validate(projectAndEnv, absolutePath, cfnParams, artifacts);
		actionToInvoke.invoke(factory, projectAndEnv, absolutePath, cfnParams, artifacts);		
	}
}
