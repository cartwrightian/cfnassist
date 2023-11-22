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

import java.io.IOException;
import java.util.Collection;

public class InitAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(InitAction.class);
	
	@SuppressWarnings("static-access")
	public InitAction() {
		createOptionWithArg("init", "Warning: Initialise a VPC to set up tags, provide VPC Id");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unused,
					   String... args) throws
            IOException, InterruptedException, CfnAssistException, MissingArgumentException {
		String vpcId = args[0];
		logger.info("Invoke init of tags for VPC: " + vpcId);
		AwsFacade aws = factory.createFacade();
		aws.initEnvAndProjectForVPC(vpcId, projectAndEnv);		
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
		return false;
	}

}
