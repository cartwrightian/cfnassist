package tw.com.ant;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.BlacklistAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class BlacklistElement implements ActionElement {
	private String tag;
	private String port;
	
	public BlacklistElement() {
		
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
			throws FileNotFoundException, IOException,
			InvalidStackParameterException, InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		
		BlacklistAction action = new BlacklistAction();
		
		action.validate(projectAndEnv, cfnParams, artifacts, tag, port);
		action.invoke(factory, projectAndEnv, cfnParams, artifacts, tag, port);

	}

}
