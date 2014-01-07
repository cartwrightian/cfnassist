package tw.com.commandline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.WrongNumberOfStacksException;

public class FileAction implements CommandLineAction {
	private static final Logger logger = LoggerFactory.getLogger(FileAction.class);

	private Option option;
	
	@SuppressWarnings("static-access")
	public FileAction() {
		option = OptionBuilder.withArgName("file").hasArg().withDescription("The single template file to apply").create("file");
	}

	@Override
	public Option getOption() {
		return option;
	}

	@Override
	public String getArgName() {
		return option.getArgName();
	}
	
	public void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv, String filename) throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		File templateFile = new File(filename);
		String stackName = aws.applyTemplate(templateFile, projectAndEnv);
		logger.info("Created stack name "+stackName);
	}

}
