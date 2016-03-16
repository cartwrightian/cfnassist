package tw.com.ant;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.S3CreateAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class S3Create implements ActionElement {
	
	public S3Create() {
	}

	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws IOException,
			InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		
		S3CreateAction action = new S3CreateAction();
		
		action.validate(projectAndEnv, cfnParams, artifacts, "");
		action.invoke(factory, projectAndEnv, cfnParams, artifacts, "");	
	}

}
