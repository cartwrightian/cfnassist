package tw.com.ant;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.AllowHostAction;
import tw.com.commandline.actions.BlockHostAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.util.Collection;

public class BlockhostElement implements ActionElement {
	private String tag;
	private String port;
    private String host;

    public BlockhostElement() {
		
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
		
		BlockHostAction action = new BlockHostAction();
		
		action.validate(projectAndEnv, cfnParams, artifacts, tag, host, port);
		action.invoke(factory, projectAndEnv, cfnParams, artifacts, tag, host, port);

	}

}
