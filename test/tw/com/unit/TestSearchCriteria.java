package tw.com.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.SearchCriteria;
import tw.com.entity.StackEntry;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSearchCriteria {
	
	private StackEntry entryA;
	private StackEntry entryB;
	private StackEntry entryC;
	private StackEntry entryD;
	private SearchCriteria criteria;
	private StackEntry entryE;
    private StackEntry entryF;
    private StackEntry entryG;

    @BeforeEach
	public void beforeEachTestRuns() {
		Stack stack = Stack.builder().build();
		entryA = new StackEntry("project", new EnvironmentTag("anEnv"), stack);
		entryB = new StackEntry("project", new EnvironmentTag("someOtherTag"), stack);
		entryC = new StackEntry("project", new EnvironmentTag("anEnv"), stack).setBuildNumber(42);
		entryD = new StackEntry("OtherProject", new EnvironmentTag("anEnv"), stack);
		entryE = new StackEntry("OtherProject", new EnvironmentTag("anEnv"), stack).setBuildNumber(42);
		entryF = new StackEntry("project", new EnvironmentTag("anEnv"), stack).setIndex(98);
        Set<Integer> updates= new HashSet<Integer>(asList(140));
        entryG = new StackEntry("project", new EnvironmentTag("anEnv"), stack).setUpdateIndex(updates);
		criteria = new SearchCriteria();
	}

    @Test
    public void shouldMatchOnEnvProjectAndUpdateIndex() {
        criteria.withEnv("anEnv").withUpdateIndex(140);

        assertFalse(criteria.matches(entryF));
        assertTrue(criteria.matches(entryG));
    }

    @Test
    public void shouldMatchOnEnvProjectAndIndex() {
        criteria.withEnv("anEnv").withIndex(98);

        assertFalse(criteria.matches(entryA));
        assertTrue(criteria.matches(entryF));
    }

	@Test
	public void shouldMatchOnEnvAndProject() {
		criteria.withEnv("anEnv").withProject("OtherProject");
		
		assertTrue(criteria.matches(entryD));
		assertFalse(criteria.matches(entryA));		
	}
	
	@Test
	public void shouldMatchOnEnvProjectAndBuild() {
		criteria.withEnv("anEnv").withProject("project").withBuild(42);
		
		assertTrue(criteria.matches(entryC));
		assertFalse(criteria.matches(entryA));
	}

	@Test
	public void shouldMatchOnEnvAndBuild() {
		criteria.withEnv("anEnv").withBuild(42);
		
		assertTrue(criteria.matches(entryC));
		assertTrue(criteria.matches(entryE));
		assertFalse(criteria.matches(entryA));
	}
	
	@Test
	public void shouldMatchOnEnv() {
		criteria.withEnv("anEnv");
		
		assertTrue(criteria.matches(entryA));
		assertFalse(criteria.matches(entryB));	
		assertTrue(criteria.matches(entryC));
	}
	
	@Test
	public void shouldMatchOnBuild() {
		criteria.withBuild(42);
		
		assertFalse(criteria.matches(entryA));	
		assertFalse(criteria.matches(entryB));
		assertTrue(criteria.matches(entryC));
	}
	
	@Test
	public void shouldMatchOnProject() {
		criteria.withProject("project");
		
		assertTrue(criteria.matches(entryA));
		assertTrue(criteria.matches(entryB));
		assertTrue(criteria.matches(entryC));
		assertFalse(criteria.matches(entryD));
	}
}
