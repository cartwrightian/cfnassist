package tw.com.unit;


import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.SavesFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.Assert.*;

public class TestSavesFile {
    private File testFile = new File("testFile");
    private SavesFile savesFile;
    private Path filename;

    @BeforeEach
    public void beforeEachTestRuns() {
        FileUtils.deleteQuietly(testFile);
        savesFile = new SavesFile();
        filename = Paths.get(testFile.getAbsolutePath());
    }

    @AfterEach
    public void afterEachTestRuns() {
        FileUtils.deleteQuietly(testFile);
    }

    @Test
    public void shouldCheckIfFileExists() throws IOException {
        FileUtils.touch(testFile);
        assertTrue(savesFile.exists(filename));

        testFile.delete();
        assertFalse(savesFile.exists(filename));
    }

    @Test
    public void shouldSaveStringInFile() throws IOException {
        savesFile.save(filename, "someText");

        String result = FileUtils.readFileToString(testFile, Charset.defaultCharset());

        assertEquals("someText", result);
    }

    @Test
    public void shouldChangePermissions() throws IOException {
        savesFile.save(filename, "someText");
        savesFile.ownerOnlyPermisssion(filename);

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(filename, LinkOption.NOFOLLOW_LINKS);

        EnvironmentSetupForTests.checkKeyPairFilePermissions(permissions);
    }


}
