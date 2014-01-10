package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
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

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.StackCreateFailed;
import tw.com.TagsAlreadyInit;
import tw.com.WrongNumberOfStacksException;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;

public class Main {
	private static final String ENV_VAR_EC2_REGION = "EC2_REGION";
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	private Option projectParam;
	private Option envParam;
	private Option regionParam;
	private CommandLineAction fileAction;
	private CommandLineAction dirAction;
	private CommandLineAction resetAction;
	private CommandLineAction rollbackAction;
	private CommandLineAction initAction;
	private Options commandLineOptions;
	private String[] args;
	private String executableName;

	@SuppressWarnings("static-access")
	Main(String[] args) {
		this.args = args;
		executableName = "cfnassist";
		commandLineOptions = new Options();
		projectParam = OptionBuilder.withArgName("project").hasArg().
				withDescription("Name of the cfnassist project, or use env var: " + AwsFacade.PROJECT_TAG).create("project");
		commandLineOptions.addOption(projectParam);
		envParam = OptionBuilder.withArgName("env").hasArg().
				withDescription("Name of cfnassit environment, or use env var: " + AwsFacade.ENVIRONMENT_TAG).create("env");
		commandLineOptions.addOption(envParam);
		regionParam = OptionBuilder.withArgName("region").hasArg().
				withDescription("AWS Region name, or use env var: "+ENV_VAR_EC2_REGION).create("region");
		commandLineOptions.addOption(regionParam);
		fileAction = new FileAction();
		commandLineOptions.addOption(fileAction.getOption());
		dirAction = new DirAction(); 
		commandLineOptions.addOption(dirAction.getOption());
		resetAction = new ResetAction();		
		commandLineOptions.addOption(resetAction.getOption());
		rollbackAction = new RollbackAction();		
		commandLineOptions.addOption(rollbackAction.getOption());
		initAction = new InitAction();	
		commandLineOptions.addOption(initAction.getOption());
	}
	
	public static void main(String[] args) throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, TagsAlreadyInit, CannotFindVpcException, StackCreateFailed {
		Main main = new Main(args);
		main.parse();
	}

	private void parse() throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, TagsAlreadyInit, CannotFindVpcException, StackCreateFailed {
		CommandLineParser parser = new BasicParser();	
		CommandLine commandLine = parser.parse(commandLineOptions, args);
		
		HelpFormatter formatter = new HelpFormatter();
		
		String project = checkForArgument(commandLine, formatter, projectParam, AwsFacade.PROJECT_TAG);	
		String env = checkForArgument(commandLine, formatter, envParam, AwsFacade.ENVIRONMENT_TAG);
		String region = checkForArgument(commandLine, formatter, regionParam, ENV_VAR_EC2_REGION);
		List<CommandLineAction> actions = new LinkedList<CommandLineAction>();
		actions.add(dirAction);
		actions.add(fileAction);
		actions.add(resetAction);
		actions.add(rollbackAction);
		actions.add(initAction);
		CommandLineAction action = selectCorrectActionFromArgs(commandLine, formatter, actions);	
		
		Region awsRegion = populateRegion(region);
		
		ProjectAndEnv projectAndEnv = new ProjectAndEnv(project, env);
		logger.info("Invoking for " + projectAndEnv);
		logger.info("Region set to " + awsRegion);
		
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		AwsFacade aws = new AwsFacade(credentialsProvider, awsRegion);
			
		String argument = commandLine.getOptionValue(action.getArgName());
		action.invoke(aws, projectAndEnv, argument);
		
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

	private CommandLineAction selectCorrectActionFromArgs(CommandLine cmd, HelpFormatter formatter, 
			List<CommandLineAction> actions) throws MissingArgumentException {
		int count = 0;
		CommandLineAction matchingAction = null;
		StringBuilder names = new StringBuilder();
		for(CommandLineAction action : actions) {
			names.append(action.getArgName()).append(" ");
			if (cmd.hasOption(action.getArgName())) {
				matchingAction = action;
				count++;
			}	
		}

		if (count!=1) {
			String msg = "Please supply only one of " + names.toString();
			logger.error(msg);	
			formatter.printHelp(executableName, commandLineOptions);
			throw new MissingArgumentException(msg);
		}		
		return matchingAction;		
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
