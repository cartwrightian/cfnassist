package tw.com;

import tw.com.exceptions.NotReadyException;

public interface ProvidesMonitoringARN {

	String getArn() throws NotReadyException;

}
