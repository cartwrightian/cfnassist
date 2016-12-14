package tw.com.providers;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

public class SavesFile {
    private static final Logger logger = LoggerFactory.getLogger(SavesFile.class);
    private Set<PosixFilePermission> permissionSet = new HashSet<>();

    public SavesFile() {
        permissionSet.add(PosixFilePermission.OWNER_READ);
        permissionSet.add(PosixFilePermission.OWNER_WRITE);
    }

    public boolean save(String destination, String contents) {
        File file = new File(destination);
        try {
            FileUtils.write(file, contents, Charset.defaultCharset());
            return true;
        } catch (IOException e) {
            logger.error("Unable to save to file " + file.getAbsolutePath(), e);
            return false;
        }
    }

    public boolean exists(String savesFile) {
        return new File(savesFile).exists();
    }

    public void ownerOnlyPermisssion(String filename) throws IOException {
        Files.setPosixFilePermissions(Paths.get(filename), permissionSet);
    }
}
