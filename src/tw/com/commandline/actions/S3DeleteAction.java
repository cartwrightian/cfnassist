package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.ArtifactUploader;

import java.io.IOException;
import java.util.Collection;

public class S3DeleteAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public S3DeleteAction() {
        createOption("s3delete", "Create artifacts in S3");
    }

    @Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argument) throws
            IOException, InterruptedException, CfnAssistException, MissingArgumentException {
		ArtifactUploader uploader = factory.createArtifactUploader(projectAndEnv);
		for(Parameter item : artifacts) {
			uploader.delete(item.parameterValue());
		}
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction)
			throws CommandLineException {
		super.guardForArtifactAndRequiredParams(projectAndEnv, artifacts);
		super.guardForSNSNotSet(projectAndEnv);
	}

	@Override
	public boolean usesProject() {
		return true;
	}

	@Override
	public boolean usesComment() {
		return false;
	}

	@Override
	public boolean usesSNS() {
		return true;
	}

}
