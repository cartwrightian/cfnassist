package tw.com.commandline;

import java.util.Collection;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;

public class RollbackAction implements CommandLineAction {
	private static final Logger logger = LoggerFactory.getLogger(RollbackAction.class);

	private Option option;
	
	@SuppressWarnings("static-access")
	public RollbackAction() {
		option = OptionBuilder.withArgName("rollback").hasArg().
					withDescription("Warning: Rollback all current deltas and reset index accordingly").create("rollback");
	}

	@Override
	public Option getOption() {
		return option;
	}
	
	@Override
	public String getArgName() {
		return option.getArgName();
	}
	
	public void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv, String folder, Collection<Parameter> unused) throws InvalidParameterException, CannotFindVpcException {
		logger.info("Invoking rollback for " + projectAndEnv);
		aws.rollbackTemplatesInFolder(folder, projectAndEnv);
	}

}
