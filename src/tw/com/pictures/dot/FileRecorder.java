package tw.com.pictures.dot;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.ec2.model.Vpc;

public class FileRecorder implements Recorder {
	private static final Logger logger = LoggerFactory.getLogger(FileRecorder.class);
	
	StringBuilder builder;
	private FileWriter fw;
	private Path folder;
	
	public FileRecorder(Path folder) {
		this.folder = folder;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileRecorder other = (FileRecorder) obj;
		if (folder == null) {
			if (other.folder != null)
				return false;
		} else if (!folder.equals(other.folder))
			return false;
		return true;
	}

	@Override
	public void write(String string) {
		builder.append(string);
	}
	
	@Override
	public void writeline(String string) {
		builder.append(string);
		builder.append("\n");
	}

	@Override
	public void writeLabel(String label) {
		builder.append(String.format("label = \"%s\";", label));
		builder.append("\n");
	}
	
	@Override
	public String toString() {
		return builder.toString();
	}

	@Override
	public void beginFor(Vpc vpc, String prefix) throws IOException {
		builder = new StringBuilder();
		String diagramFilename = prefix+vpc.vpcId()+".dot";
		Path target = Paths.get(folder.toString(), diagramFilename);
		logger.info(">>>>> Saving to " + target.toAbsolutePath());
		if (!Files.exists(folder)) {
			logger.warn("Target folder is missing, creating " + folder);
			Files.createDirectory(folder);
		}
		fw = new FileWriter(target.toFile());	
	}

	@Override
	public void end() throws IOException {
		fw.append(builder.toString());
		fw.close();
	}

}
