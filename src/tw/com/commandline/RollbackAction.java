package tw.com.commandline;

import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

public class RollbackAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(RollbackAction.class);

	@SuppressWarnings("static-access")
	public RollbackAction() {
		option = OptionBuilder.withArgName("rollback").hasArg().
					withDescription("Warning: Rollback all current deltas and reset index accordingly").create("rollback");
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, String folder, Collection<Parameter> unused, Collection<Parameter> artifacts) throws InvalidParameterException, CfnAssistException, MissingArgumentException, InterruptedException {
		logger.info("Invoking rollback for " + projectAndEnv);
		AwsFacade aws = factory.createFacade();
		aws.rollbackTemplatesInFolder(folder, projectAndEnv);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, String argumentForAction,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		guardForNoBuildNumber(projectAndEnv);	
		guardForNoArtifacts(artifacts);
	}

}
