package tw.com.commandline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.WrongNumberOfStacksException;

import com.amazonaws.services.cloudformation.model.Parameter;

public class TidyOldStacksAction extends SharedAction {
	
	@SuppressWarnings("static-access")
	public TidyOldStacksAction() {
		option = OptionBuilder.withArgName("tidyOldStacks").hasArgs(2).
				withDescription("Delete stacks matching given template no longer associated to the LB via an instance."+ 
								"Pass template filename and type tag").
				create("tidyOldStacks");
	}
	
	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts,
			String... args) throws InvalidParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
			InterruptedException, CfnAssistException, MissingArgumentException,
			TooManyELBException {
		AwsFacade facade = factory.createFacade();
		File file = new File(args[0]);
		facade.tidyNonLBAssocStacks(file, projectAndEnv, args[1]);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... args)
			throws CommandLineException {
		if (args.length!=2) {
			throw new CommandLineException("Missing arguments for command");
		}
		if ((args[0]==null) || (args[1]==null)) {
			throw new CommandLineException("Missing arguments for command");
		}
		super.guardForNoBuildNumber(projectAndEnv);
	}

}
