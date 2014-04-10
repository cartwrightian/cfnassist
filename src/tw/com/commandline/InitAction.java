package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.ELBRepository;
import tw.com.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;

public class InitAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(InitAction.class);
	
	@SuppressWarnings("static-access")
	public InitAction() {
		option = OptionBuilder.withArgName("init").hasArg().
					withDescription("Warning: Initialise a VPC to set up tags, provide VPC Id").create("init");
	}

	@Override
	public void invoke(AwsFacade aws, ELBRepository repository,  ProjectAndEnv projectAndEnv,
			String vpcId, Collection<Parameter> unused) throws InvalidParameterException,
			FileNotFoundException, IOException, InterruptedException, CfnAssistException {
		logger.info("Invoke init of tags for VPC: " + vpcId);
		aws.initEnvAndProjectForVPC(vpcId, projectAndEnv);		
	}

}
