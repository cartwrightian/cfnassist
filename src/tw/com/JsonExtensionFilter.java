package tw.com;

import java.io.File;
import java.io.FilenameFilter;

import org.apache.commons.io.FilenameUtils;

public class JsonExtensionFilter implements FilenameFilter {

	@Override
	public boolean accept(File dir, String name) {
		return FilenameUtils.getExtension(name).endsWith("json");
	}

}
