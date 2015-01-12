package tw.com.commandline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class DeleteAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(DeleteAction.class);

	
	@SuppressWarnings("static-access")
	public DeleteAction() {
		option = OptionBuilder.withArgName("delete").hasArg().
				withDescription("The template file corresponding to stack to delete").create("delete");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, 
			Collection<Parameter> artifacts, String... args) throws InvalidStackParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
			InterruptedException, CfnAssistException, MissingArgumentException {
		String filename = args[0];
		logger.info(String.format("Attempting to delete corresponding to %s and %s", filename, projectAndEnv));
		File templateFile = new File(filename);
		AwsFacade aws = factory.createFacade();
		aws.deleteStackFrom(templateFile, projectAndEnv);	
	}
	
	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		guardForNoArtifacts(artifacts);
	}

	@Override
	public boolean usesProject() {
		return true;
	}

	@Override
	public boolean usesComment() {
		return false;
	}

	@Override
	public boolean usesSNS() {
		return true;
	}

}
