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

public class RemoveLogsAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(RemoveLogsAction.class);

	@SuppressWarnings("static-access")
	public RemoveLogsAction() {
        createOptionWithArgs("removeLogs", "Warning: Deletes cloudwatch logs older than N days", 1);
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unused,
					   Collection<Parameter> artifacts, String... args) throws CfnAssistException, MissingArgumentException, InterruptedException {
		logger.info("Invoking removeLogs for " + projectAndEnv + " and " + args[0]);
		AwsFacade aws = factory.createFacade();
		try {
            int days = Integer.parseInt(args[0]);
			aws.removeCloudWatchLogsOlderThan(projectAndEnv, days);
        }
        catch (NumberFormatException parseFailed) {
		    throw new CfnAssistException("Unable to parse number of weeks " + args[0]);
        }
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction)
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
