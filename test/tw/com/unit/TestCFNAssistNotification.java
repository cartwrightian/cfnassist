package tw.com.unit;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.identitymanagement.model.User;

import tw.com.entity.CFNAssistNotification;

public class TestCFNAssistNotification {

	private User user;
	
	@Before
	public void beforeEachTestRuns() {
		user = new User("path", "userName", "userId", "userArn", new Date());
	}

	@Test
	public void shouldTestEquality() {
		CFNAssistNotification notifA = new CFNAssistNotification("stackA", "status1", user);
		CFNAssistNotification notifB = new CFNAssistNotification("stackA", "status1", user);
		CFNAssistNotification notifC = new CFNAssistNotification("stackX", "status1", user);
		
		assertEquals(notifA, notifB);
		assertEquals(notifB, notifA);
		assertFalse(notifA.equals(notifC));
	}
	
	@Test
	public void testToAndFromJSON() throws IOException {
		CFNAssistNotification notifA = new CFNAssistNotification("stackA", "status1", user);
		
		String json = CFNAssistNotification.toJSON(notifA);
		
		CFNAssistNotification result = CFNAssistNotification.fromJSON(json);
		
		assertEquals(notifA, result);
	}
}
