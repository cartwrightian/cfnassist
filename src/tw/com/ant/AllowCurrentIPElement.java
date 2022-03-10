package tw.com.ant;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.AllowCurrentIPAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class AllowCurrentIPElement implements ActionElement {
	private String tag;
	private String port;
	
	public AllowCurrentIPElement() {
		
	}

	public void setTag(String tag) {
		this.tag = tag;
	}
	
	public void setPort(String port) {
		this.port = port;
	}
	
	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws IOException,
			InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		
		AllowCurrentIPAction action = new AllowCurrentIPAction();
		
		action.validate(projectAndEnv, cfnParams, artifacts, tag, port);
		action.invoke(factory, projectAndEnv, cfnParams, artifacts, tag, port);

	}

}
