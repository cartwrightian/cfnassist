package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.WrongNumberOfStacksException;

public class DirAction implements CommandLineAction {
	private static final Logger logger = LoggerFactory.getLogger(DirAction.class);

	private Option option;

	@SuppressWarnings("static-access")
	public DirAction() {
		option = OptionBuilder.withArgName("dir").hasArg().
				withDescription("The directory/folder containing delta templates to apply").create("dir");
	}

	@Override
	public Option getOption() {
		return option;
	}
	
	@Override
	public String getArgName() {
		return option.getArgName();
	}

	public void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv, String folderPath) throws FileNotFoundException, InvalidParameterException, IOException, WrongNumberOfStacksException, InterruptedException, CannotFindVpcException {
		ArrayList<String> stackNames = aws.applyTemplatesFromFolder(folderPath, projectAndEnv);
		logger.info(String.format("Created %s stacks", stackNames.size()));
		for(String name : stackNames) {
			logger.info("Created stack " +name);
		}
	}

}
