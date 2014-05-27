package tw.com.ant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.tools.ant.BuildException;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.ProjectAndEnv;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.DeleteAction;
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
	
	public void execute(AwsFacade aws, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, ELBRepository repository) throws FileNotFoundException, IOException, InvalidParameterException, InterruptedException, CfnAssistException, CommandLineException {
		String absolutePath = target.getAbsolutePath();
		
		if (!target.isFile()) {
			throw new BuildException("Cannot invoke delete for a directory, use Rollback");
		}
		
		CommandLineAction actionToInvoke = new DeleteAction();

		actionToInvoke.validate(projectAndEnv, absolutePath, cfnParams);
		actionToInvoke.invoke(aws, repository, projectAndEnv, absolutePath, cfnParams);		
	}

}
