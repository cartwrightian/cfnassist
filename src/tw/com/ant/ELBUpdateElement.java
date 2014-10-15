package tw.com.ant;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.ElbAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

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
			InvalidParameterException, InterruptedException,
			CfnAssistException, CommandLineException {
		ElbAction actionToInvoke = new ElbAction(); 
		
		actionToInvoke.validate(projectAndEnv, typeTag, cfnParams, artifacts);
		actionToInvoke.invoke(factory, projectAndEnv, typeTag, cfnParams, artifacts);

	}

}
