package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class S3CreateAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public S3CreateAction() {
		createOption("s3create", "Create artifacts in S3");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argument) throws
            IOException,
            InterruptedException, CfnAssistException, MissingArgumentException {
		super.uploadArtifacts(factory, projectAndEnv, artifacts, cfnParams);
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
		return false;
	}

}
