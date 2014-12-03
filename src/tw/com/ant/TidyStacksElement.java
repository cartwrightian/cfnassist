package tw.com.ant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;

import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.TidyOldStacksAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class TidyStacksElement implements ActionElement {
	private File target;
	private String typeTag;

	public void setTarget(File target) {
		this.target = target;
	}
	
	public void setTypeTag(String typeTag) {
		this.typeTag = typeTag;
	}
	
	public TidyStacksElement() {
	}

	@Override
	public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws FileNotFoundException, IOException,
			InvalidParameterException, InterruptedException,
			CfnAssistException, CommandLineException, MissingArgumentException {
		
		TidyOldStacksAction actionToInvoke = new TidyOldStacksAction();
		String absolutePath = target.getAbsolutePath();

		actionToInvoke.validate(projectAndEnv, cfnParams, artifacts, absolutePath, typeTag);
		actionToInvoke.invoke(factory, projectAndEnv, cfnParams, artifacts, absolutePath, typeTag);

	}

}
