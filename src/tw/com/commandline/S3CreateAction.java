package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;

import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class S3CreateAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public S3CreateAction() {
		option = OptionBuilder.withArgName("s3create").withDescription("Create artifacts in S3").create("s3create");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			String argument, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts) throws InvalidParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
			InterruptedException, CfnAssistException, MissingArgumentException {
		super.uploadArtifacts(factory, projectAndEnv, artifacts, cfnParams);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, String argumentForAction,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws CommandLineException {
		super.guardForArtifactAndRequiredParams(projectAndEnv, artifacts);
	}

}
