package tw.com.commandline;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.exceptions.InvalidParameterException;
import tw.com.providers.SNSEventSource;

public class CommandFlags {
	private static final Logger logger = LoggerFactory.getLogger(CommandFlags.class);
	
	private Option projectParam;
	private Option envParam;
	private Option regionParam;
	private Option buildNumberParam;
	private Option keysValuesParam;
	private Option snsParam;
	private Option commentParam;
	private Option artifactParam;
	private Option bucketParam;

	private String executableName;
	private Options commandLineOptions;

	private String project;
	private String env;
	private String region;
	private String buildNumber;
	private Boolean sns;
	private String comment;
	private String s3bucket;
	private Collection<Parameter> cfnParams;
	private Collection<Parameter> artifacts;

	public CommandFlags(String executableName, Options commandLineOptions) {
		this.executableName = executableName;
		this.commandLineOptions = commandLineOptions;
		createOptions();
		addOptions();
	}

	public void addOptions() {
		commandLineOptions.addOption(projectParam);
		commandLineOptions.addOption(envParam);
		commandLineOptions.addOption(regionParam);
		commandLineOptions.addOption(keysValuesParam);
		commandLineOptions.addOption(buildNumberParam);
		commandLineOptions.addOption(snsParam);
		commandLineOptions.addOption(commentParam);
		commandLineOptions.addOption(artifactParam);
		commandLineOptions.addOption(bucketParam);
	}
	
	public void populateFlags(CommandLine commandLine, HelpFormatter formatter) throws MissingArgumentException, InvalidParameterException {
		project = checkForArgument(commandLine, formatter, projectParam, AwsFacade.PROJECT_TAG, false);	
		env = checkForArgument(commandLine, formatter, envParam, AwsFacade.ENVIRONMENT_TAG, false);
		region = checkForArgument(commandLine, formatter, regionParam, Main.ENV_VAR_EC2_REGION, true);
		buildNumber = checkForArgument(commandLine, formatter, buildNumberParam, AwsFacade.BUILD_TAG, false);
		sns = checkForArgumentPresent(commandLine, formatter, snsParam);
		comment = checkForArgument(commandLine, formatter, commentParam, "", false);
		cfnParams = checkForKeyValueParameters(commandLine, formatter, keysValuesParam);
		artifacts = checkForKeyValueParameters(commandLine, formatter, artifactParam);
		boolean bucketRequired = (!artifacts.isEmpty());
		s3bucket = checkForArgument(commandLine, formatter, bucketParam, AwsFacade.ENV_S3_BUCKET, bucketRequired);
	}
	
	@SuppressWarnings("static-access")
	private void createOptions() {
		projectParam = OptionBuilder.withArgName("project").hasArg().
				withDescription("Name of the cfnassist project, or use env var: " + AwsFacade.PROJECT_TAG).
				create("project");
		
		envParam = OptionBuilder.withArgName("env").hasArg().
				withDescription("Name of cfnassit environment, or use env var: " + AwsFacade.ENVIRONMENT_TAG).
				create("env");
		
		regionParam = OptionBuilder.withArgName("region").hasArg().
				withDescription("AWS Region name, or use env var: "+Main.ENV_VAR_EC2_REGION).
				create("region");
		
		keysValuesParam = OptionBuilder.withArgName("parameters").
				hasArgs().withValueSeparator(';').
				withDescription("Provide paramters for cfn scripts, format as per cfn commandline tools").
				create("parameters");
		
		buildNumberParam = OptionBuilder.withArgName("build").
				hasArgs().withDescription("A Build number/id to tag the deployed stacks with, or use env var: " + AwsFacade.BUILD_TAG).
				create("build");
		
		snsParam = OptionBuilder.withArgName("sns").
				withDescription(
						String.format("Use SNS to publish updates from cloud formation, uses the topic %s"
						,SNSEventSource.SNS_TOPIC_NAME)).create("sns");
		
		commentParam = OptionBuilder.withArgName("comment").hasArg().
				withDescription("Add a comment within the tag " + AwsFacade.COMMENT_TAG).create("comment");
		
		artifactParam = OptionBuilder.withArgName("artifacts").
				hasArgs().withValueSeparator(';').
				withDescription("Provide files to be uploaded to S3 bucket, param values will be replaced with the S3 URLs and passed into the template file").
				create("artifacts");
		
		bucketParam = OptionBuilder.withArgName("bucket").
				hasArgs().withDescription("Bucket name to use for S3 artifacts").
				create("bucket");
		
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
			formatter.printHelp( executableName, commandLineOptions);
			throw new MissingArgumentException(option);	
		}
		return "";
	}
	
	private Collection<Parameter> checkForKeyValueParameters(CommandLine cmd, HelpFormatter formatter, Option commandFlag) throws InvalidParameterException {
		
		LinkedList<Parameter> results = new LinkedList<Parameter>();
		String argName = commandFlag.getArgName();
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
			logger.info("Add parameter " + keyValue);
		}
		
		return results;
	}
	
	private Boolean checkForArgumentPresent(CommandLine commandLine,
			HelpFormatter formatter, Option option) {
		String argName = option.getArgName();
		logger.debug("Checking for arg " + argName);
		return commandLine.hasOption(argName);
	}
	
	
	public String getProject() {
		return project;
	}

	public String getEnv() {
		return env;
	}

	public String getRegion() {
		return region;
	}

	public String getBuildNumber() {
		return buildNumber;
	}

	public Boolean getSns() {
		return sns;
	}

	public String getComment() {
		return comment;
	}

	public Collection<Parameter> getAdditionalParameters() {
		return cfnParams;
	}
	
	public boolean haveBuildNumber() {
		return !buildNumber.isEmpty();
	}

	public boolean haveComment() {
		return !comment.isEmpty();
	}
	
	public boolean haveSnsEnable() {
		return sns;
	}

	public Collection<Parameter> getUploadParams() {
		return artifacts;
	}

	public String getS3Bucket() {
		return s3bucket;
	}

	public boolean haveS3Bucket() {
		return !s3bucket.isEmpty();
	}

}
