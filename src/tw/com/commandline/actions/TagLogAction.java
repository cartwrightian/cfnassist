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

import java.util.Collection;

public class TagLogAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(TagLogAction.class);

	@SuppressWarnings("static-access")
	public TagLogAction() {
        createOptionWithArgs("tagLog", "Tags the named group with current project and env", 1);
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unused,
					   String... args) throws CfnAssistException, MissingArgumentException, InterruptedException {
		logger.info("Invoking removeLogs for " + projectAndEnv + " and " + args[0]);
		AwsFacade aws = factory.createFacade();
		String groupName = args[0];
		aws.tagCloudWatchLog(projectAndEnv, groupName);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                         String... argumentForAction)
			throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
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
