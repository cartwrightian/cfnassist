package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class ElbAction extends SharedAction {

	@SuppressWarnings("static-access")
	public ElbAction() {
		createOptionWithArg("elbUpdate", "Update elb to point at instances tagged with build");
	}
	
	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... args)
			throws IOException, InterruptedException,
			CfnAssistException, MissingArgumentException {

		AwsFacade facade = factory.createFacade();
		facade.updateELBToInstancesMatchingBuild(projectAndEnv, args[0]);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		guardForNoArtifacts(artifacts);
		if (!projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException("You must provide the build parameter");
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
