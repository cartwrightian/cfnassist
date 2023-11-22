package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class UpdateTargetGroupAction extends SharedAction {

	@SuppressWarnings("static-access")
	public UpdateTargetGroupAction() {
		createOptionWithArgs("targetGroupUpdate", "Update target group to point at instances tagged with build", 2);
	}
	
	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
					   String... args)
			throws IOException, InterruptedException,
			CfnAssistException, MissingArgumentException {

		AwsFacade facade = factory.createFacade();
		int port = Integer.parseInt(args[1]);
		facade.updateTargetGroupToInstancesMatchingBuild(projectAndEnv, args[0], port);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                         String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		if (!projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException("You must provide the build parameter");
		}

		try {
			Integer.parseInt(argumentForAction[1]);
		}
		catch (NumberFormatException exception) {
			throw new CommandLineException(exception.getMessage());
		}
	}

	@Override
	public boolean usesProject() {
		return true;
	}

	@Override
	public boolean usesComment() {
		return true;
	}

	@Override
	public boolean usesSNS() {
		return false;
	}

}
