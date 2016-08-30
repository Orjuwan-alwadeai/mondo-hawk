/*******************************************************************************
 * Copyright (c) 2016 Aston University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.orientdb.indexes;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawk.core.graph.IGraphEdge;
import org.hawk.core.graph.IGraphIterable;
import org.hawk.orientdb.OrientDatabase;
import org.hawk.orientdb.OrientEdge;
import org.hawk.orientdb.OrientNode;

import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.index.OCompositeKey;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexCursor;
import com.orientechnologies.orient.core.index.OIndexManager;
import com.orientechnologies.orient.core.index.OSimpleKeyIndexDefinition;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;

/**
 * Stores in/out edges as keys in an SB-Tree index, instead of as properties in
 * the documents themselves.
 */
public class IndexBasedEdgeStore {

	private OrientDatabase graph;
	private OIndex<?> edgeStore;

	private static final String IDX_NAME = "hawkEdgeStore";

	private static final String IDX_KEY_OUT = "O";
	private static final String IDX_KEY_IN = "I";
	private static final String IDX_KEY_LAST = "Z";

	public IndexBasedEdgeStore(OrientDatabase graph) {
		this.graph = graph;
		OIndexManager indexManager = graph.getGraph().getMetadata().getIndexManager();

		edgeStore = indexManager.getIndex(IDX_NAME);
		if (edgeStore == null) {
			final boolean txWasOpen = graph.getGraph().getTransaction().isActive();
			if (txWasOpen) {
				graph.getConsole().println("Warning: prematurely committing a transaction so we can create edge store");
				graph.getGraph().commit();
				// OrientDB needs to explicitly close tx
				graph.getGraph().getTransaction().close();
			}

			final OSimpleKeyIndexDefinition indexDef = new OSimpleKeyIndexDefinition(OType.LINK, // src
																									// node
					OType.STRING, // in/out (see IDX_KEY_* constants)
					OType.STRING);
			edgeStore = indexManager.createIndex(IDX_NAME, OClass.INDEX_TYPE.NOTUNIQUE.toString(), indexDef, null, null,
					null, null);

			if (txWasOpen) {
				graph.getGraph().begin();
			}
		}
	}

	public void add(OrientEdge edge) {
		for (OCompositeKey key : getKeys(edge)) {
			//System.out.println("Added " + key + " = " + edge.getDocument());
			edgeStore.put(key, edge.getDocument());
		}
	}

	public void remove(OrientEdge edge) {
		for (OCompositeKey key : getKeys(edge)) {
			edgeStore.remove(key, edge.getDocument());
		}
	}

	public IGraphIterable<IGraphEdge> getEdges(OrientNode node) {
		final OCompositeKey fromKey = new OCompositeKey(node.getDocument(), IDX_KEY_IN, "");
		final OCompositeKey toKey = new OCompositeKey(node.getDocument(), IDX_KEY_LAST, "");
		final boolean fromInclusive = true;
		final boolean toInclusive = true;
		final boolean ascOrder = false;

		return returnEdgesBetween(fromKey, toKey, fromInclusive, toInclusive, ascOrder);
	}

	@SuppressWarnings("unchecked")
	public IGraphIterable<IGraphEdge> getEdges(OrientNode node, String type) {
		final OCompositeKey inKey = new OCompositeKey(node.getDocument(), IDX_KEY_IN, type);
		final Collection<OIdentifiable> inResults = (Collection<OIdentifiable>) edgeStore.get(inKey);

		final OCompositeKey outKey = new OCompositeKey(node.getDocument(), IDX_KEY_OUT, type);
		final Collection<OIdentifiable> outResults = (Collection<OIdentifiable>) edgeStore.get(outKey);

		final Set<OIdentifiable> resultSet = new HashSet<>(inResults);
		resultSet.addAll(outResults);
		return new ResultSetIterable<>(resultSet, graph, IGraphEdge.class);
	}

	public IGraphIterable<IGraphEdge> getIncoming(OrientNode node) {
		final OCompositeKey fromKey = new OCompositeKey(node.getDocument(), IDX_KEY_IN, "");
		final OCompositeKey toKey = new OCompositeKey(node.getDocument(), IDX_KEY_OUT, "");
		final boolean fromInclusive = true;
		final boolean toInclusive = false;
		final boolean ascOrder = false;

		return returnEdgesBetween(fromKey, toKey, fromInclusive, toInclusive, ascOrder);
	}

	public IGraphIterable<IGraphEdge> getOutgoing(OrientNode node) {
		final OCompositeKey fromKey = new OCompositeKey(node.getDocument(), IDX_KEY_OUT, "");
		final OCompositeKey toKey = new OCompositeKey(node.getDocument(), IDX_KEY_LAST, "");
		final boolean fromInclusive = true;
		final boolean toInclusive = false;
		final boolean ascOrder = false;

		return returnEdgesBetween(fromKey, toKey, fromInclusive, toInclusive, ascOrder);
	}

	@SuppressWarnings("unchecked")
	public IGraphIterable<IGraphEdge> getIncoming(OrientNode node, String type) {
		final OCompositeKey inKey = new OCompositeKey(node.getDocument(), IDX_KEY_IN, type);
		final Collection<OIdentifiable> resultSet = (Collection<OIdentifiable>) edgeStore.get(inKey);
		return new ResultSetIterable<>(resultSet, graph, IGraphEdge.class);
	}

	@SuppressWarnings("unchecked")
	public IGraphIterable<IGraphEdge> getOutgoing(OrientNode node, String type) {
		final OCompositeKey outKey = new OCompositeKey(node.getDocument(), IDX_KEY_OUT, type);
		final Collection<OIdentifiable> resultSet = (Collection<OIdentifiable>) edgeStore.get(outKey);
		return new ResultSetIterable<>(resultSet, graph, IGraphEdge.class);
	}

	protected IGraphIterable<IGraphEdge> returnEdgesBetween(final OCompositeKey fromKey, final OCompositeKey toKey,
			final boolean fromInclusive, final boolean toInclusive, final boolean ascOrder) {
		final OIndexCursor cursor = edgeStore.iterateEntriesBetween(fromKey, fromInclusive, toKey, toInclusive,
				ascOrder);
		final Set<OIdentifiable> values = cursor.toValues();
		return new ResultSetIterable<>(values, graph, IGraphEdge.class);
	}

	protected List<OCompositeKey> getKeys(OrientEdge edge) {
		return Arrays.asList(new OCompositeKey(edge.getStartNode().getDocument(), IDX_KEY_OUT, edge.getType()),
				new OCompositeKey(edge.getEndNode().getDocument(), IDX_KEY_IN, edge.getType()));
	}

	public void flush() {
		edgeStore.flush();
	}
}
