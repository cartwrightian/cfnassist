package tw.com.commandline;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.*;

import java.io.IOException;
import java.util.Collection;

public class Main {
	public static final String ENV_VAR_EC2_REGION = "EC2_REGION";
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	
	private Options commandLineOptions;
	private String[] args;
	private String executableName;
	
	private CommandFlags flags;
	private Actions commandActions;

	public Main(String[] args) {
		this.args = args;
		executableName = "cfnassist";
		commandLineOptions = new Options();
		flags = new CommandFlags(executableName, commandLineOptions);
		commandActions = new Actions();
		commandActions.addActionsTo(commandLineOptions);
	}
	
	public static void main(String[] args) throws ParseException, IOException, InvalidStackParameterException, WrongNumberOfStacksException, InterruptedException, TagsAlreadyInit, CannotFindVpcException {
		Main main = new Main(args);
		int result = main.parse();
		System.exit(result);
	}

	public int parse() {
		FacadeFactory factory = new FacadeFactory();
		return parse(factory, true);
	}

	public int parse(FacadeFactory factory, boolean act) {	
		try {
			CommandLineParser parser = new BasicParser();
			CommandLine commandLine = parser.parse(commandLineOptions, args);
			HelpFormatter formatter = new HelpFormatter();
		
			flags.populateFlags(commandLine, formatter);
			CommandLineAction action = commandActions.selectCorrectActionFromArgs(commandLine, formatter, executableName, commandLineOptions );	
			
			Region awsRegion = populateRegion(flags.getRegion());
			logger.info("Region set to " + awsRegion);
			
			ProjectAndEnv projectAndEnv = new ProjectAndEnv(flags.getProject(), flags.getEnv());
			if (flags.haveBuildNumber()) {
				projectAndEnv.addBuildNumber(flags.getBuildNumber());
			}
			if (flags.getSns()) {
				projectAndEnv.setUseSNS();
			}
			if (flags.haveS3Bucket()) {
				projectAndEnv.setS3Bucket(flags.getS3Bucket());
			}
			if (flags.haveCapabilityIAM()) {
				projectAndEnv.setUseCapabilityIAM();
			}
			logger.info("Invoking for " + projectAndEnv);
			
			String[] argsForAction = commandLine.getOptionValues(action.getArgName());
			Collection<Parameter> artifacts = flags.getUploadParams();
			action.validate(projectAndEnv, flags.getAdditionalParameters(), artifacts, argsForAction);
			
			factory.setRegion(awsRegion);
			setFactoryOptionsForAction(factory, action);

			Collection<Parameter> additionalParams = flags.getAdditionalParameters();
			
			if (act) {				
				action.invoke(factory, projectAndEnv, additionalParams, artifacts, argsForAction);
			} else {
				logger.info("Not invoking");
			}
		}
		catch (CfnAssistException exception) {
			logger.error("CommandLine fail due to cfn assit problem: ", exception);
			return -1;
		} catch (MissingArgumentException | IOException | InterruptedException e) {
			logger.error("Processing failed: ", e);
			return -1;
		} catch (CommandLineException e) {
			logger.error("CommandLine processing failed: ", e);
			return -1;
		} catch (ParseException e) {
			logger.error("Unable to parse commandline: ", e);
			return -1;
		} 
		logger.debug("CommandLine ok");
		return 0;
	}

	private void setFactoryOptionsForAction(FacadeFactory factory, CommandLineAction action) {
		// TODO move some validation checking to here
		if (action.usesProject()) {
			factory.setProject(flags.getProject());
		}   
		if (action.usesComment() && flags.haveComment()) {
			factory.setCommentTag(flags.getComment());	
		} 
		if (action.usesSNS() && flags.haveSnsEnable()) {
			factory.setSNSMonitoring();
		}
		
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

}
