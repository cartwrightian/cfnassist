package tw.com.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.IamException;
import software.amazon.awssdk.services.iam.model.User;

public class IdentityProvider {
	private static final Logger logger = LoggerFactory.getLogger(IdentityProvider.class);

	private IamClient iamClient;

	public IdentityProvider(IamClient iamClient) {
		this.iamClient = iamClient;
	}

	// needs iam:GetUser
	public User getUserId() {
		logger.debug("Get current user");
		try {
			GetUserResponse result = iamClient.getUser();
			User user = result.user();
			logger.info("Fetched current user: " + user);
			return user;
		}
		catch(IamException exception) {
			logger.warn("Unable to fetch current user: " + exception.toString());
			return null;
		}	
	}

}
