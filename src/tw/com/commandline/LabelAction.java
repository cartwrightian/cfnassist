package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.CfnAssistException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
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
	public void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv,
			String stackname, Collection<Parameter> cfnParams)
			throws InvalidParameterException, FileNotFoundException,
			IOException, InterruptedException, CfnAssistException {
		logger.info("Invoke label of existing stack: " + stackname);
		aws.initEnvAndProjectForStack(stackname, projectAndEnv);
	}

}
