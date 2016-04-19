package tw.com.providers;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class SavesFile {
    private static final Logger logger = LoggerFactory.getLogger(SavesFile.class);

    public boolean save(String destination, String contents) {
        File file = new File(destination);
        try {
            FileUtils.write(file, contents);
            return true;
        } catch (IOException e) {
            logger.error("Unable to save to file " + file.getAbsolutePath(), e);
            return false;
        }
    }

    public boolean exists(String savesFile) {
        return new File(savesFile).exists();
    }
}
