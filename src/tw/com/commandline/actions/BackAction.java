package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.util.Collection;

public class BackAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(BackAction.class);

	@SuppressWarnings("static-access")
	public BackAction() {
		createOption("back",
                "Warning: Remove last delta and reset index accordingly.");
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unused,
					   String... args) throws CfnAssistException, MissingArgumentException, InterruptedException {
		logger.info("Invoking stepback for " + projectAndEnv);
		AwsFacade aws = factory.createFacade();
		aws.stepbackLastChange(projectAndEnv);
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
		return false;
	}

	@Override
	public boolean usesSNS() {
		return true;
	}

}
