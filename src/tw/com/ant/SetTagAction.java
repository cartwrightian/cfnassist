package tw.com.ant;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class SetTagAction implements ActionElement {
    private String name;
    private String value;

    @Override
    public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams) throws IOException, InterruptedException, CfnAssistException, CommandLineException, MissingArgumentException {
        CommandLineAction action = new tw.com.commandline.actions.AddTagAction();

        action.validate(projectAndEnv, cfnParams, name, value);
        action.invoke(factory, projectAndEnv, cfnParams, name, value);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
