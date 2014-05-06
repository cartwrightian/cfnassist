package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
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
import tw.com.ELBRepository;
import tw.com.FacadeFactory;
import tw.com.ProjectAndEnv;
import tw.com.SNSMonitor;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudformation.model.Parameter;

public class Main {
	private static final String ENV_VAR_EC2_REGION = "EC2_REGION";
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	private Option projectParam;
	private Option envParam;
	private Option regionParam;
	private Option buildNumberParam;
	
	private Options commandLineOptions;
	private String[] args;
	private String executableName;
	private Option keysValuesParam;
	private Option snsParam;
	private Option commentParam;
	private Actions commandActions;

	public Main(String[] args) {
		this.args = args;
		executableName = "cfnassist";
		commandLineOptions = new Options();
		createOptions();
		commandActions = new Actions();
		commandActions.addActionsTo(commandLineOptions);
	}
	
	public static void main(String[] args) throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, TagsAlreadyInit, CannotFindVpcException {
		Main main = new Main(args);
		int result = main.parse();
		System.exit(result);
	}

	@SuppressWarnings("static-access")
	private void createOptions() {
		projectParam = OptionBuilder.withArgName("project").hasArg().
				withDescription("Name of the cfnassist project, or use env var: " + AwsFacade.PROJECT_TAG).
				create("project");
		commandLineOptions.addOption(projectParam);
		envParam = OptionBuilder.withArgName("env").hasArg().
				withDescription("Name of cfnassit environment, or use env var: " + AwsFacade.ENVIRONMENT_TAG).
				create("env");
		commandLineOptions.addOption(envParam);
		regionParam = OptionBuilder.withArgName("region").hasArg().
				withDescription("AWS Region name, or use env var: "+ENV_VAR_EC2_REGION).
				create("region");
		commandLineOptions.addOption(regionParam);
		keysValuesParam = OptionBuilder.withArgName("parameters").
				hasArgs().withValueSeparator(';').
				withDescription("Provide paramters for cfn scripts, format as per cfn commandline tools").
				create("parameters");
		commandLineOptions.addOption(keysValuesParam);
		buildNumberParam = OptionBuilder.withArgName("build").
				hasArgs().withDescription("A Build number/id to tag the deployed stacks with, or use env var: " + AwsFacade.BUILD_TAG).
				create("build");
		commandLineOptions.addOption(buildNumberParam);
		snsParam = OptionBuilder.withArgName("sns").
				withDescription(
						String.format("Use SNS to publish updates from cloud formation, uses the topic %s"
						,SNSMonitor.SNS_TOPIC_NAME)).create("sns");
		commandLineOptions.addOption(snsParam);
		commentParam = OptionBuilder.withArgName("comment").hasArg().
				withDescription("Add a comment within the tag " + AwsFacade.COMMENT_TAG).create("comment");
		commandLineOptions.addOption(commentParam);
	}

	public int parse() {
		
		try {
			CommandLineParser parser = new BasicParser();	
			CommandLine commandLine = parser.parse(commandLineOptions, args);
			
			HelpFormatter formatter = new HelpFormatter();
			
			String project = checkForArgument(commandLine, formatter, projectParam, AwsFacade.PROJECT_TAG, false);	
			String env = checkForArgument(commandLine, formatter, envParam, AwsFacade.ENVIRONMENT_TAG, false);
			String region = checkForArgument(commandLine, formatter, regionParam, ENV_VAR_EC2_REGION, true);
			String buildNumber = checkForArgument(commandLine, formatter, buildNumberParam, AwsFacade.BUILD_TAG, false);
			Boolean sns = checkForArgumentPresent(commandLine, formatter, snsParam);
			String comment = checkForArgument(commandLine, formatter, commentParam, "", false);
			Collection<Parameter> cfnParams = checkForCfnParameters(commandLine, formatter, keysValuesParam);

			CommandLineAction action = commandActions.selectCorrectActionFromArgs(commandLine, formatter, executableName, commandLineOptions );	
			
			Region awsRegion = populateRegion(region);
			FacadeFactory factory = new FacadeFactory(awsRegion, project);
			
			ProjectAndEnv projectAndEnv = new ProjectAndEnv(project, env);
			if (!buildNumber.isEmpty()) {
				projectAndEnv.addBuildNumber(buildNumber);
			}
			if (sns) {
				projectAndEnv.setUseSNS();
			}
			logger.info("Invoking for " + projectAndEnv);
			logger.info("Region set to " + awsRegion);
			
			AwsFacade facade = factory.createFacade(projectAndEnv.useSNS());
			ELBRepository repository = factory.createElbRepo();
			if (!comment.isEmpty()) {
				facade.setCommentTag(comment);
			}
				
			String argumentForAction = commandLine.getOptionValue(action.getArgName());
			action.validate(facade, projectAndEnv, argumentForAction, cfnParams);
			action.invoke(facade, repository, projectAndEnv, argumentForAction, cfnParams);
		}
		catch (Exception exception) {
			//  back to caller via exit status
			logger.error("CommandLine fail: ", exception);
			return -1;
		}
		logger.debug("CommandLine ok");
		return 0;
	}

	private Collection<Parameter> checkForCfnParameters(
			CommandLine cmd, HelpFormatter formatter,
			Option cfnParamOptions) throws InvalidParameterException {
		
		LinkedList<Parameter> results = new LinkedList<Parameter>();
		String argName = cfnParamOptions.getArgName();
		logger.debug("Checking for arg " + argName);
		if (!cmd.hasOption(argName)) {
			logger.debug("Additional parameters not supplied");
			return results;
		}

		logger.info("Process additional parameters");

		List<String> valuesList = Arrays.asList(cmd.getOptionValues(argName));
		logger.debug(String.format("Found %s arguments inside of parameter", valuesList.size()));
		for(String keyValue : valuesList) {
			String[] parts = keyValue.split("=");
			if (parts.length!=2) {
				String msg = "Unable to process parameters given, problem with " + keyValue;
				logger.error(msg);
				throw new InvalidParameterException(msg);
			}
			Parameter pair = new Parameter();
			pair.setParameterKey(parts[0]);
			pair.setParameterValue(parts[1]);
			results.add(pair);
			logger.info("Add cfn parameter " + keyValue);
		}
		
		return results;
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

	private String checkForArgument(CommandLine cmd, HelpFormatter formatter,
			Option option, String environmentalVar, boolean required) throws MissingArgumentException {
		String argName = option.getArgName();
		logger.debug("Checking for arg " + argName);
		if (cmd.hasOption(argName)) {
			String optionValue = cmd.getOptionValue(argName);
			logger.info("Got value " + optionValue);
			return optionValue;
		}
		
		if (!environmentalVar.isEmpty()) {
			logger.info(String.format("Argument not given %s, try environmental var %s", argName, environmentalVar));
			String fromEnv = System.getenv(environmentalVar);
			if (fromEnv!=null) {
				logger.info("Got value " + fromEnv);
				return fromEnv;
			}
		}
		
		if (required)
		{
			formatter.printHelp( executableName, commandLineOptions );
			throw new MissingArgumentException(option);	
		}
		return "";
	}
	
	private Boolean checkForArgumentPresent(CommandLine commandLine,
			HelpFormatter formatter, Option option) {
		String argName = option.getArgName();
		logger.debug("Checking for arg " + argName);
		return commandLine.hasOption(argName);
	}
}
