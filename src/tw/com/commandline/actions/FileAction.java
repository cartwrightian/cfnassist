package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class FileAction extends SharedAction {
	static final Logger logger = LoggerFactory.getLogger(FileAction.class);
	
	@SuppressWarnings("static-access")
	public FileAction() {
		createOptionWithArg("file", "The single template file to apply");
	}
	
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
					   String... args) throws IOException, CfnAssistException, InterruptedException, MissingArgumentException {
		File templateFile = new File(args[0]);
		AwsFacade aws = factory.createFacade();
		StackNameAndId stackId = aws.applyTemplate(templateFile, projectAndEnv, cfnParams);
		logger.info("Created stack name "+stackId);
	}
	
	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
						 String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
	}

	@Override
	public boolean usesProject() {
		return true;
	}

	@Override
	public boolean usesComment() {
		return true;
	}

	@Override
	public boolean usesSNS() {
		return true;
	}

}
