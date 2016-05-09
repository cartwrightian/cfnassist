package tw.com.commandline;

import java.io.IOException;
import java.util.List;

public class CommandExecutor {
    public void execute(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        process.waitFor();
    }
}
