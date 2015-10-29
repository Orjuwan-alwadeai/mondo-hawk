package org.hawk.orientdb.indexes;

import java.util.Iterator;
import java.util.Set;

final class StarKeyValueOIndexCursorFactoryIterable implements Iterable<OIndexCursorFactory> {
	private final Object valueExpr;
	private final Set<String> valueIdxNames;
	private final AbstractOrientIndex idx;

	StarKeyValueOIndexCursorFactoryIterable(Object valueExpr, AbstractOrientIndex idx, Set<String> valueIdxNames) {
		this.valueExpr = valueExpr;
		this.valueIdxNames = valueIdxNames;
		this.idx = idx;
	}

	@Override
	public Iterator<OIndexCursorFactory> iterator() {
		final Iterator<String> itIdxNames = valueIdxNames.iterator();
		return new Iterator<OIndexCursorFactory>() {
			@Override
			public boolean hasNext() {
				return itIdxNames.hasNext();
			}

			@Override
			public OIndexCursorFactory next() {
				return new SingleKeyValueQueryOIndexCursorFactory(valueExpr, idx, itIdxNames.next());
			}

			@Override
			public void remove() {
				itIdxNames.remove();
			}
		};
	}
}