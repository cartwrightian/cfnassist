package tw.com.commandline.actions;


import org.apache.commons.cli.MissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandExecutor;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class SSHCommandAction extends SharedAction {
    private static final Logger logger = LoggerFactory.getLogger(SSHCommandAction.class);

    @SuppressWarnings("static-access")
    public SSHCommandAction() {
        createOptionalWithOptionalArg("ssh", "Create ssh command for the project/env combination");
    }


    @Override
    public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                       Collection<Parameter> artifacts, String... argument) throws IOException, InterruptedException, CfnAssistException, MissingArgumentException {

        AwsFacade aws = factory.createFacade();
        CommandExecutor commandExecutor = factory.getCommandExecutor();
        String user = (argument==null) ? "ec2-user" : argument[0];
        List<String> sshCommand = aws.createSSHCommand(projectAndEnv, user);
        logger.info("About to execute " + sshCommand);
        commandExecutor.execute(sshCommand);
    }

    @Override
    public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                         Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
        guardForProjectAndEnv(projectAndEnv);
        guardForNoBuildNumber(projectAndEnv);
        guardForNoArtifacts(artifacts);
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
