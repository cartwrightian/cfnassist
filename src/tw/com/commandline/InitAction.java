package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

public class InitAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(InitAction.class);
	
	@SuppressWarnings("static-access")
	public InitAction() {
		option = OptionBuilder.withArgName("init").hasArg().
					withDescription("Warning: Initialise a VPC to set up tags, provide VPC Id").create("init");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,  String vpcId,
			Collection<Parameter> unused, Collection<Parameter> artifacts) throws InvalidParameterException,
			FileNotFoundException, IOException, InterruptedException, CfnAssistException, MissingArgumentException {
		logger.info("Invoke init of tags for VPC: " + vpcId);
		AwsFacade aws = factory.createFacade();
		aws.initEnvAndProjectForVPC(vpcId, projectAndEnv);		
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
