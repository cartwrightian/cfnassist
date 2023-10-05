package tw.com.ant;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.ElbAction;
import tw.com.commandline.actions.UpdateTargetGroupAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class TargetGroupUpdateElement implements ActionElement {

	private String typeTag;
	private String port;

	public void setTypeTag(String typeTag) {
		this.typeTag = typeTag;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public TargetGroupUpdateElement() {
	}

	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
						Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws IOException,
			InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		UpdateTargetGroupAction actionToInvoke = new UpdateTargetGroupAction();
		
		actionToInvoke.validate(projectAndEnv, cfnParams, artifacts, typeTag, port);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams, artifacts, typeTag, port);

	}

}
