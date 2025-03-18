package tw.com.unit;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.StackEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestStackEntry {
	
	@Test
	public void shouldExtractBasenameFromEntryWithNoBuildNumber() {
		Stack stack = createAStack("ProjectEnvTheBaseName");
		StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), stack);
		
		assertEquals("TheBaseName", entry.getBaseName());
	}
	
	@Test
	public void shouldExtractBasenameFromEntryWithBuildNumber() {
		Stack stack = createAStack("Project42EnvTheBaseName");
		StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), stack);
		entry.setBuildNumber(42);
		
		assertEquals("TheBaseName",entry.getBaseName());
	}

    private Stack createAStack(String name) {
        return Stack.builder().stackName(name).build();
    }

    @Test
    public void shouldHaveIndex() {
        StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), createAStack("theStackName"));
        entry.setIndex(56);

        assertTrue(entry.hasIndex());
        assertEquals(56, entry.getIndex());
    }

    @Test
    public void shouldHaveUpdateIndex() {
        StackEntry entry = new StackEntry("Project", new EnvironmentTag("Env"), createAStack("theStackName"));
        Set<Integer> updates = new HashSet<>(List.of(42));
        entry.setUpdateIndex(updates);

        assertTrue(entry.hasUpdateIndex());
        assertEquals(new HashSet<>(List.of(42)), entry.getUpdateIndex());
    }

    @Test
    public void shouldHaveEquality() {
        StackEntry entryA = new StackEntry("project", new EnvironmentTag("Env1"), createAStack(""));
        StackEntry entryB = new StackEntry("project", new EnvironmentTag("Env1"), createAStack(""));
        StackEntry entryC = new StackEntry("project", new EnvironmentTag("Env2"), createAStack(""));

        assertEquals(entryA, entryB);
        assertEquals(entryB, entryA);
        assertNotEquals(entryC, entryA);
    }

}
