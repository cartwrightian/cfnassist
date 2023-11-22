package tw.com.commandline.actions;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public class DirAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(DirAction.class);

	@SuppressWarnings("static-access")
	public DirAction() {
		createOptionWithArg("dir","The directory/folder containing delta templates to apply");
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
					   String... args) throws IOException, CfnAssistException, InterruptedException, MissingArgumentException {
		AwsFacade aws = factory.createFacade();

		String folderPath = args[0];
		ArrayList<StackNameAndId> stackIds = aws.applyTemplatesFromFolder(folderPath , projectAndEnv, cfnParams);
		logger.info(String.format("Created %s stacks", stackIds.size()));
		for(StackNameAndId name : stackIds) {
			logger.info("Created stack " +name);
		}
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                         String... argumentForAction)
			throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);		
		guardForNoBuildNumber(projectAndEnv);	
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
		

