package tw.com.repository;

import tw.com.exceptions.WrongNumberOfStacksException;

public interface CheckStackExists {
	boolean stackExists(String stackName) throws WrongNumberOfStacksException;
}
