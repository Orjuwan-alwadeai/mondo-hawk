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
package org.hawk.orientdb;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hawk.core.graph.IGraphDatabase;
import org.hawk.core.graph.IGraphEdge;
import org.hawk.core.graph.IGraphNode;
import org.hawk.orientdb.indexes.IndexBasedEdgeStore;
import org.hawk.orientdb.util.OrientNameCleaner;

import com.orientechnologies.orient.core.db.record.OTrackedList;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class OrientNode implements IGraphNode {
	private static final String PREFIX_PROPERTY = "_hp_";

	/** Database that contains this node. */
	private final OrientDatabase graph;

	/** Identifier of the node within the database. */
	private ORID id;

	/** Keeps the changed document in memory until the next save. */
	private ODocument changedVertex;

	/** Should only be used with non-persistent IDs. */
	public OrientNode(ODocument doc, OrientDatabase graph) {
		if (doc.getIdentity().isPersistent()) {
			graph.getConsole().println("Warning, inefficient: OrientNode(ODocument) being used with persistent ID " + doc.getIdentity());
		}
		this.graph = graph;
		this.changedVertex = doc;
		this.id = doc.getIdentity();
	}

	public OrientNode(ORID id, OrientDatabase graph) {
		this.id = id;
		this.graph = graph;
	}

	@Override
	public ORID getId() {
		/*
		 * If we have a document, it's best to refer to it for the identity: if
		 * this OrientNode is used after a transaction, the ORID might have
		 * changed from a temporary to a persistent one.
		 */
		if (changedVertex != null) {
			return changedVertex.getIdentity();
		}
		return id;
	}

	@Override
	public Set<String> getPropertyKeys() {
		final Set<String> keys = new HashSet<>();
		ODocument tmpVertex = getDocument();
		for (String s : tmpVertex.fieldNames()) {
			if (s != null && s.startsWith(PREFIX_PROPERTY)) {
				keys.add(OrientNameCleaner.unescapeFromField(s.substring(PREFIX_PROPERTY.length())));
			}
		}
		return keys;
	}

	@Override
	public Object getProperty(String name) {
		final String fieldName = OrientNameCleaner.escapeToField(PREFIX_PROPERTY + name);
		ODocument tmpVertex = getDocument();
		final Object value = tmpVertex.field(fieldName);
		if (value instanceof OTrackedList<?>) {
			final OTrackedList<?> cValue = (OTrackedList<?>)value;
			Class<?> genericClass = cValue.getGenericClass();
			if (genericClass == null) {
				if (!cValue.isEmpty()) {
					genericClass = cValue.get(0).getClass();
				} else {
					genericClass = Object.class;
				}
			}
			final Object[] newArray = (Object[])Array.newInstance(genericClass, cValue.size());
			return cValue.toArray(newArray);
		}
		return value;
	}

	public static void setProperties(ODocument doc, Map<String, Object> props) {
		final Map<String, Object> mappedProps = new HashMap<>();
		for (Entry<String, Object> entry : props.entrySet()) {
			String fieldName = entry.getKey();
			mappedProps.put(OrientNameCleaner.escapeToField(PREFIX_PROPERTY + fieldName), entry.getValue());
		}
		doc.fromMap(mappedProps);
	}

	@Override
	public void setProperty(String name, Object value) {
		if (value == null) {
			removeProperty(name);
		} else {
			changedVertex = getDocument();
			changedVertex.field(OrientNameCleaner.escapeToField(PREFIX_PROPERTY + name), value);
			changedVertex.save();
		}
	}

	@Override
	public Iterable<IGraphEdge> getEdges() {
		return new IndexBasedEdgeStore(graph).getEdges(this);
	}

	@Override
	public Iterable<IGraphEdge> getEdgesWithType(String type) {
		return new IndexBasedEdgeStore(graph).getEdges(this, type);
	}

	@Override
	public Iterable<IGraphEdge> getOutgoingWithType(String type) {
		return new IndexBasedEdgeStore(graph).getOutgoing(this, type);
	}

	@Override
	public Iterable<IGraphEdge> getIncomingWithType(String type) {
		return new IndexBasedEdgeStore(graph).getIncoming(this, type);
	}

	@Override
	public Iterable<IGraphEdge> getIncoming() {
		return new IndexBasedEdgeStore(graph).getIncoming(this);
	}

	@Override
	public Iterable<IGraphEdge> getOutgoing() {
		return new IndexBasedEdgeStore(graph).getOutgoing(this);
	}

	@Override
	public void delete() {
		for (IGraphEdge e : getEdges()) {
			e.delete();
		}
		graph.getGraph().delete(getId());
		changedVertex = null;
	}

	@Override
	public IGraphDatabase getGraph() {
		return graph;
	}

	@Override
	public void removeProperty(String name) {
		changedVertex = getDocument();
		changedVertex.removeField(OrientNameCleaner.escapeToField(PREFIX_PROPERTY + name));
		changedVertex.save();
	}

	@Override
	public int hashCode() {
		final int prime = 5381;
		int result = 1;
		result = prime * result + ((getId() == null) ? 0 : getId().toString().hashCode());
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
		OrientNode other = (OrientNode) obj;
		if (getId() == null) {
			if (other.getId() != null)
				return false;
		} else if (!getId().equals(other.getId()))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "OrientNode [" + getId() + "]";
	}

	public ODocument getDocument() {
		if (changedVertex != null) {
			return changedVertex;
		}

		ODocument vertex = graph.getNodeById(getId()).changedVertex;
		if (vertex != null) {
			return vertex;
		}

		final ODocument loaded = graph.getGraph().load(getId());
		if (loaded != null) {
			loaded.deserializeFields();
		}
		/*else {
			graph.getConsole().printerrln("Loading node with id " + getId() + " from OrientDB produced null value");
			Thread.dumpStack();
		}*/
		return loaded;
	}

	public void save() {
		if (changedVertex != null && changedVertex.isDirty()) {
			changedVertex.save();
			id = changedVertex.getIdentity();
		}
		if (getId().isPersistent()) {
			changedVertex = null;
		}
	}
}
