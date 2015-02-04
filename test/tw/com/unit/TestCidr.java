package tw.com.unit;

import static org.junit.Assert.*;

import org.junit.Test;

import tw.com.entity.Cidr;
import tw.com.exceptions.CfnAssistException;

public class TestCidr {
	
	@Test
	public void shouldHaveDefaultCidr() {
		Cidr cidr = Cidr.Default();
		assertTrue(cidr.isDefault());
	}
	
	@Test 
	public void shouldParseDefaultCidr() throws CfnAssistException {
		Cidr cidr = Cidr.parse("0.0.0.0/0");
		
		assertTrue(cidr.isDefault());
	}
	
	@Test
	public void shouldParseCidr() throws CfnAssistException {
		Cidr cidr = Cidr.parse("192.168.0.2/32");
		
		assertFalse(cidr.isDefault());
		assertEquals("192.168.0.2/32", cidr.toString());
	}
	
	@Test
	public void shouldThrowOnBadFormat() {	
		try {
			Cidr.parse("notvalid/xx");
			fail("Should have thrown");
		} catch (CfnAssistException expected) {
			// no op
		}
	}

}
