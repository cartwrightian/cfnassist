package tw.com.unit;

import com.amazonaws.services.cloudformation.model.Stack;
import org.junit.Test;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.StackEntry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

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

	@Test
    public void shouldHaveIndex() {
        StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), new Stack().withStackName("theStackName"));
        entry.setIndex(56);

        assertTrue(entry.hasIndex());
        assertEquals(new Integer(56), entry.getIndex());
    }

    @Test
    public void shouldHaveUpdateIndex() {
        StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), new Stack().withStackName("theStackName"));
        Set<Integer> updates = new HashSet<>(Arrays.asList(42));
        entry.setUpdateIndex(updates);

        assertTrue(entry.hasUpdateIndex());
        assertEquals(new HashSet<>(Arrays.asList(42)), entry.getUpdateIndex());
    }

    @Test
    public void shouldHaveEquality() {
        StackEntry entryA = new StackEntry("project", new EnvironmentTag("Env1"), new Stack());
        StackEntry entryB = new StackEntry("project", new EnvironmentTag("Env1"), new Stack());
        StackEntry entryC = new StackEntry("project", new EnvironmentTag("Env2"), new Stack());

        assertTrue(entryA.equals(entryB));
        assertTrue(entryB.equals(entryA));
        assertFalse(entryC.equals(entryA));
    }

}
