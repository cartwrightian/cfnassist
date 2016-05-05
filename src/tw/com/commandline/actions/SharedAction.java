package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.Option;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.ArtifactUploader;

import java.util.Collection;

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

	protected void guardForNoArtifacts(Collection<Parameter> artifacts)
			throws CommandLineException {
		if (!artifacts.isEmpty()) {
			throw new CommandLineException(
					"artifacts are not valid with action: " + getArgName());
		}
	}

	protected void guardForBucketName(ProjectAndEnv projectAndEnv)
			throws CommandLineException {
		if (!projectAndEnv.hasBucketName()) {
			throw new CommandLineException(
					"You must provide bucket name if you specify artitacts");
		}
	}

	protected void guardForBuildNumber(ProjectAndEnv projectAndEnv)
			throws CommandLineException {
		if (!projectAndEnv.hasBuildNumber()) {
			throw new CommandLineException(
					"You must provide build number if you specify artitacts");
		}
	}

	protected void guardForArtifactAndRequiredParams(
			ProjectAndEnv projectAndEnv, Collection<Parameter> artifacts)
			throws CommandLineException {
		if (!artifacts.isEmpty()) {
			guardForBuildNumber(projectAndEnv);
			guardForBucketName(projectAndEnv);
		}
	}

	protected void uploadArtifacts(FacadeFactory factory,
			ProjectAndEnv projectAndEnv, Collection<Parameter> artifacts,
			Collection<Parameter> cfnParams) {
		if (artifacts.isEmpty()) {
			return;
		}

		ArtifactUploader artifactUploader = factory.createArtifactUploader(projectAndEnv);
		cfnParams.addAll(artifactUploader.uploadArtifacts(artifacts));
	}

	protected void guardForSNSNotSet(ProjectAndEnv projectAndEnv) throws CommandLineException {
		if (projectAndEnv.useSNS()) {
			throw new CommandLineException("Setting sns does not work with this action");
		}
		
	}
}
