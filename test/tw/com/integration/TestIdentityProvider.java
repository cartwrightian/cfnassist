package tw.com.integration;

import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.User;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.IdentityProvider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class TestIdentityProvider {

    private IdentityProvider identityProvider;
    private IamClient iamClient;

    @Before
    public void shouldRunBeforeEachTest() {
        iamClient = EnvironmentSetupForTests.createIamClient();
        identityProvider = new IdentityProvider(iamClient);
    }

    @Test
    public void shouldGetUserId() {
        User result = identityProvider.getUserId();
        assertNotNull(result);
        assertFalse(result.userName().isEmpty());
    }
}
