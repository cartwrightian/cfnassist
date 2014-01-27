package tw.com.commandline;

import java.util.Collection;

import org.apache.commons.cli.Option;

import tw.com.AwsFacade;
import tw.com.ProjectAndEnv;

import com.amazonaws.services.cloudformation.model.Parameter;

public abstract class SharedAction implements CommandLineAction {

	protected Option option;
	
	@Override
	public Option getOption() {
		return option;
	}

	@Override
	public String getArgName() {
		return option.getArgName();
	}
	
	@Override
	public void validate(AwsFacade aws, ProjectAndEnv projectAndEnv,
			String argumentForAction, Collection<Parameter> cfnParams) throws CommandLineException {
		if (projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException("Build number parameter is not valid with action: " + getArgName());
		}
	}

}
