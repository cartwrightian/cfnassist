package tw.com.providers;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class ArtifactUploader {
    private static final Logger logger = LoggerFactory.getLogger(ArtifactUploader.class);

    private S3Client s3Client;
    private String bucketName;
    private Integer buildNumber;

    private static String S3_HOST = "s3.amazonaws.com";

    //private ProgressListener progressListener = new UploadProgressListener();

    public ArtifactUploader(S3Client s3Client, String bucketName, Integer buildNumber) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.buildNumber = buildNumber;
    }

    public List<Parameter> uploadArtifacts(Collection<Parameter> arts) {
        logger.info("Uploading artifacts to bucket " + bucketName);
        LinkedList<Parameter> urls = new LinkedList<>();
        for(Parameter artifact : arts) {
            urls.add(processArtifact(artifact));
        }
        return urls;
    }

    private Parameter processArtifact(Parameter artifact) {
        String fullFilePath = artifact.parameterValue();
        File item = FileUtils.getFile(fullFilePath);

        URI uri;
        if (item.isDirectory()) {
            for (File file : item.listFiles()) {
                if (file.isFile()) {
                    URI fileUrl = uploadItem(file.toPath());
                    logger.info(String.format("Uploaded %s at URL %s", file.getAbsolutePath(), fileUrl));
                } else {
                    logger.warn("Skipped item " + file.getAbsolutePath());
                }
            }
            uri = createUrl(bucketName, buildNumber.toString());
        } else {
            uri = uploadItem(item.toPath());
        }
        logger.info(String.format("Uploaded %s at URL %s", item.getAbsolutePath(), uri));
        return Parameter.builder().parameterKey(artifact.parameterKey()).parameterValue(uri.toString()).build();
    }

    private URI uploadItem(Path item) {
        Path fullFilePath = item.toAbsolutePath();
        String name = FilenameUtils.getName(fullFilePath.toString());
        String key = String.format("%s/%s", buildNumber, name);
        logger.debug(String.format("Uploading file %s to key %s", item.toAbsolutePath(), key));
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucketName).key(key).build();//. item);

        s3Client.putObject(putObjectRequest, item);
        return createUrl(bucketName, key);
    }

    private URI createUrl(String bucketName, String key) {
        return URI.create(String.format("https://%s/%s/%s", S3_HOST, bucketName, key));
    }

    public void delete(String filename) {
        String key = String.format("%s/%s",buildNumber,filename);
        logger.info(String.format("Delete from bucket '%s' key '%s'", bucketName, key));
        DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucketName).key(key).build();
        s3Client.deleteObject(request);
    }

}
