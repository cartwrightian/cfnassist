package tw.com.providers;

import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class ArtifactUploader {
	private static final Logger logger = LoggerFactory.getLogger(ArtifactUploader.class);

	private AmazonS3 s3Client;
	private String bucketName;
	private Integer buildNumber;

	private ProgressListener progressListener = new UploadProgressListener();

	public ArtifactUploader(AmazonS3 s3Client, String bucketName, Integer buildNumber) {
		this.s3Client = s3Client;
		this.bucketName = bucketName;
		this.buildNumber = buildNumber;
	}

	public List<Parameter> uploadArtifacts(Collection<Parameter> arts) {
		logger.info("Uploading artifacts to bucket " + bucketName);
		LinkedList<Parameter> urls = new LinkedList<Parameter>();
		for(Parameter artifact : arts) {
			urls.add(processArtifact(artifact));
		}
		return urls;
	}

	private Parameter processArtifact(Parameter artifact) {
		String fullFilePath = artifact.getParameterValue();
		File item = FileUtils.getFile(fullFilePath);
		
		URL url;
		if (item.isDirectory()) {
			for (File file : item.listFiles()) {
				if (file.isFile()) {
					URL fileUrl = uploadItem(file);
					logger.info(String.format("Uploaded %s at URL %s", file.getAbsolutePath(), fileUrl));
				} else {
					logger.warn("Skipped item " + file.getAbsolutePath());
				}
			}
			url = s3Client.getUrl(bucketName, buildNumber.toString());
		} else {
			url = uploadItem(item);
		}
		logger.info(String.format("Uploaded %s at URL %s", item.getAbsolutePath(), url));
		return new Parameter().withParameterKey(artifact.getParameterKey()).withParameterValue(url.toString());
	}

	private URL uploadItem(File item) {
		String fullFilePath = item.getAbsolutePath();
		String name = FilenameUtils.getName(fullFilePath);
		String key = String.format("%s/%s", buildNumber, name);
		logger.debug(String.format("Uploading file %s to key %s", item.getAbsolutePath(), key));
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, item);
		putObjectRequest.setGeneralProgressListener(progressListener);
		s3Client.putObject(putObjectRequest);
		//s3Client.putObject(bucketName, key, item);
		return s3Client.getUrl(bucketName, key);
	}

	public void delete(String filename) {
		String key = String.format("%s/%s",buildNumber,filename);
		logger.info(String.format("Delete from bucket '%s' key '%s'", bucketName, key));
		s3Client.deleteObject(bucketName,key);
	}

}
