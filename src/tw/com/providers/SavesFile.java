package tw.com.providers;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.OutputLogEventDecorator;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SavesFile {
    private static final Logger logger = LoggerFactory.getLogger(SavesFile.class);
    private Set<PosixFilePermission> permissionSet = new HashSet<>();

    public SavesFile() {
        permissionSet.add(PosixFilePermission.OWNER_READ);
        permissionSet.add(PosixFilePermission.OWNER_WRITE);
    }

    // use version that takes a Path
    @Deprecated
    public boolean save(String destination, String contents) {
        return save(Paths.get(destination),contents);
    }

    public boolean save(Path destination, String contents) {
        File file = destination.toFile();
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

    public void save(Path path, List<Stream<OutputLogEventDecorator>> fetchLogs) {
        try {
            logger.info("Writing events to " + path.toAbsolutePath());
            PrintWriter printWriter = new PrintWriter(Files.newBufferedWriter(path), true);
            fetchLogs.forEach(stream -> stream.forEach(item -> printWriter.println(item.toString())));
        } catch (IOException e) {
            logger.error("Unable to save to file " + path.toAbsolutePath(), e);
        }
    }
}
