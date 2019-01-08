package tw.com.ant;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.AllowHostAction;
import tw.com.commandline.actions.WhitelistAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class AllowhostElement implements ActionElement {
	private String tag;
	private String port;
    private String host;

    public AllowhostElement() {
		
	}

	public void setHost(String host) { this.host = host; }

	public void setTag(String tag) {
		this.tag = tag;
	}
	
	public void setPort(String port) {
		this.port = port;
	}
	
	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws
            InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		
		AllowHostAction action = new AllowHostAction();
		
		action.validate(projectAndEnv, cfnParams, artifacts, tag, host, port);
		action.invoke(factory, projectAndEnv, cfnParams, artifacts, tag, host, port);

	}

}
