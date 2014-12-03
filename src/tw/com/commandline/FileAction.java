package tw.com.commandline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

public class FileAction extends SharedAction {
	static final Logger logger = LoggerFactory.getLogger(FileAction.class);
	
	@SuppressWarnings("static-access")
	public FileAction() {
		option = OptionBuilder.withArgName("file").hasArg().withDescription("The single template file to apply").create("file");
	}
	
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, 
			Collection<Parameter> artifacts, String... args) throws FileNotFoundException, IOException, CfnAssistException, InterruptedException, InvalidParameterException, MissingArgumentException {
		File templateFile = new File(args[0]);
		AwsFacade aws = factory.createFacade();
		uploadArtifacts(factory, projectAndEnv, artifacts, cfnParams);
		StackNameAndId stackId = aws.applyTemplate(templateFile, projectAndEnv, cfnParams);
		logger.info("Created stack name "+stackId);
	}
	
	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		guardForArtifactAndRequiredParams(projectAndEnv, artifacts);
	}

}
