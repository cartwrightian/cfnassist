package tw.com.commandline;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.AwsFacade;
import tw.com.entity.Tagging;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.providers.SNSEventSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class CommandFlags {
	private static final Logger logger = LoggerFactory.getLogger(CommandFlags.class);
	
	private Option projectParam;
	private Option envParam;
	private Option buildNumberParam;
	private Option keysValuesParam;
	private Option snsParam;
	private Option capIAMParam;
	private Option commentParam;
	private Option artifactParam;
	private Option bucketParam;

	private String executableName;
	private Options commandLineOptions;

	private String project;
	private String env;
	private Integer buildNumber = null;
	private Boolean sns;
	private Boolean capabilityIAM;
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
		commandLineOptions.addOption(keysValuesParam);
		commandLineOptions.addOption(buildNumberParam);
		commandLineOptions.addOption(snsParam);
		commandLineOptions.addOption(capIAMParam);
		commandLineOptions.addOption(commentParam);
		commandLineOptions.addOption(artifactParam);
		commandLineOptions.addOption(bucketParam);
	}
	
	public void populateFlags(CommandLine commandLine, HelpFormatter formatter) throws MissingArgumentException, InvalidStackParameterException {
		project = checkForArgument(commandLine, formatter, projectParam, AwsFacade.PROJECT_TAG, false);	
		env = checkForArgument(commandLine, formatter, envParam, AwsFacade.ENVIRONMENT_TAG, false);
		String buildNumberAsString = checkForArgument(commandLine, formatter, buildNumberParam, AwsFacade.BUILD_TAG, false);
		if (!buildNumberAsString.isEmpty()) {
			buildNumber = Integer.parseInt(buildNumberAsString);
		}
		sns = checkForArgumentPresent(commandLine, snsParam);
		capabilityIAM = checkForArgumentPresent(commandLine, capIAMParam);
		comment = checkForArgument(commandLine, formatter, commentParam, "", false);
		cfnParams = checkForKeyValueParameters(commandLine, keysValuesParam);
		artifacts = checkForKeyValueParameters(commandLine, artifactParam);
		boolean bucketRequired = (!artifacts.isEmpty());
		s3bucket = checkForArgument(commandLine, formatter, bucketParam, AwsFacade.ENV_S3_BUCKET, bucketRequired);
	}
	
	@SuppressWarnings("static-access")
	private void createOptions() {
		projectParam = createParam("project", "Name of the cfnassist project, or use env var: " + AwsFacade.PROJECT_TAG);
		envParam = createParam("env", "Name of cfnassit environment, or use env var: " + AwsFacade.ENVIRONMENT_TAG);

        keysValuesParam = createParamMultiArgs("parameters",
                "Provide paramters for cfn scripts, format as per cfn commandline tools");

        buildNumberParam = createParam("build",
                "A Build number/id to tag the deployed stacks with, or use env var: " + AwsFacade.BUILD_TAG);

        snsParam = createParamNoArg("sns", String.format("Use SNS to publish updates from cloud formation, uses the topic %s"
                , SNSEventSource.SNS_TOPIC_NAME));

        capIAMParam = createParamNoArg("capabilityIAM",
                "Pass capability IAM to create stack (needed if you get capability missing exceptions)");

        commentParam = createParam("comment", "Add a comment within the tag " + Tagging.COMMENT_TAG);

        artifactParam = createParamMultiArgs("artifacts","Provide files to be uploaded to S3 bucket, param values will " +
                "be replaced with the S3 URLs and passed into the template file");

        bucketParam = createParam("bucket", "Bucket name to use for S3 artifacts");
	}

    private Option createParamMultiArgs(String name, String description) {
        return Option.builder(name).
                argName(name).desc(description).valueSeparator(';').hasArgs().build();
    }

    private Option createParamNoArg(String name, String description) {
        return Option.builder(name).
                argName(name).desc(description).hasArg(false).build();
    }

    private Option createParam(String name, String description) {
        return Option.builder().
				argName(name).
				longOpt(name).
				desc(description).
				hasArg(true).
				build();
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
	
	private Collection<Parameter> checkForKeyValueParameters(CommandLine cmd, Option commandFlag) throws InvalidStackParameterException {
		
		LinkedList<Parameter> results = new LinkedList<>();
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
				throw new InvalidStackParameterException(msg);
			}
			Parameter pair = Parameter.builder().parameterKey(parts[0]).parameterValue(parts[1]).build();
			results.add(pair);
			logger.info("Add parameter " + keyValue);
		}
		
		return results;
	}
	
	private Boolean checkForArgumentPresent(CommandLine commandLine,
											Option option) {
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

	public Integer getBuildNumber() {
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
		return buildNumber!=null;
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
	
	public boolean haveCapabilityIAM() {
		return capabilityIAM;
	}

}
