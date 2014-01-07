package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	private Option projectParam;
	private Option envParam;
	private Option fileParam;
	private Option dirParam;
	private Option regionParam;
	private Option resetParam;
	private Option rollBackParam;
	private Options commandLineOptions;
	private String[] args;
	private String executableName;

	@SuppressWarnings("static-access")
	Main(String[] args) {
		this.args = args;
		executableName = "cfnassist";
		commandLineOptions = new Options();
		projectParam = OptionBuilder.withArgName("project").hasArg().withDescription("Name of the cfnassist project").create("project");
		commandLineOptions.addOption(projectParam);
		envParam = OptionBuilder.withArgName("env").hasArg().withDescription("Name of cfnassit environment").create("env");
		commandLineOptions.addOption(envParam);
		fileParam = OptionBuilder.withArgName("file").hasArg().withDescription("The template file to apply").create("file");
		commandLineOptions.addOption(fileParam);
		dirParam = OptionBuilder.withArgName("dir").hasArg().withDescription("The directory/folder containing delta templates to apply").create("dir");
		commandLineOptions.addOption(dirParam);
		regionParam = OptionBuilder.withArgName("region").hasArg().withDescription("AWS Region name").create("region");
		commandLineOptions.addOption(regionParam);
		resetParam = OptionBuilder.withArgName("reset").withDescription("Warning: Resets the Delta Tag "+AwsFacade.INDEX_TAG).create("reset");
		commandLineOptions.addOption(resetParam);
		rollBackParam = OptionBuilder.withArgName("rollback").hasArg().withDescription("Warning: Rollback all current deltas").create("rollback");
		commandLineOptions.addOption(rollBackParam);
	}
	
	public static void main(String[] args) throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		Main main = new Main(args);
		main.parse();
	}

	private void parse() throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		CommandLineParser parser = new BasicParser();	
		CommandLine commandLine = parser.parse(commandLineOptions, args);
		
		HelpFormatter formatter = new HelpFormatter();
		
		String project = checkForArgument(commandLine, formatter, projectParam, "Y");	
		String env = checkForArgument(commandLine, formatter, envParam, "X");
		String region = checkForArgument(commandLine, formatter, regionParam, "EC2_REGION");
		List<Option> exclusives = new LinkedList<Option>();
		exclusives.add(dirParam);
		exclusives.add(fileParam);
		exclusives.add(resetParam);
		exclusives.add(rollBackParam);
		checkForOneOfArgument(commandLine, formatter, exclusives);	
		
		Region awsRegion = populateRegion(region);
		
		ProjectAndEnv projectAndEnv = new ProjectAndEnv(project, env);
		logger.info("Invoking for " + projectAndEnv);
		logger.info("Region set to " + awsRegion);
		
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AwsFacade aws = new AwsFacade(credentialsProvider, awsRegion);
		
		if (commandLine.hasOption(dirParam.getArgName())) {
			invokeForDir(aws, projectAndEnv, commandLine.getOptionValue(dirParam.getArgName()));
		} else if (commandLine.hasOption(fileParam.getArgName())) {
			invokeForFile(aws, projectAndEnv, commandLine.getOptionValue(fileParam.getArgName()));
		} else if (commandLine.hasOption(resetParam.getArgName())) {
			invokeReset(aws, projectAndEnv);
		} else if (commandLine.hasOption(rollBackParam.getArgName())) {
			invokeRollback(aws, projectAndEnv, commandLine.getOptionValue(rollBackParam.getArgName()));
		}
	}

	private void invokeRollback(AwsFacade aws, ProjectAndEnv projectAndEnv, String folder) throws InvalidParameterException {
		logger.info("Invoking rollback for " + projectAndEnv);
		aws.rollbackTemplatesInFolder(folder, projectAndEnv);
	}

	private void invokeReset(AwsFacade aws, ProjectAndEnv projectAndEnv) {
		logger.info("Reseting index for " + projectAndEnv);
		aws.resetDeltaIndex(projectAndEnv);	
	}

	private Region populateRegion(String regionName) throws MissingArgumentException {
		logger.info("Check for region using name "+regionName);
		Region result = RegionUtils.getRegion(regionName);
		if (result==null) {
			String msg = "Unable for find region for " + regionName;
			logger.error(msg);
			throw new MissingArgumentException(msg);
		}
		return result;
	}

	private void invokeForFile(AwsFacade aws, ProjectAndEnv projectAndEnv, String filename) throws FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		File templateFile = new File(filename);
		String stackName = aws.applyTemplate(templateFile, projectAndEnv);
		logger.info("Created stack name "+stackName);
	}

	private void invokeForDir(AwsFacade aws, ProjectAndEnv projectAndEnv, String folderPath) throws FileNotFoundException, InvalidParameterException, IOException, WrongNumberOfStacksException, InterruptedException {
		ArrayList<String> stackNames = aws.applyTemplatesFromFolder(folderPath, projectAndEnv);
		logger.info(String.format("Created %s stacks", stackNames.size()));
		for(String name : stackNames) {
			logger.info("Created stack " +name);
		}
	}

	private void checkForOneOfArgument(CommandLine cmd, HelpFormatter formatter, List<Option> exclusives) throws MissingArgumentException {
		int count = 0;
		StringBuilder names = new StringBuilder();
		for(Option option : exclusives) {
			names.append(option.getArgName()).append(" ");
			if (cmd.hasOption(option.getArgName())) {
				count++;
			}	
		}

		if (count!=1) {
			String msg = "Please supply only one of " + names.toString();
			logger.error(msg);	
			formatter.printHelp(executableName, commandLineOptions);
			throw new MissingArgumentException(msg);
		}	
	}

	private String checkForArgument(CommandLine cmd, HelpFormatter formatter,
			Option option, String environmentalVar) throws MissingArgumentException {
		String argName = option.getArgName();
		logger.debug("Checking for arg " + argName);
		if (cmd.hasOption(argName)) {
			return cmd.getOptionValue(argName);
		}
		
		logger.info(String.format("Argument not given %s, try environmental var %s", argName, environmentalVar));
		String fromEnv = System.getenv(environmentalVar);
		if (fromEnv!=null) {
			return fromEnv;
		}
		formatter.printHelp( executableName, commandLineOptions );		
		throw new MissingArgumentException(option);	
	}
}
