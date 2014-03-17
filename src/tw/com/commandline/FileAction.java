package tw.com.commandline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.ProjectAndEnv;
import tw.com.StackId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

public class FileAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(FileAction.class);
	
	@SuppressWarnings("static-access")
	public FileAction() {
		option = OptionBuilder.withArgName("file").hasArg().withDescription("The single template file to apply").create("file");
	}
	
	public void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv, String filename, Collection<Parameter> cfnParams) throws FileNotFoundException, IOException, CfnAssistException, InterruptedException, InvalidParameterException {
		File templateFile = new File(filename);
		StackId stackId = aws.applyTemplate(templateFile, projectAndEnv, cfnParams);
		logger.info("Created stack name "+stackId);
	}
	

	@Override
	public void validate(AwsFacade aws, ProjectAndEnv projectAndEnv,
			String argumentForAction, Collection<Parameter> cfnParams) {
		// all parameters are valid with this action
	}

}
