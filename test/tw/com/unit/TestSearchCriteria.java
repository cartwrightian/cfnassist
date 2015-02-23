package tw.com.unit;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.cloudformation.model.Stack;

import tw.com.entity.EnvironmentTag;
import tw.com.entity.SearchCriteria;
import tw.com.entity.StackEntry;

public class TestSearchCriteria {
	
	private StackEntry entryA;
	private StackEntry entryB;
	private StackEntry entryC;
	private StackEntry entryD;
	private SearchCriteria criteria;
	private StackEntry entryE;
	

	@Before
	public void beforeEachTestRuns() {
		Stack stack = new Stack();
		entryA = new StackEntry("project", new EnvironmentTag("anEnv"), stack);
		entryB = new StackEntry("project", new EnvironmentTag("someOtherTag"), stack);
		entryC = new StackEntry("project", new EnvironmentTag("anEnv"), stack).setBuildNumber(42);
		entryD = new StackEntry("OtherProject", new EnvironmentTag("anEnv"), stack);
		entryE = new StackEntry("OtherProject", new EnvironmentTag("anEnv"), stack).setBuildNumber(42);
		criteria = new SearchCriteria();
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
