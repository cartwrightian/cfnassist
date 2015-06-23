package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.User;

public class IdentityProvider {
	private static final Logger logger = LoggerFactory.getLogger(IdentityProvider.class);

	private AmazonIdentityManagementClient iamClient;

	public IdentityProvider(AmazonIdentityManagementClient iamClient) {
		this.iamClient = iamClient;
	}

	// needs iam:GetUser
	public User getUserId() {
		logger.debug("Get current user");
		try {
			GetUserResult result = iamClient.getUser();
			User user = result.getUser();
			logger.info("Fetched current user: " + user);
			return user;
		}
		catch(AmazonServiceException exception) {
			logger.warn("Unable to fetch current user: " + exception.toString());
			return null;
		}	
	}

}
