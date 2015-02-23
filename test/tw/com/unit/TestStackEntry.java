package tw.com.unit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.amazonaws.services.cloudformation.model.Stack;

import tw.com.entity.EnvironmentTag;
import tw.com.entity.StackEntry;

public class TestStackEntry {
	
	@Test
	public void shouldExtractBasenameFromEntryWithNoBuildNumber() {
		Stack stack = new Stack().withStackName("ProjectEnvTheBaseName");
		StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), stack);
		
		assertEquals("TheBaseName", entry.getBaseName());
	}
	
	@Test
	public void shouldExtractBasenameFromEntryWithBuildNumber() {
		Stack stack = new Stack().withStackName("Project42EnvTheBaseName");
		StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), stack);
		entry.setBuildNumber(42);
		
		assertEquals("TheBaseName",entry.getBaseName());
	}

}
