package tw.com.ant;


import software.amazon.awssdk.services.cloudformation.model.Parameter;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineAction;
import tw.com.commandline.CommandLineException;
import tw.com.commandline.actions.InitAction;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

public class InitElement implements ActionElement {
    private String vpcId;

    public InitElement() {

    }

    @Override
    public void execute(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams) throws IOException, InterruptedException, CfnAssistException, CommandLineException, MissingArgumentException {

        CommandLineAction actionToInvoke = new InitAction();

        actionToInvoke.validate(projectAndEnv, cfnParams, vpcId);
        actionToInvoke.invoke(factory, projectAndEnv, cfnParams, vpcId);
    }

    public void setVpcId(String vpcId) {
        this.vpcId = vpcId;
    }
}
