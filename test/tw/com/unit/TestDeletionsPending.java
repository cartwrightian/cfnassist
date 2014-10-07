package tw.com.unit;

import static org.junit.Assert.*;

import java.util.LinkedList;

import org.junit.Test;

import tw.com.DeletionPending;
import tw.com.DeletionsPending;
import tw.com.StackId;

public class TestDeletionsPending {

	@Test
	public void shouldIterateOverPendingsInCorrectOrder() {
		DeletionsPending list = new DeletionsPending();
		
		list.add(1, new StackId("112", "nameA"));
		list.add(0, new StackId("113", "nameB"));
		list.add(2, new StackId("114", "nameC"));
		
		LinkedList<Integer> results = new LinkedList<Integer>();
		for(DeletionPending pending : list) {
			results.add(pending.getDelta());
		}
		
		assertEquals(3, results.size());
		assertEquals(2, (int)results.get(0));
		assertEquals(1, (int)results.get(1));
		assertEquals(0, (int)results.get(2));
	}
}
