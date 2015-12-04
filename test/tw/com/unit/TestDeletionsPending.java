package tw.com.unit;

import org.junit.Before;
import org.junit.Test;
import tw.com.SetsDeltaIndex;
import tw.com.entity.DeletionPending;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CannotFindVpcException;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class TestDeletionsPending implements SetsDeltaIndex {

	private DeletionsPending pending;
	private Integer setDelta;
	
	@Before
	public void beforeEachTestRuns() {
		pending = new DeletionsPending();
		
		pending.add(2, new StackNameAndId("nameA", "112"));
		pending.add(1, new StackNameAndId("nameB", "113"));
		pending.add(3, new StackNameAndId("nameC", "114"));	
		
		setDelta = -100;
	}

	@Test
	public void shouldIterateOverPendingsInCorrectOrder() {
		
		LinkedList<Integer> results = new LinkedList<>();
		for(DeletionPending item : pending) {
			results.add(item.getDelta());
		}
		
		assertEquals(3, results.size());
		assertEquals(3, (int)results.get(0));
		assertEquals(2, (int)results.get(1));
		assertEquals(1, (int)results.get(2));
	}
	
	@Test
	public void shouldMarkItemsAsDeleted() {
		pending.markIdAsDeleted("114");	
		List<String> result = pending.getNamesOfDeleted();
		assertEquals(1, result.size());
		assertEquals("nameC", result.get(0));
		assertTrue(pending.hasMore());
		
		pending.markIdAsDeleted("112");
		result = pending.getNamesOfDeleted();
		assertEquals(2, result.size());
		assertEquals("nameA", result.get(1));
		assertTrue(pending.hasMore());
		
		pending.markIdAsDeleted("113");
		result = pending.getNamesOfDeleted();
		assertEquals(3, result.size());
		assertEquals("nameB", result.get(2));
		assertFalse(pending.hasMore());
	}
	
	@Test
	public void shouldUpdateDeltaIndexCorrectly() throws CannotFindVpcException {
		pending.markIdAsDeleted("114");
		pending.markIdAsDeleted("112");
		
		pending.updateDeltaIndex(this);
		assertEquals(new Integer(1), setDelta);
	}

	@Override
	public void setDeltaIndex(Integer setDelta) throws CannotFindVpcException {
		this.setDelta = setDelta;	
	}

}
