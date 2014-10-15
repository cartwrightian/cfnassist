package tw.com;

public interface ProvidesMonitoringARN {

	String getArn() throws NotReadyException;

}
