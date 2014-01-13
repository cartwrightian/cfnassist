package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.CannotFindVpcException;
import tw.com.InvalidParameterException;
import tw.com.ProjectAndEnv;
import tw.com.TagsAlreadyInit;
import tw.com.WrongNumberOfStacksException;

public class InitAction implements CommandLineAction {
	private static final Logger logger = LoggerFactory.getLogger(InitAction.class);
	
	private Option option;

	@SuppressWarnings("static-access")
	public InitAction() {
		option = OptionBuilder.withArgName("init").hasArg().
					withDescription("Warning: Initialise a VPC to set up tags, provide VPC Id").create("init");
	}

	@Override
	public Option getOption() {
		return option;
	}
	
	@Override
	public String getArgName() {
		return option.getArgName();
	}

	@Override
	public void invoke(AwsFacade aws, ProjectAndEnv projectAndEnv,
			String vpcId, Collection<Parameter> unused) throws InvalidParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
			InterruptedException, TagsAlreadyInit, CannotFindVpcException {
		logger.info("Invoke init of tags for VPC: " + vpcId);
		aws.initTags(projectAndEnv, vpcId);		
	}

}
