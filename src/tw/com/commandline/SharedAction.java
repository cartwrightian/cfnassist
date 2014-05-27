package tw.com.commandline;

import org.apache.commons.cli.Option;

import tw.com.ProjectAndEnv;

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
			throw new CommandLineException("Build number parameter is not valid with action: " + getArgName());
		}
	}
	
}
