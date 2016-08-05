/*******************************************************************************
 * Copyright (c) 2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.orientdb.indexes;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.hawk.orientdb.OrientDatabase;
import org.hawk.orientdb.util.OrientNameCleaner;

import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.index.OCompositeKey;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexAbstractCursor;
import com.orientechnologies.orient.core.index.OIndexCursor;
import com.orientechnologies.orient.core.index.OIndexManager;
import com.orientechnologies.orient.core.index.OIndexManagerProxy;
import com.orientechnologies.orient.core.index.OIndexRemote;
import com.orientechnologies.orient.core.index.OSimpleKeyIndexDefinition;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;

public class AbstractOrientIndex {

	private static final String KEY_IDX_SUFFIX = "_keys";

	protected static final class DocumentCollectionOIndexCursor extends OIndexAbstractCursor {
		private final Iterator<ODocument> documentIterator;

		protected DocumentCollectionOIndexCursor(Collection<ODocument> result) {
			documentIterator = result.iterator();
		}

		@Override
		public Map.Entry<Object, OIdentifiable> nextEntry() {
			if (!documentIterator.hasNext())
				return null;

			final ODocument value = documentIterator.next();

			return new Map.Entry<Object, OIdentifiable>() {
				@Override
				public Object getKey() {
					return value.field("key");
				}

				@Override
				public OIdentifiable getValue() {
					return value.field("rid");
				}

				@Override
				public OIdentifiable setValue(OIdentifiable value) {
					throw new UnsupportedOperationException("setValue");
				}
			};
		}
	}

	public enum IndexType { NODE, EDGE };
	private static final String SEPARATOR_SBTREE = "_@sbtree@_";

	protected final String name;
	protected final OrientDatabase graph;
	protected final IndexType type;

	/**
	 * Normalizes a value expression so it'll always be either an Integer, a
	 * Double or a String.
	 */
	protected static Object normalizeValue(Object valueExpr) {
		if (valueExpr instanceof Byte || valueExpr instanceof Short || valueExpr instanceof Long) {
			valueExpr = ((Number)valueExpr).intValue();
		} else if (valueExpr instanceof Float) {
			valueExpr = ((Float)valueExpr).doubleValue();
		} else if (valueExpr instanceof String || valueExpr instanceof Integer || valueExpr instanceof Double) {
			return valueExpr;
		}
		return valueExpr.toString();
	}

	public String getName() {
		return name;
	}

	public OrientDatabase getDatabase() {
		return graph;
	}

	public AbstractOrientIndex(String name, OrientDatabase graph, IndexType type) {
		this.name = name;
		this.graph = graph;
		this.type = type;
	}

	protected OIndex<?> getOrCreateFieldIndex(final String field, final Class<?> valueClass) {
		final String idxName = getSBTreeIndexName(valueClass);
		final OIndexManager indexManager = getIndexManager();
		OIndex<?> idx = indexManager.getIndex(idxName);
	
		if (idx == null) {
			createIndex(valueClass);

			// We need to fetch again the index: using the one that was just
			// created will result in multithreading exceptions from OrientDB
			idx = indexManager.getIndex(idxName);
		}

		if (type == IndexType.NODE) {
			graph.getIndexStore().addNodeFieldIndex(name, field);
		} else {
			graph.getIndexStore().addEdgeFieldIndex(name, field);
		}

		return idx;
	}

	/**
	 * Creates the SBTree index paired to this field within this logical index.
	 */
	protected void createIndex(final Class<?> keyClass) {
		final OIndexManager indexManager = getIndexManager();
	
		// Indexes have to be created outside transactions
		final String idxName = getSBTreeIndexName(keyClass);
		final boolean txWasOpen = graph.getGraph().getTransaction().isActive();
		if (txWasOpen) {
			graph.getConsole().println("Warning: prematurely committing a transaction so we can create index " + idxName);
			graph.saveDirty();
			graph.getGraph().commit();
			// OrientDB needs to explicitly close tx
			graph.getGraph().getTransaction().close();
		}

		// Index key type
		OType keyType = OType.STRING;
		if (keyClass == Byte.class || keyClass == Short.class || keyClass == Integer.class || keyClass == Long.class) {
			keyType = OType.INTEGER;
		} else if (keyClass == Float.class || keyClass == Double.class) {
			keyType = OType.DOUBLE;
		}
	
		// Create SBTree NOTUNIQUE index
		final OSimpleKeyIndexDefinition indexDef = new OSimpleKeyIndexDefinition( OType.STRING, keyType);
		indexManager.createIndex(idxName, OClass.INDEX_TYPE.NOTUNIQUE.toString(), indexDef, null, null, null, null);
		final OSimpleKeyIndexDefinition keyIndexDef = new OSimpleKeyIndexDefinition(OType.LINK, OType.STRING, keyType);
		indexManager.createIndex(idxName + KEY_IDX_SUFFIX, OClass.INDEX_TYPE.NOTUNIQUE.toString(), keyIndexDef, null, null, null, null);

		if (txWasOpen) {
			graph.getGraph().begin();
		}
	}

	protected String getSBTreeIndexName(final Class<?> keyClass) {
		OType keyType = OType.STRING;
		if (keyClass == Byte.class || keyClass == Short.class || keyClass == Integer.class || keyClass == Long.class) {
			keyType = OType.INTEGER;
		} else if (keyClass == Float.class || keyClass == Double.class) {
			keyType = OType.DOUBLE;
		}

		return OrientNameCleaner.escapeToField(name + SEPARATOR_SBTREE + keyType.name());
	}

	protected OIndex<?> getKeyIndex(Class<?> keyClass) {
		return getIndexManager().getIndex(getSBTreeIndexName(keyClass) + KEY_IDX_SUFFIX);
	}

	protected OIndexManager getIndexManager() {
		final ODatabaseDocumentTx rawGraph = graph.getGraph();
		final OIndexManagerProxy indexManager = rawGraph.getMetadata().getIndexManager();
		return indexManager;
	}

	protected OIndex<?> getIndex(final Class<? extends Object> valueClass) {
		return getIndexManager().getIndex(getSBTreeIndexName(valueClass));
	}

	public static Object getMaxValue(Class<?> klass) {
		if (klass == Double.class) {
			return Double.MAX_VALUE;
		} else if (klass == Integer.class) {
			return Integer.MAX_VALUE;
		} else {
			return Character.MAX_VALUE + "";
		}
	}

	public static Object getMinValue(Class<?> klass) {
		if (klass == Double.class) {
			return Double.MIN_VALUE;
		} else if (klass == Integer.class) {
			return Integer.MIN_VALUE;
		} else {
			return "";
		}
	}

	public static OIndexCursor iterateEntriesBetween(final String key, final Object from, final Object to,
			final boolean fromInclusive, final boolean toInclusive, final OIndex<?> idx, final ODatabase<?> db) {
		return iterateEntriesBetween(
				new OCompositeKey(key, from), fromInclusive,
				new OCompositeKey(key, to), toInclusive,
				idx, db);
	}

	public static OIndexCursor iterateEntriesBetween(final OCompositeKey cmpFrom, final boolean fromInclusive,
			final OCompositeKey cmpTo, final boolean toInclusive, final OIndex<?> idx, final ODatabase<?> db) {
		if (idx instanceof OIndexRemote) {
		    final StringBuilder query = new StringBuilder(OIndexRemote.QUERY_GET_VALUES_BEETWEN_SELECT);

		    if (fromInclusive)
		      query.append(OIndexRemote.QUERY_GET_VALUES_BEETWEN_INCLUSIVE_FROM_CONDITION);
		    else
		      query.append(OIndexRemote.QUERY_GET_VALUES_BEETWEN_EXCLUSIVE_FROM_CONDITION);

		    query.append(OIndexRemote.QUERY_GET_VALUES_AND_OPERATOR);

		    if (toInclusive)
		      query.append(OIndexRemote.QUERY_GET_VALUES_BEETWEN_INCLUSIVE_TO_CONDITION);
		    else
		      query.append(OIndexRemote.QUERY_GET_VALUES_BEETWEN_EXCLUSIVE_TO_CONDITION);

		    final String sql = String.format(query.toString(), idx.getName());
		    final OCommandSQL cmd = new OCommandSQL(sql);
		    final Collection<ODocument> result = db.command(cmd).execute(cmpFrom, cmpTo);

		    return new DocumentCollectionOIndexCursor(result);

		} else {
			return idx.iterateEntriesBetween(cmpFrom, fromInclusive, cmpTo, toInclusive, false);
		}
	}
}