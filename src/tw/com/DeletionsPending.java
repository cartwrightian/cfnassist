package tw.com;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

public class DeletionsPending implements Iterable<DeletionPending> {
	LinkedList<DeletionPending> items;
	
	public DeletionsPending() {
		items = new LinkedList<DeletionPending>();
	}

	public void add(int delta, StackId stackId) {
		DeletionPending item = new DeletionPending(delta, stackId);
		items.add(item);	
	}

	@Override
	public Iterator<DeletionPending> iterator() {
		Collections.sort(items);
		return items.iterator();
	}

}
