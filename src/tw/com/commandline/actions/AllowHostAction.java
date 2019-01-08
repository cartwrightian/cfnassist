package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.net.UnknownHostException;
import java.util.Collection;

public class AllowHostAction extends SharedAction {

	private static final int INDEX_OF_PORT_ARG = 2;

	@SuppressWarnings("static-access")
	public AllowHostAction() {
		createOptionWithArgs("allowhost",
				"Allow given host ip's (i.e. add to the security group) for ELB tagged with type tag, supply tag, hostname & port",
				3);
	}

	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
					   Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
					   String... argument) throws InterruptedException, MissingArgumentException, CfnAssistException {
		
		AwsFacade facade = factory.createFacade();

		Integer port = Integer.parseInt(argument[INDEX_OF_PORT_ARG]);
		String hostName = argument[1];
		try {
			facade.addHostAndPortToELB(projectAndEnv, argument[0], hostName, port);
		} catch (UnknownHostException unknownHost) {
			throw new CfnAssistException("Unable to resolve host "+ hostName, unknownHost);
		}
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
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
