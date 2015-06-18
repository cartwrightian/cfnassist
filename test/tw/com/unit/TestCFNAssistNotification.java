package tw.com.unit;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import tw.com.entity.CFNAssistNotification;

public class TestCFNAssistNotification {

	@Test
	public void shouldTestEquality() {
		CFNAssistNotification notifA = new CFNAssistNotification("stackA", "status1");
		CFNAssistNotification notifB = new CFNAssistNotification("stackA", "status1");
		CFNAssistNotification notifC = new CFNAssistNotification("stackX", "status1");
		
		assertEquals(notifA, notifB);
		assertEquals(notifB, notifA);
		assertFalse(notifA.equals(notifC));
	}
	
	@Test
	public void testToAndFromJSON() throws IOException {
		CFNAssistNotification notifA = new CFNAssistNotification("stackA", "status1");
		
		String json = CFNAssistNotification.toJSON(notifA);
		
		CFNAssistNotification result = CFNAssistNotification.fromJSON(json);
		
		assertEquals(notifA, result);
	}
}
