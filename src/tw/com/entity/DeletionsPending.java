package tw.com.entity;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.SetsDeltaIndex;
import tw.com.exceptions.CannotFindVpcException;

public class DeletionsPending implements Iterable<DeletionPending> {
	private static final Logger logger = LoggerFactory.getLogger(DeletionsPending.class);

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((deleted == null) ? 0 : deleted.hashCode());
		result = prime * result + ((items == null) ? 0 : items.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DeletionsPending other = (DeletionsPending) obj;
		if (deleted == null) {
			if (other.deleted != null)
				return false;
		} else if (!deleted.equals(other.deleted))
			return false;
		if (items == null) {
			if (other.items != null)
				return false;
		} else if (!items.equals(other.items))
			return false;
		return true;
	}

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

	public void add(int delta, StackNameAndId stackId) {
		DeletionPending item = new DeletionPending(delta, stackId);
		items.add(item);	
	}

	@Override
	public Iterator<DeletionPending> iterator() {
		Collections.sort(items);
		return items.iterator();
	}

	public void markIdAsDeleted(String stackId) {
		for(DeletionPending item : items) {
			StackNameAndId itemStackId = item.getStackId();
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
