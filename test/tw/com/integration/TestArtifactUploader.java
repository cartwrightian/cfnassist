package tw.com.integration;

import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.providers.ArtifactUploader;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class TestArtifactUploader {

    private static final Integer BUILD_NUMBER = 6574;
    private static final String KEY_A = BUILD_NUMBER+"/instance.json";
    private static final String KEY_B = BUILD_NUMBER+"/simpleStack.json";

    private static S3Client s3Client;

    @BeforeClass
    public static void beforeAllTestsRun() {
        s3Client = EnvironmentSetupForTests.createS3Client();
    }

    @After
    public void afterEachTestHasRun() {
        deleteTestKeysFromBucket();
    }

    @Before
    public void beforeEachTestRuns() {
        deleteTestKeysFromBucket();
    }

    private void deleteTestKeysFromBucket() {
        try {
            deleteKey(KEY_A);
            deleteKey(KEY_B);
            deleteKey(BUILD_NUMBER+"/cfnassit-1.0.DEV.zip");
            deleteKey(BUILD_NUMBER+"/01createSubnet.json");
            deleteKey(BUILD_NUMBER+"/02createAcls.json");
        }
        catch(S3Exception exception) {
            System.out.println(exception);
        }
    }

    private void deleteKey(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder().
                bucket(EnvironmentSetupForTests.BUCKET_NAME).key(key).build() ;
        s3Client.deleteObject(request);
    }

    @Test
    public void expectUploadFilesAndURLsReturnedBack() {
        List<Parameter> arts = new LinkedList<>();

        // any files would do here
        Parameter artA = createParameter("urlA", FilesForTesting.INSTANCE);
        Parameter artB = createParameter("urlB", FilesForTesting.SIMPLE_STACK);
        arts.add(artA);
        arts.add(artB);

        ArtifactUploader uploader = new ArtifactUploader(s3Client, EnvironmentSetupForTests.BUCKET_NAME, BUILD_NUMBER);
        List<Parameter> results = uploader.uploadArtifacts(arts);

        assertEquals(arts.size(), results.size());

        // expect keys remains same
        assertEquals(arts.get(0).parameterKey(), results.get(0).parameterKey());
        assertEquals(arts.get(1).parameterKey(), results.get(1).parameterKey());

        // expect value the S3 URL
        assertEquals(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_A, results.get(0).parameterValue());
        assertEquals(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_B, results.get(1).parameterValue());

        // check upload actually happened
        List<S3Object> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);

        assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
        assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_B));
    }

    private Parameter createParameter(String parameterKey, String parameterValue) {
        return Parameter.builder().parameterKey(parameterKey).parameterValue(parameterValue).build();
    }

    @Test
    public void expectUploadDirWithFolderURLReturnedBack() {

        // any folder with files would do here
        String folderPath = FilesForTesting.ORDERED_SCRIPTS_FOLDER; // 2 items

        File folder = new File(folderPath);

        FilenameFilter filter = (dir, name) -> name.endsWith(".json");
        String[] filesOnDisc = folder.list(filter);
        assertNotNull(filesOnDisc);

        Parameter folderParam = createParameter("folder", folderPath);

        ArtifactUploader uploader = new ArtifactUploader(s3Client, EnvironmentSetupForTests.BUCKET_NAME, BUILD_NUMBER);
        List<Parameter> results = uploader.uploadArtifacts(Collections.singletonList(folderParam));

        assertEquals(1, results.size());
        String s3Prefix = EnvironmentSetupForTests.S3_PREFIX+"/"+BUILD_NUMBER;
        assertEquals(s3Prefix, results.get(0).parameterValue());

        List<S3Object> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);

        assertEquals(objectSummaries.toString(), filesOnDisc.length, objectSummaries.size());

        for(String file : filesOnDisc) {
            EnvironmentSetupForTests.isContainedIn(objectSummaries, String.format("%s/%s", s3Prefix, FilenameUtils.getName(file)));
        }
    }

    @Test
    public void canDeleteArtifactsFromS3() {
        List<Parameter> arts = new LinkedList<>();

        // any files would do here
        Parameter artA = createParameter("urlA", FilesForTesting.INSTANCE);
        arts.add(artA);
        ArtifactUploader uploader = new ArtifactUploader(s3Client, EnvironmentSetupForTests.BUCKET_NAME, BUILD_NUMBER);
        uploader.uploadArtifacts(arts);

        // check upload actually happened
        List<S3Object> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
        assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));

        uploader.delete("instance.json");
        objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
        assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
    }

//	@Test
//	@Ignore("For debugging of large file uploads only")
//	public void testUploadLargeFile() {
//		List<Parameter> arts = new LinkedList<Parameter>();
//		Parameter artA = new Parameter().withParameterKey("urlA").withParameterValue("cfnassit-1.0.DEV.zip"); 
//		
//		arts.add(artA);
//		
//		ArtifactUploader uploader = new ArtifactUploader(s3Client, EnvironmentSetupForTests.BUCKET_NAME, BUILD_NUMBER);
//		uploader.uploadArtifacts(arts);
//				
//	}

}
