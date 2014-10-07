package tw.com;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.exceptions.CannotFindVpcException;

public class DeletionsPending implements Iterable<DeletionPending> {
	private static final Logger logger = LoggerFactory.getLogger(DeletionsPending.class);

	@Override
	public String toString() {
		return "DeletionsPending [items=" + items + "]";
	}

	LinkedList<DeletionPending> items;
	LinkedList<DeletionPending> deleted;
	
	public DeletionsPending() {
		items = new LinkedList<DeletionPending>();
		deleted = new LinkedList<DeletionPending>();
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

	public int remaining() {
		return items.size();
	}

	public boolean containsStackId(String stackId) {
		for(DeletionPending item : items) {
			if (item.getStackId().equals(stackId)) {
				return true;
			}
		}
		return false;
	}

	public void markIdAsDeleted(String stackId) {
		for(DeletionPending item : items) {
			StackId itemStackId = item.getStackId();
			if (itemStackId.getStackId().equals(stackId)) {
				logger.info(String.format("Matched stackid %s, marking stack as deleted", stackId));
				deleted.add(item);
				return;
			}
		}
	}

	public boolean hasMore() {
		return deleted.size()<items.size();
	}

	public void updateDeltaIndex(SetsDeltaIndex setsDeltaIndex) throws CannotFindVpcException {
		if (deleted.size()==0) {
			logger.warn("Failed to delete any stacks");
			return;
		}
		int lowest = deleted.get(0).getDelta()-1;
		for(DeletionPending update : deleted) {
			int delta = update.getDelta()-1;
			if (delta<lowest) {
				lowest=delta;
			}
		}	
		logger.info(String.format("Updating delta index to %s", lowest));
		setsDeltaIndex.setDeltaIndex(lowest);
	}

	public List<String> getNamesOfDeleted() {
		List<String> names = new LinkedList<String>();
		for(DeletionPending update : deleted) {
			names.add(update.getStackId().getStackName());
		}
		return names;
	}

}
