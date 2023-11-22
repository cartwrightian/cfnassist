package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class DeleteAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(DeleteAction.class);

	@SuppressWarnings("static-access")
	public DeleteAction() {
		createOptionWithArg("delete", "The template file corresponding to stack to delete");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
					   String... args) throws
			IOException,
			InterruptedException, CfnAssistException, MissingArgumentException {
		String filename = args[0];
		logger.info(String.format("Attempting to delete corresponding to %s and %s", filename, projectAndEnv));
		File templateFile = new File(filename);
		AwsFacade aws = factory.createFacade();
		aws.deleteStackFrom(templateFile, projectAndEnv);	
	}
	
	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                         String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
		// caused nested deleted in ant task to fail, passing artifacts causes no harm/action so comment out for now
		//guardForNoArtifacts(artifacts);
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
