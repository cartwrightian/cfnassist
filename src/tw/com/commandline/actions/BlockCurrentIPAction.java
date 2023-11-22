package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.ProvidesCurrentIp;

import java.util.Collection;

public class BlockCurrentIPAction extends SharedAction {

	@SuppressWarnings("static-access")
	public BlockCurrentIPAction() {
		createOptionWithArgs("blockCurrentIP",
                "Block (i.e remove from security group) current ip for ELB tagged with type tag for port", 2);
	}

    @Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams,
					   String... argument) throws
			InterruptedException,
			CfnAssistException, MissingArgumentException {
		
		AwsFacade facade = factory.createFacade();
		ProvidesCurrentIp hasCurrentIp = factory.getCurrentIpProvider();
		
		Integer port = Integer.parseInt(argument[1]);
		facade.removeCurrentIPAndPortFromELB(projectAndEnv, argument[0], hasCurrentIp, port);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams,
                         String... argumentForAction) throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);

		try {
			Integer.parseInt(argumentForAction[1]); 
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
