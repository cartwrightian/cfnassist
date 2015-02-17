package tw.com.commandline.actions;

import java.util.Collection;

import org.apache.commons.cli.Option;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.ArtifactUploader;

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
