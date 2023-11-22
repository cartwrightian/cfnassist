package tw.com.ant;

import org.apache.commons.cli.MissingArgumentException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public interface ActionElement {

	void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv,
				 Collection<Parameter> cfnParams)
			throws IOException, InterruptedException, CfnAssistException, CommandLineException, MissingArgumentException;
}