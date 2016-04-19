package tw.com.acceptance;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DeleteKeyPairRequest;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import tw.com.CLIArgBuilder;
import tw.com.EnvironmentSetupForTests;
import tw.com.commandline.Main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestCommandLineEC2Operations {

    private static AmazonEC2Client ec2Client;

    @BeforeClass
    public static void beforeAllTestsRun() {
        DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
        ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
    }

    @Ignore
    @Test
    public void shouldCreateKeyPair() {
        fail("todo");

//        String[] args = CLIArgBuilder.createKeyPair();
//        Main main = new Main(args);
//        int result = main.parse();
//
//        DeleteKeyPairRequest deleteRequest = new DeleteKeyPairRequest().withKeyName();
//        ec2Client.deleteKeyPair(deleteRequest);
//
//        assertEquals(0, result);
    }
}
