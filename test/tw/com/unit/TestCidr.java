package tw.com.unit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import tw.com.entity.Cidr;
import tw.com.exceptions.CfnAssistException;

class TestCidr {
	
	@Test
    void shouldHaveDefaultCidr() {
		Cidr cidr = Cidr.Default();
		Assertions.assertTrue(cidr.isDefault());
	}
	
	@Test
    void shouldParseDefaultCidr() throws CfnAssistException {
		Cidr cidr = Cidr.parse("0.0.0.0/0");
		
		Assertions.assertTrue(cidr.isDefault());
	}
	
	@Test
    void shouldParseCidr() throws CfnAssistException {
		Cidr cidr = Cidr.parse("192.168.0.2/32");
		
		Assertions.assertFalse(cidr.isDefault());
		Assertions.assertEquals("192.168.0.2/32", cidr.toString());
	}
	
	@Test
    void shouldThrowOnBadFormat() {
		try {
			Cidr.parse("notvalid/xx");
			Assertions.fail("Should have thrown");
		} catch (CfnAssistException expected) {
			// no op
		}
	}

}
