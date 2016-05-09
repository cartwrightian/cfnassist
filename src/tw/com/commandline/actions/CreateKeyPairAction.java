package tw.com.commandline.actions;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.ec2.model.KeyPair;
import org.apache.commons.cli.MissingArgumentException;
import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.commandline.CommandLineException;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CfnAssistException;

import java.io.IOException;
import java.util.Collection;

import static java.lang.String.format;

public class CreateKeyPairAction extends SharedAction {

    @SuppressWarnings("static-access")
    public CreateKeyPairAction() {
        createOptionalWithOptionalArg("keypair", "Create a new keypair for the given project and environment");
    }

    @Override
    public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
                       Collection<Parameter> artifacts, String... argument) throws IOException, InterruptedException,
            CfnAssistException, MissingArgumentException {
        AwsFacade facade = factory.createFacade();

        String filename;
        if (argument==null) {
            String home = System.getenv("HOME");
            String keypairName = format("%s_%s", projectAndEnv.getProject(), projectAndEnv.getEnv());
            filename = format("%s/.ssh/%s.pem", home, keypairName);
        } else {
            filename = argument[0];
        }

        KeyPair keyPair = facade.createKeyPair(projectAndEnv, factory.getSavesFile(), filename);
        System.out.println(format("Created key %s with fingerprint %s", keyPair.getKeyName(),
                keyPair.getKeyFingerprint()));
    }

    @Override
    public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts, String... argumentForAction) throws CommandLineException {
        guardForProjectAndEnv(projectAndEnv);
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
