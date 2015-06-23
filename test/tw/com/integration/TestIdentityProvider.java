package tw.com.integration;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.User;

import tw.com.EnvironmentSetupForTests;
import tw.com.providers.IdentityProvider;

public class TestIdentityProvider {

	private IdentityProvider identityProvider;
	private AmazonIdentityManagementClient iamClient;

	@Before
	public void shouldRunBeforeEachTest() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		iamClient = EnvironmentSetupForTests.createIamClient(credentialsProvider);
		identityProvider = new IdentityProvider(iamClient);
	}
	
	@Test
	public void shouldGetUserId() {
		User result = identityProvider.getUserId();
		assertNotNull(result);
		assertFalse(result.getUserName().isEmpty());
	}
}
