package tw.com.ant;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.BlockCurrentIPAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class BlockCurrentIPElement implements ActionElement {
	private String tag;
	private String port;
	
	public BlockCurrentIPElement() {
		
	}

	public void setTag(String tag) {
		this.tag = tag;
	}
	
	public void setPort(String port) {
		this.port = port;
	}
	
	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams)
			throws IOException, InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		
		BlockCurrentIPAction action = new BlockCurrentIPAction();
		
		action.validate(projectAndEnv, cfnParams, tag, port);
		action.invoke(factory, projectAndEnv, cfnParams, tag, port);

	}

}
