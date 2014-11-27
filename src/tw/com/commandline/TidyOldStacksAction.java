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
		option = OptionBuilder.withArgName("tidyOldStakcs").hasArg().
				withDescription("delete stacks matching given template no longer associated to the LB via an instance").create("tidyOldStakcs");
	}
	
	@Override
	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv,
			String argument, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts) throws InvalidParameterException,
			FileNotFoundException, IOException, WrongNumberOfStacksException,
			InterruptedException, CfnAssistException, MissingArgumentException,
			TooManyELBException {
		AwsFacade facade = factory.createFacade();
		File file = new File(argument);
		facade.tidyNonLBAssocStacks(file, projectAndEnv);
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, String argumentForAction,
			Collection<Parameter> cfnParams, Collection<Parameter> artifacts)
			throws CommandLineException {
		super.guardForNoBuildNumber(projectAndEnv);
	}

}
