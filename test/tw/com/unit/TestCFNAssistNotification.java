package tw.com.unit;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import software.amazon.awssdk.services.iam.model.User;
import tw.com.EnvironmentSetupForTests;
import tw.com.entity.CFNAssistNotification;

class TestCFNAssistNotification {

	private User user;
	
	@BeforeEach
	public void beforeEachTestRuns() {
		user = EnvironmentSetupForTests.createUser();
	}

	@Test
    void shouldTestEquality() {
		CFNAssistNotification notifA = new CFNAssistNotification("stackA", "status1", user);
		CFNAssistNotification notifB = new CFNAssistNotification("stackA", "status1", user);
		CFNAssistNotification notifC = new CFNAssistNotification("stackX", "status1", user);
		
		Assertions.assertEquals(notifA, notifB);
		Assertions.assertEquals(notifB, notifA);
		Assertions.assertFalse(notifA.equals(notifC));
	}
	
	@Test
    void testToAndFromJSON() throws IOException {
		CFNAssistNotification notifA = new CFNAssistNotification("stackA", "status1", user);
		
		String json = CFNAssistNotification.toJSON(notifA);
		
		CFNAssistNotification result = CFNAssistNotification.fromJSON(json);
		
		Assertions.assertEquals(notifA, result);
	}
}
