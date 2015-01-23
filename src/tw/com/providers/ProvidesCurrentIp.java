package tw.com.providers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.CfnAssistException;

public class ProvidesCurrentIp {
	private static final String GETIP_URL = "http://checkip.amazonaws.com";
	private static final Logger logger = LoggerFactory.getLogger(ProvidesCurrentIp.class);


	public InetAddress getCurrentIp() throws CfnAssistException {
        try {
        	URL whatismyip = new URL(GETIP_URL);
            BufferedReader in = null;
            logger.debug("Attempt to fetch public IP from " + GETIP_URL);
            in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            String ip = in.readLine();
            logger.info("Got public IP as " + ip);
            in.close();
            return Inet4Address.getByName(ip);
        } catch (IOException e) {
            throw new CfnAssistException("Unable to get currnet public ip " + e.getMessage());
		} 
	}
}
