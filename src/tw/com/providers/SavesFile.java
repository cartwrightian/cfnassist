package tw.com.providers;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.OutputLogEventDecorator;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;

public class SavesFile {
    private static final Logger logger = LoggerFactory.getLogger(SavesFile.class);
    private Set<PosixFilePermission> permissionSet = new HashSet<>();
    OpenOption[] options = new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.APPEND,
            StandardOpenOption.WRITE};

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

    public boolean save(Path path, List<Stream<OutputLogEventDecorator>> fetchLogs) {
        if (Files.exists(path)) {
            logger.warn(format("File '%s' already exists", path.toAbsolutePath().toString()));
        }
        try {
            logger.info("Writing events to " + path.toAbsolutePath());
            PrintWriter printWriter = new PrintWriter(Files.newBufferedWriter(path, options), true);
            fetchLogs.forEach(stream -> stream.forEach(item -> printWriter.println(item.toString())));
            printWriter.close();
        } catch (IOException e) {
            logger.error("Unable to save to file " + path.toAbsolutePath(), e);
            return false;
        }
        return true;
    }
}
