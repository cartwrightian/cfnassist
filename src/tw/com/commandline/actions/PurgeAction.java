package tw.com.commandline.actions;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.util.Collection;

public class PurgeAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(PurgeAction.class);

	@SuppressWarnings("static-access")
	public PurgeAction() {
		createOption("purge",
                "Warning: Rollback all current deltas and reset index accordingly. DEPRECATED: use purge");
	}

    public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unused,
			Collection<Parameter> artifacts, String... args) throws CfnAssistException, MissingArgumentException, InterruptedException {
		logger.info("Invoking rollback for " + projectAndEnv);
		AwsFacade aws = factory.createFacade();
		aws.rollbackTemplatesByIndexTag(projectAndEnv);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction)
			throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		guardForNoBuildNumber(projectAndEnv);	
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
