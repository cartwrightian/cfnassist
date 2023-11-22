package tw.com.commandline.actions;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.ProvidesCurrentIp;

import java.io.IOException;
import java.util.Collection;

public class AllowCurrentIPAction extends SharedAction {

	private static final int INDEX_OF_PORT_ARG = 1;

	@SuppressWarnings("static-access")
	public AllowCurrentIPAction() {
		createOptionWithArgs("allowCurrentIP",
				"Allow current ip (i.e. add to the security group) for ELB tagged with type tag, supply tag & port",
				2);
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams,
					   String... argument) throws
			IOException, InterruptedException,
			CfnAssistException, MissingArgumentException {
		
		AwsFacade facade = factory.createFacade();
		ProvidesCurrentIp hasCurrentIp = factory.getCurrentIpProvider();
		
		Integer port = Integer.parseInt(argument[INDEX_OF_PORT_ARG]);
		facade.addCurrentIPWithPortToELB(projectAndEnv, argument[0], hasCurrentIp, port);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams,
                         String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);
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
