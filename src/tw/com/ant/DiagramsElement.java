package tw.com.ant;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.tools.ant.BuildException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.CreateDiagramAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class DiagramsElement implements ActionElement {
	private File target;
	
	public DiagramsElement() {
		
	}

	public void setTarget(File target) {
		this.target = target;
	}
	
	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws IOException, InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		String path = target.getAbsolutePath();

		if (!target.isDirectory()) {
			throw new BuildException(
					String.format("Target %s was not a directory, please provide a directory for the diagrams to be saved into", path));
		}
		
		CreateDiagramAction action = new CreateDiagramAction();
		
		action.validate(projectAndEnv, cfnParams, artifacts, path);
		action.invoke(factory, projectAndEnv, cfnParams, artifacts, path);

	}

}
