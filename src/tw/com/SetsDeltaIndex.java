package tw.com;

import tw.com.exceptions.CannotFindVpcException;

public interface SetsDeltaIndex {
	void setDeltaIndex(int newDelta) throws CannotFindVpcException;
}
