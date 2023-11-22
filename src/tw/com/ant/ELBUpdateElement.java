package tw.com.ant;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.ElbAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class ELBUpdateElement implements ActionElement {
	
	private String typeTag;
	
	public void setTypeTag(String typeTag) {
		this.typeTag = typeTag;
	}
	
	public ELBUpdateElement() {	
	}

	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
						Collection<Parameter> cfnParams)
			throws IOException,
			InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		ElbAction actionToInvoke = new ElbAction(); 
		
		actionToInvoke.validate(projectAndEnv, cfnParams, typeTag);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams, typeTag);

	}

}
