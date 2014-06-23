package tw.com.ant;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.ProjectAndEnv;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.ElbAction;
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
	public void execute(AwsFacade aws, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, ELBRepository repository)
			throws FileNotFoundException, IOException,
			InvalidParameterException, InterruptedException,
			CfnAssistException, CommandLineException {
		ElbAction actionToInvoke = new ElbAction(); 
		
		actionToInvoke.validate(projectAndEnv, typeTag, cfnParams);
		actionToInvoke.invoke(aws, repository, projectAndEnv, typeTag, cfnParams);

	}

}
