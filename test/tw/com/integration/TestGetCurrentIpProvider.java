package tw.com.integration;

import static org.junit.Assert.*;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import tw.com.exceptions.CfnAssistException;
import tw.com.providers.ProvidesCurrentIp;

public class TestGetCurrentIpProvider {

	@Test
	public void shouldGetCurrentIp() throws CfnAssistException {
		ProvidesCurrentIp provider = new ProvidesCurrentIp();
		InetAddress result = provider.getCurrentIp();
		assertNotNull(result);
	}
}
