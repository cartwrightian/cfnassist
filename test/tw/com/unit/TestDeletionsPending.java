package tw.com.unit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.com.SetsDeltaIndex;
import tw.com.entity.DeletionPending;
import tw.com.entity.DeletionsPending;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CannotFindVpcException;

import java.util.LinkedList;
import java.util.List;

class TestDeletionsPending implements SetsDeltaIndex {

	private DeletionsPending pending;
	private Integer setDelta;
	
	@BeforeEach
	public void beforeEachTestRuns() {
		pending = new DeletionsPending();
		
		pending.add(2, new StackNameAndId("nameA", "112"));
		pending.add(1, new StackNameAndId("nameB", "113"));
		pending.add(3, new StackNameAndId("nameC", "114"));	
		
		setDelta = -100;
	}

	@Test
    void shouldIterateOverPendingsInCorrectOrder() {
		
		LinkedList<Integer> results = new LinkedList<>();
		for(DeletionPending item : pending) {
			results.add(item.getDelta());
		}
		
		Assertions.assertEquals(3, results.size());
		Assertions.assertEquals(3, (int)results.get(0));
		Assertions.assertEquals(2, (int)results.get(1));
		Assertions.assertEquals(1, (int)results.get(2));
	}
	
	@Test
    void shouldMarkItemsAsDeleted() {
		pending.markIdAsDeleted("114");	
		List<String> result = pending.getNamesOfDeleted();
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("nameC", result.get(0));
		Assertions.assertTrue(pending.hasMore());
		
		pending.markIdAsDeleted("112");
		result = pending.getNamesOfDeleted();
		Assertions.assertEquals(2, result.size());
		Assertions.assertEquals("nameA", result.get(1));
		Assertions.assertTrue(pending.hasMore());
		
		pending.markIdAsDeleted("113");
		result = pending.getNamesOfDeleted();
		Assertions.assertEquals(3, result.size());
		Assertions.assertEquals("nameB", result.get(2));
		Assertions.assertFalse(pending.hasMore());
	}
	
	@Test
    void shouldUpdateDeltaIndexCorrectly() throws CannotFindVpcException {
		pending.markIdAsDeleted("114");
		pending.markIdAsDeleted("112");
		
		pending.updateDeltaIndex(this);
		Assertions.assertEquals(1, setDelta);
	}

	@Override
	public void setDeltaIndex(Integer setDelta) throws CannotFindVpcException {
		this.setDelta = setDelta;	
	}

}
