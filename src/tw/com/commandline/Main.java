package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.ArtifactUploader;
import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.FacadeFactory;
import tw.com.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudformation.model.Parameter;

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
	
	public static void main(String[] args) throws ParseException, FileNotFoundException, IOException, InvalidParameterException, WrongNumberOfStacksException, InterruptedException, TagsAlreadyInit, CannotFindVpcException {
		Main main = new Main(args);
		int result = main.parse();
		System.exit(result);
	}

	public int parse() {
		return parse(true);
	}

	public int parse(boolean act) {
		
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
			logger.info("Invoking for " + projectAndEnv);
			
			String argumentForAction = commandLine.getOptionValue(action.getArgName());
			action.validate(projectAndEnv, argumentForAction, flags.getAdditionalParameters());
			
			FacadeFactory factory = new FacadeFactory(awsRegion, flags.getProject());
			AwsFacade facade = factory.createFacade(flags.getSns());
			ELBRepository repository = factory.createElbRepo();
			if (flags.haveComment()) {
				facade.setCommentTag(flags.getComment());
			}
			
			Collection<Parameter> additionalParams = flags.getAdditionalParameters();
			
			if (act) {	
				Collection<Parameter> uploadParams = flags.getUploadParams();
				
				if (!uploadParams.isEmpty()) {
					ArtifactUploader artifactUploader = new ArtifactUploader(factory.getS3Client(), 
							flags.getBucketName(), flags.getBuildNumber());
					additionalParams.addAll(artifactUploader.uploadArtifacts(uploadParams));
				}
				action.invoke(facade, repository, projectAndEnv, argumentForAction, additionalParams);
			} else {
				logger.info("Not invoking");
			}
		}
		catch (Exception exception) {
			//  back to caller via exit status
			logger.error("CommandLine fail: ", exception);
			return -1;
		}
		logger.debug("CommandLine ok");
		return 0;
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
