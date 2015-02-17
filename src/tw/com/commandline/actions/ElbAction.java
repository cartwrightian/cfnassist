package tw.com.commandline.actions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.exceptions.WrongNumberOfStacksException;
import com.amazonaws.services.cloudformation.model.Parameter;

public class ElbAction extends SharedAction {

	@SuppressWarnings("static-access")
	public ElbAction() {
		option = OptionBuilder.withArgName("elbUpdate").hasArg().
				withDescription("Update elb to point at instances tagged with build").create("elbUpdate");
	}
	
	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... args)
			throws InvalidStackParameterException, FileNotFoundException,
			IOException, WrongNumberOfStacksException, InterruptedException,
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
