package tw.com.commandline.actions;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class TidyOldStacksAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public TidyOldStacksAction() {
		createOptionWithArgs("tidyOldStacks","Delete stacks matching given template no longer associated to the LB via an instance."+
				"Pass template filename and type tag",2);
	}
	
	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams,
					   String... args) throws
			IOException,
			InterruptedException, CfnAssistException, MissingArgumentException {
		AwsFacade facade = factory.createFacade();
		File file = new File(args[0]);
		facade.tidyNonLBAssocStacks(file, projectAndEnv, args[1]);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                         String... args)
			throws CommandLineException {
		if (args.length!=2) {
			throw new CommandLineException("Missing arguments for command");
		}
		if ((args[0]==null) || (args[1]==null)) {
			throw new CommandLineException("Missing arguments for command");
		}
		super.guardForNoBuildNumber(projectAndEnv);
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
		return true;
	}

}
