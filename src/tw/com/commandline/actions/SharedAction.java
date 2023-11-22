package tw.com.commandline.actions;

import org.apache.commons.cli.Option;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;

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

	protected void createOption(String optName, String description) {
		option = Option.builder(optName).argName(optName).desc(description).build();
	}

    protected void createOptionWithArg(String name, String description) {
        option =  Option.builder(name).argName(name).desc(description).hasArg().build();
    }

    protected void createOptionWithArgs(String name, String description, int numberOfArgs) {
        option =  Option.builder(name).argName(name).desc(description).hasArgs().numberOfArgs(numberOfArgs).build();
    }

	protected void createOptionalWithOptionalArg(String name, String description) {
		option =  Option.builder(name).argName(name).desc(description).hasArg().optionalArg(true).build();
	}

	protected void guardForProjectAndEnv(ProjectAndEnv projectAndEnv)
			throws CommandLineException {
		guardForProject(projectAndEnv);
		guardForEnv(projectAndEnv);
	}

	private void guardForEnv(ProjectAndEnv projectAndEnv)
			throws CommandLineException {
		if (!projectAndEnv.hasEnv()) {
			throw new CommandLineException("Must provide env");
		}
	}

	protected void guardForProject(ProjectAndEnv projectAndEnv)
			throws CommandLineException {
		if (!projectAndEnv.hasProject()) {
			throw new CommandLineException("Must provide project");
		}
	}

	protected void guardForNoBuildNumber(ProjectAndEnv projectAndEnv)
			throws CommandLineException {
		if (projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException(
					"Build number parameter is not valid with action: "
							+ getArgName());
		}
	}

	protected void guardForBuildNumber(ProjectAndEnv projectAndEnv)
			throws CommandLineException {
		if (!projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException(
					"You must provide build number if you specify artitacts");
		}
	}

	protected void guardForSNSNotSet(ProjectAndEnv projectAndEnv) throws CommandLineException {
		if (projectAndEnv.useSNS()) {
			throw new CommandLineException("Setting sns does not work with this action");
		}
		
	}
}
