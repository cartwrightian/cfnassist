package tw.com;

import tw.com.exceptions.CannotFindVpcException;

public interface SetsDeltaIndex {
	void setDeltaIndex(Integer newDelta) throws CannotFindVpcException;
}
