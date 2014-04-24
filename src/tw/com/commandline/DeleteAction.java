package tw.com.commandline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
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
	public void invoke(AwsFacade aws, ELBRepository repository, ProjectAndEnv projectAndEnv, String filename, Collection<Parameter> cfnParams) throws InvalidParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
			InterruptedException, CfnAssistException {
		logger.info(String.format("Attempting to delete corresponding to %s and %s", filename, projectAndEnv));
		File templateFile = new File(filename);
		aws.deleteStackFrom(templateFile, projectAndEnv);	
	}
	
	@Override
	public void validate(AwsFacade aws, ProjectAndEnv projectAndEnv,
			String argumentForAction, Collection<Parameter> cfnParams) {
		// all parameters are valid with this action
	}

}
