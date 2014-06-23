package tw.com;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.s3.AmazonS3Client;

public class ArtifactUploader {
	private static final Logger logger = LoggerFactory.getLogger(ArtifactUploader.class);


	private AmazonS3Client s3Client;
	private String bucketName;
	private String buildNumber;

	public ArtifactUploader(AmazonS3Client s3Client, String bucketName, String buildNumber) {
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
		String name = FilenameUtils.getName(fullFilePath);		
		String key = String.format("%s/%s",buildNumber, name);
		logger.debug(String.format("Uploading file %s to key %s",fullFilePath, key));
		File file = FileUtils.getFile(fullFilePath);
		s3Client.putObject(bucketName, key, file);

		String url = s3Client.getResourceUrl(bucketName, key);
		logger.info(String.format("Uploaded %s at URL %s", fullFilePath, url));
		return new Parameter().withParameterKey(artifact.getParameterKey()).withParameterValue(url);
	}

	public void delete(String filename) {
		String key = String.format("%s/%s",buildNumber,filename);
		logger.info(String.format("Delete from bucket '%s' key '%s'", bucketName, key));
		s3Client.deleteObject(bucketName,key);
	}

}
