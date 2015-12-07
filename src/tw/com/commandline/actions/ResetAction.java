package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.util.Collection;

public class ResetAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(ResetAction.class);
	
	@SuppressWarnings("static-access")
	public ResetAction() {
		createOption("reset", "Warning: Resets the Delta Tag "+AwsFacade.INDEX_TAG+ " to zero");
	}
	
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unusedParams, Collection<Parameter> artifacts, String... unusedArg) throws MissingArgumentException, CfnAssistException, InterruptedException {
		logger.info("Reseting index for " + projectAndEnv);
		AwsFacade aws = factory.createFacade();
		aws.resetDeltaIndex(projectAndEnv);	
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
		return false;
	}

}
