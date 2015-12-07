package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.providers.ProvidesCurrentIp;

import java.io.IOException;
import java.util.Collection;

public class BlacklistAction extends SharedAction {

	@SuppressWarnings("static-access")
	public BlacklistAction() {
		createOptionWithArgs("blacklist",
                "Blacklist (i.e remove from security group) current ip for ELB tagged with type tag for port", 2);
//		option = OptionBuilder.
//				withArgName("blacklist").
//				hasArgs(2).
//				withDescription("Blacklist (i.e remove from security group) current ip for ELB tagged with type tag for port").
//				create("blacklist");
	}



    @Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... argument) throws
            IOException, InterruptedException,
			CfnAssistException, MissingArgumentException {
		
		AwsFacade facade = factory.createFacade();
		ProvidesCurrentIp hasCurrentIp = factory.getCurrentIpProvider();
		
		Integer port = Integer.parseInt(argument[1]);
		facade.blacklistCurrentIpForPortToElb(projectAndEnv, argument[0], hasCurrentIp, port);	
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
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
