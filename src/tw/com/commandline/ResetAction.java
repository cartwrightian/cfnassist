package tw.com.commandline;

import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;

public class ResetAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(ResetAction.class);
	
	@SuppressWarnings("static-access")
	public ResetAction() {
		option = OptionBuilder.withArgName("reset").
				withDescription("Warning: Resets the Delta Tag "+AwsFacade.INDEX_TAG+ " to zero").create("reset");
	}
	
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, String unusedArg, Collection<Parameter> unusedParams, Collection<Parameter> artifacts) throws CannotFindVpcException, MissingArgumentException {
		logger.info("Reseting index for " + projectAndEnv);
		AwsFacade aws = factory.createFacade();
		aws.resetDeltaIndex(projectAndEnv);	
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
