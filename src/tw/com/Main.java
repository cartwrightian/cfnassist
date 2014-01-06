package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.management.BadAttributeValueExpException;

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
	private Option project;
	private Option env;
	private Option file;
	private Option dir;
	private Option region;
	private Options options;
	private String[] args;
	private String executableName;
	private Option reset;

	
	@SuppressWarnings("static-access")
	Main(String[] args) {
		this.args = args;
		executableName = "cfnassist";
		options = new Options();
		project = OptionBuilder.withArgName("project").hasArg().withDescription("Name of the cfnassist project").create("project");
		options.addOption(project);
		env = OptionBuilder.withArgName("env").hasArg().withDescription("Name of cfnassit environment").create("env");
		options.addOption(env);
		file = OptionBuilder.withArgName("file").hasArg().withDescription("The template file to apply").create("file");
		options.addOption(file);
		dir = OptionBuilder.withArgName("dir").hasArg().withDescription("The directory/folder containing delta templates to apply").create("dir");
		options.addOption(dir);
		region = OptionBuilder.withArgName("region").hasArg().withDescription("AWS Region name").create("region");
		options.addOption(region);
		reset = OptionBuilder.withArgName("reset").withDescription("Resets the Delta Tag "+AwsFacade.INDEX_TAG).create("reset");
		options.addOption(reset);
	}
	
	public static void main(String[] args) throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		Main main = new Main(args);
		main.parse();
	}

	private void parse() throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException {
		CommandLineParser parser = new BasicParser();	
		CommandLine cmd = parser.parse( options, args);
		
		HelpFormatter formatter = new HelpFormatter();
		
		checkForArgument(cmd, formatter, project);	
		checkForArgument(cmd, formatter, env);
		checkForArgument(cmd, formatter, region);
		checkForOneOfArgument(cmd, formatter, dir, file, reset);	
		
		Region awsRegion = populateRegion(cmd, formatter);
		
		ProjectAndEnv projectAndEnv = new ProjectAndEnv(cmd.getOptionValue(project.getArgName()), cmd.getOptionValue(env.getArgName()));
		logger.info("Invoking for " + projectAndEnv);
		logger.info("Region set to " + awsRegion);
		
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AwsFacade aws = new AwsFacade(credentialsProvider, awsRegion);
		
		if (cmd.hasOption(dir.getArgName())) {
			invokeForDir(aws, projectAndEnv, cmd.getOptionValue(dir.getArgName()));
		} else if (cmd.hasOption(file.getArgName())) {
			invokeForFile(aws, projectAndEnv, cmd.getOptionValue(file.getArgName()));
		} else if (cmd.hasOption(reset.getArgName())) {
			invokeReset(aws, projectAndEnv);
		}
	}

	private void invokeReset(AwsFacade aws, ProjectAndEnv projectAndEnv) {
		logger.info("Reseting index for " + projectAndEnv);
		aws.resetDeltaIndex(projectAndEnv);	
	}

	private Region populateRegion(CommandLine cmd, HelpFormatter formatter) throws MissingArgumentException {
		String regionName = cmd.getOptionValue(region.getArgName());
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

	private void checkForOneOfArgument(CommandLine cmd,
			HelpFormatter formatter, Option opt1, Option opt2, Option opt3) throws MissingArgumentException {
		boolean flagA = cmd.hasOption(opt1.getArgName());
		boolean flagB = cmd.hasOption(opt2.getArgName());
		boolean flagC = cmd.hasOption(opt3.getArgName());
		if (!(flagA ^ flagB ^ flagC)) {
			String msg = String.format("Please give one only of options %s, %s or %s", opt1.getArgName(), opt2.getArgName(), opt3.getArgName());
			logger.error(msg);	
			formatter.printHelp( executableName, options );		
			throw new MissingArgumentException(msg);
		}	
	}

	private void checkForArgument(CommandLine cmd, HelpFormatter formatter,
			Option option) throws MissingArgumentException {
		if (!cmd.hasOption(option.getArgName())) {
			logger.error("Missing argument " + option.getArgName());	
			formatter.printHelp( executableName, options );		
			throw new MissingArgumentException(option);
		}
	}
}
