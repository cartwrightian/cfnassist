package tw.com.ant;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.tools.ant.BuildException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.DirAction;
import tw.com.commandline.actions.FileAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class TemplatesElement implements ActionElement {
	
	private File target;
	
	public TemplatesElement() {

	}

	public void setTarget(File target) {
		this.target = target;
	}
	

	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams)
			throws IOException, InterruptedException, CfnAssistException,
			CommandLineException, MissingArgumentException {
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
		actionToInvoke.validate(projectAndEnv, cfnParams, absolutePath);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams, absolutePath);
	}
}
