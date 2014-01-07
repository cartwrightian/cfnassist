package tw.com.commandline;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.ProjectAndEnv;

public class ResetAction implements CommandLineAction {
	private static final Logger logger = LoggerFactory.getLogger(ResetAction.class);
	
	private Option option;

	@SuppressWarnings("static-access")
	public ResetAction() {
		option = OptionBuilder.withArgName("reset").
				withDescription("Warning: Resets the Delta Tag "+AwsFacade.INDEX_TAG+ " to zero").create("reset");
	}

	@Override
	public Option getOption() {
		return option;
	}
	
	@Override
	public String getArgName() {
		return option.getArgName();
	}
	
	public void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv, String unused) throws CannotFindVpcException {
		logger.info("Reseting index for " + projectAndEnv);
		aws.resetDeltaIndex(projectAndEnv);	
	}

}
