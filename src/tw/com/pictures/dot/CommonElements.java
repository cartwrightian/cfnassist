package tw.com.pictures.dot;

import tw.com.exceptions.CfnAssistException;

public interface CommonElements {
	void addSecurityGroup(String id, String label) throws CfnAssistException;
	
	void addPortRange(String uniqueId, String label) throws CfnAssistException;

	void connectWithLabel(String uniqueIdA, String uniqueIdB, String label);
}
