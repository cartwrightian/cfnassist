package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class LabelAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(LabelAction.class);
	
	@SuppressWarnings("static-access")
	public LabelAction() {
		String description = String.format("Warning: Label an existing stack with %s and %s", AwsFacade.PROJECT_TAG, AwsFacade.ENVIRONMENT_TAG);
		option = OptionBuilder.withArgName("labelstack").hasArg().
				withDescription(description).create("labelstack");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... stackname)
			throws InvalidParameterException, FileNotFoundException,
			IOException, InterruptedException, CfnAssistException {
		logger.info("Invoke label of existing stack: " + stackname);
		
		throw new CfnAssistException("Not possible with current AWS APIs");
		//aws.initEnvAndProjectForStack(stackname, projectAndEnv);
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
		// TODO Auto-generated method stub
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
