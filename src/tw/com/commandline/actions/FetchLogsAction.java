package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FetchLogsAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(FetchLogsAction.class);

	@SuppressWarnings("static-access")
	public FetchLogsAction() {
        createOptionWithArgs("logs", "Fetchs logs for project for last N days", 1);
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> unused, 
			Collection<Parameter> artifacts, String... args) throws CfnAssistException, MissingArgumentException, InterruptedException {
		logger.info("Invoking get logs for " + projectAndEnv + " and " + args[0]);
		AwsFacade aws = factory.createFacade();
		int days = Integer.parseInt(args[0]);

		List<Path> filenames = aws.fetchLogs(projectAndEnv, days);
		System.out.println("Logs for " + projectAndEnv);
		filenames.forEach(filename -> {
		    System.out.println(String.format("Saved file '%s'", filename.toAbsolutePath().toString()));;
        });
        System.out.flush();
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
