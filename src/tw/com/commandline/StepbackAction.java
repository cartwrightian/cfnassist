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
import tw.com.exceptions.InvalidStackParameterException;

public class StepbackAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(StepbackAction.class);

	@SuppressWarnings("static-access")
	public StepbackAction() {
		option = OptionBuilder.withArgName("stepback").hasArg().
					withDescription("Warning: Remove last delta and reset index accordingly").create("stepback");
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unused, 
			Collection<Parameter> artifacts, String... args) throws InvalidStackParameterException, CfnAssistException, MissingArgumentException, InterruptedException {
		String folder = args[0];
		logger.info("Invoking stepback for " + projectAndEnv + " and folder " + folder);
		AwsFacade aws = factory.createFacade();
		aws.stepbackLastChange(folder, projectAndEnv);
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
