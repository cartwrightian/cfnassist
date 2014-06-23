package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.OptionBuilder;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.ProjectAndEnv;
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
	public void invoke(AwsFacade aws, ELBRepository repository, ProjectAndEnv projectAndEnv,
			String typeTag, Collection<Parameter> cfnParams)
			throws InvalidParameterException, FileNotFoundException,
			IOException, WrongNumberOfStacksException, InterruptedException,
			CfnAssistException {
		
		repository.updateInstancesMatchingBuild(projectAndEnv, typeTag);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, String argumentForAction,
			Collection<Parameter> cfnParams) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		if (!projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException("You must provide the build parameter");
		}
	}

}
