package tw.com.ant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.tools.ant.BuildException;

import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.DeleteAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class DeleteElement implements ActionElement {
	private File target;
	
	public DeleteElement() {

	}

	public void setTarget(File target) {
		this.target = target;
	}
	
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts) 
			throws FileNotFoundException, IOException, InvalidStackParameterException, InterruptedException, 
			CfnAssistException, CommandLineException, MissingArgumentException {
		String absolutePath = target.getAbsolutePath();
		
		if (!target.isFile()) {
			throw new BuildException("Cannot invoke delete for a directory, use Rollbackif you want to roll back all deltas in a folder");
		}
		
		CommandLineAction actionToInvoke = new DeleteAction();

		actionToInvoke.validate(projectAndEnv, cfnParams, artifacts, absolutePath);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams, artifacts, absolutePath);		
	}

}
