package tw.com;

import java.io.File;
import java.io.FilenameFilter;

import org.apache.commons.io.FilenameUtils;

public class TemplateExtensionFilter implements FilenameFilter {

	@Override
	public boolean accept(File dir, String name) {
		boolean json = FilenameUtils.getExtension(name).endsWith("json");
		boolean yaml = FilenameUtils.getExtension(name).endsWith("yaml");
		return json || yaml;
	}

}
