package tw.com.ant;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.PurgeAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class PurgeElement implements ActionElement {

	public PurgeElement() {
	}

	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams)
			throws IOException, InterruptedException, CfnAssistException, CommandLineException, MissingArgumentException {

		CommandLineAction actionToInvoke = new PurgeAction();

		actionToInvoke.validate(projectAndEnv, cfnParams);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams);
	}

}
