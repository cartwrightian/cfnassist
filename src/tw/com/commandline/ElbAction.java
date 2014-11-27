package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongNumberOfStacksException;
import com.amazonaws.services.cloudformation.model.Parameter;

public class ElbAction extends SharedAction {

	@SuppressWarnings("static-access")
	public ElbAction() {
		option = OptionBuilder.withArgName("elbUpdate").hasArg().
				withDescription("Update elb to point at instances tagged with build").create("elbUpdate");
	}
	
	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, String typeTag,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws InvalidParameterException, FileNotFoundException,
			IOException, WrongNumberOfStacksException, InterruptedException,
			CfnAssistException, MissingArgumentException {

		AwsFacade facade = factory.createFacade();
		facade.updateELBToInstancesMatchingBuild(projectAndEnv, typeTag);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, String argumentForAction,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		guardForNoArtifacts(artifacts);
		if (!projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException("You must provide the build parameter");
		}
	}

}
