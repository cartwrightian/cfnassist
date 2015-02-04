package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.providers.ProvidesCurrentIp;

import com.amazonaws.services.cloudformation.model.Parameter;

public class WhitelistAction extends SharedAction {

	private static final int INDEX_OF_PORT_ARG = 1;

	@SuppressWarnings("static-access")
	public WhitelistAction() {
		option = OptionBuilder.
				withArgName("whitelist").
				hasArgs(2).
				withDescription("Whitelist current ip (i.e. add to the security group) for ELB tagged with type tag, supply tag & port").
				create("whitelist");
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argument) throws InvalidStackParameterException,
			FileNotFoundException, IOException, InterruptedException,
			CfnAssistException, MissingArgumentException {
		
		AwsFacade facade = factory.createFacade();
		ProvidesCurrentIp hasCurrentIp = factory.getCurrentIpProvider();
		
		Integer port = Integer.parseInt(argument[INDEX_OF_PORT_ARG]);
		facade.whitelistCurrentIpForPortToElb(projectAndEnv, argument[0], hasCurrentIp, port);	
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argumentForAction) throws CommandLineException {
		try {
			Integer.parseInt(argumentForAction[INDEX_OF_PORT_ARG]); 
		}
		catch (NumberFormatException exception) {
			throw new CommandLineException(exception.getLocalizedMessage());
		}
		
	}
	
	@Override
	public boolean usesProject() {
		return true;
	}

	@Override
	public boolean usesComment() {
		return false;
	}

	@Override
	public boolean usesSNS() {
		return false;
	}


}
