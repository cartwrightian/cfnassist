package tw.com.integration;

import static org.junit.Assert.*;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.ArtifactUploader;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class TestArtifactUploader {
	
	private static final String BUILD_NUMBER = "6574";
	private static final String KEY_A = BUILD_NUMBER+"/instance.json";
	private static final String KEY_B = BUILD_NUMBER+"/simpleStack.json";
	
	private static AmazonS3Client s3Client;

	@BeforeClass 
	public static void beforeAllTestsRun() {
		s3Client = EnvironmentSetupForTests.createS3Client(new DefaultAWSCredentialsProviderChain());
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
			s3Client.deleteObject(EnvironmentSetupForTests.BUCKET_NAME, KEY_A);
			s3Client.deleteObject(EnvironmentSetupForTests.BUCKET_NAME, KEY_B);
		} 
		catch(AmazonS3Exception exception) {
			System.out.println(exception);
		}
	}

	@Test
	public void expectUploadAndURLsReturnedBack() {		
		List<Parameter> arts = new LinkedList<Parameter>();
		
		// any files would do here
		Parameter artA = new Parameter().withParameterKey("urlA").withParameterValue(FilesForTesting.INSTANCE); 
		Parameter artB = new Parameter().withParameterKey("urlB").withParameterValue(FilesForTesting.SIMPLE_STACK);
		arts.add(artA);
		arts.add(artB);
		
		ArtifactUploader uploader = new ArtifactUploader(s3Client, EnvironmentSetupForTests.BUCKET_NAME, BUILD_NUMBER);
		List<Parameter> results = uploader.uploadArtifacts(arts);
		
		assertEquals(arts.size(), results.size());
		
		// expect keys remains same
		assertEquals(arts.get(0).getParameterKey(), results.get(0).getParameterKey());
		assertEquals(arts.get(1).getParameterKey(), results.get(1).getParameterKey());
		
		// expect value the S3 URL
		assertEquals(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_A, results.get(0).getParameterValue());
		assertEquals(EnvironmentSetupForTests.S3_PREFIX+"/"+KEY_B, results.get(1).getParameterValue());
		
		// check upload actually happened	
		List<S3ObjectSummary> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_B));
	}
	
	@Test
	public void canDeleteArtifactsFromS3() {
		List<Parameter> arts = new LinkedList<Parameter>();
		
		// any files would do here
		Parameter artA = new Parameter().withParameterKey("urlA").withParameterValue(FilesForTesting.INSTANCE); 
		arts.add(artA);	
		ArtifactUploader uploader = new ArtifactUploader(s3Client, EnvironmentSetupForTests.BUCKET_NAME, BUILD_NUMBER);
		uploader.uploadArtifacts(arts);	
		
		// check upload actually happened	
		List<S3ObjectSummary> objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		assertTrue(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
		
		uploader.delete("instance.json");
		objectSummaries = EnvironmentSetupForTests.getBucketObjects(s3Client);
		assertFalse(EnvironmentSetupForTests.isContainedIn(objectSummaries, KEY_A));
	}

	

}
