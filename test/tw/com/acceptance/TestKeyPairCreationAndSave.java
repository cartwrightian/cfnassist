package tw.com.acceptance;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tw.com.CLIArgBuilder;
import tw.com.EnvironmentSetupForTests;
import tw.com.commandline.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestKeyPairCreationAndSave {

    private static Ec2Client ec2Client;

    @BeforeAll
    public static void beforeAllTestsRun() {
        ec2Client = EnvironmentSetupForTests.createEC2Client();
    }

    @Test
    public void shouldCreateKeyPairWithFilename() throws IOException {
        String keypairName = "CfnAssist_Test";

        deleteKeyPair(keypairName);

        String filename = "testFilenameForPem.tmp";
        Path path = Paths.get(filename);
        Files.deleteIfExists(path);

        String[] args = CLIArgBuilder.createKeyPair(filename);
        Main main = new Main(args);
        int commandResult = main.parse();

        List<KeyPairInfo> keys = deleteKeyPair(keypairName);

        // now do the asserts
        assertEquals(0, commandResult);
        assertEquals(1, keys.size());
        assertEquals(keypairName, keys.get(0).keyName());

        assertTrue(Files.exists(path));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(Paths.get(filename), LinkOption.NOFOLLOW_LINKS);
        EnvironmentSetupForTests.checkKeyPairFilePermissions(permissions);

        Files.deleteIfExists(path);
    }

    private List<KeyPairInfo> deleteKeyPair(String keypairName) {
        List<KeyPairInfo> keys;
        try {
            DescribeKeyPairsRequest query = DescribeKeyPairsRequest.builder().keyNames(keypairName).build();
            DescribeKeyPairsResponse keysFound = ec2Client.describeKeyPairs(query);
            keys = keysFound.keyPairs();
        } catch (Ec2Exception exception) {
            keys = new LinkedList<>();
        }

        if (keys.size() > 0) {
            DeleteKeyPairRequest deleteRequest = DeleteKeyPairRequest.builder().keyName(keypairName).build();
            ec2Client.deleteKeyPair(deleteRequest);
        }
        return keys;
    }
}
