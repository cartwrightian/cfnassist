package tw.com.ant;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.ElbAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class ELBUpdateElement implements ActionElement {
	
	private String typeTag;
	
	public void setTypeTag(String typeTag) {
		this.typeTag = typeTag;
	}
	
	public ELBUpdateElement() {	
	}

	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams,Collection<Parameter> artifacts)
			throws FileNotFoundException, IOException,
			InvalidStackParameterException, InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		ElbAction actionToInvoke = new ElbAction(); 
		
		actionToInvoke.validate(projectAndEnv, cfnParams, artifacts, typeTag);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams, artifacts, typeTag);

	}

}
