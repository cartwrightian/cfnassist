package tw.com.pictures.dot;

import java.io.IOException;

import software.amazon.awssdk.services.ec2.model.Vpc;

public interface Recorder {

	public abstract void write(String string);

	public abstract void writeline(String string);

	public abstract void writeLabel(String label);

	public abstract void beginFor(Vpc vpc, String prefix) throws IOException;

	public abstract void end() throws IOException;

}