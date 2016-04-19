package tw.com.unit;


import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tw.com.providers.SavesFile;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSavesFile {
    File testFile = new File("testFile");
    private SavesFile savesFile;
    private String filename;

    @Before
    public void beforeEachTestRuns() {
        FileUtils.deleteQuietly(testFile);
        savesFile = new SavesFile();
        filename = testFile.getAbsolutePath();
    }

    @After
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

        String result = FileUtils.readFileToString(testFile);

        assertEquals("someText", result);
    }
}
