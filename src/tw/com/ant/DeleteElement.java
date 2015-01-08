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
import tw.com.commandline.DeleteAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class DeleteElement implements ActionElement {
	private File target;
	
	public DeleteElement() {

	}

	public void setTarget(File target) {
		this.target = target;
	}
	
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts) 
			throws FileNotFoundException, IOException, InvalidParameterException, InterruptedException, 
			CfnAssistException, CommandLineException, MissingArgumentException {
		String absolutePath = target.getAbsolutePath();
		
		if (!target.isFile()) {
			throw new BuildException("Cannot invoke delete for a directory, use Rollback");
		}
		
		CommandLineAction actionToInvoke = new DeleteAction();

		actionToInvoke.validate(projectAndEnv, cfnParams, artifacts, absolutePath);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams, artifacts, absolutePath);		
	}

}
