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
package org.hawk.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hawk.core.IModelIndexer;
import org.hawk.core.graph.IGraphEdge;
import org.hawk.core.graph.IGraphNode;

/**
 * Read-only abstraction of a model element type in the graph populated by the
 * updater.
 */
public class TypeNode {
	private final IGraphNode node;
	private final String name; 

	// never use this field directly: use getSlots() instead, as we use lazy
	// initialization.
	private List<Slot> slots;

	public TypeNode(IGraphNode node) {
		this.node = node;
		this.name = (String)node.getProperty(IModelIndexer.IDENTIFIER_PROPERTY);
	}

	public IGraphNode getNode() {
		return node;
	}

	public MetamodelNode getMetamodel() {
		final Iterator<IGraphEdge> itEPackageEdges = node.getOutgoingWithType("epackage").iterator();
		return new MetamodelNode(itEPackageEdges.next().getEndNode());
	}

	public String getMetamodelURI() {
		return getMetamodel().getUri();
	}

	public String getTypeName() {
		return name;
	}

	public List<Slot> getSlots() {
		if (slots == null) {
			slots = new ArrayList<>();
			for (String propertyName : node.getPropertyKeys()) {
				// skip over the 'id' property, which is a friendly identifier and not a 'real' slot
				if (IModelIndexer.IDENTIFIER_PROPERTY.equals(propertyName)) continue;

				final Slot slot = new Slot(this, propertyName);
				if (slot.isAttribute() || slot.isReference() || slot.isMixed() || slot.isDerived()) {
					slots.add(slot);
				}
			}
		}
		return slots;
	}
	
	public Iterable<ModelElementNode> getAll() {
		final Iterable<IGraphEdge> iterableKind = node.getIncomingWithType(ModelElementNode.EDGE_LABEL_OFKIND);
		final Iterable<IGraphEdge> iterableType = node.getIncomingWithType(ModelElementNode.EDGE_LABEL_OFTYPE);
		return new Iterable<ModelElementNode>() {

			@Override
			public Iterator<ModelElementNode> iterator() {
				final Iterator<IGraphEdge> itKind = iterableKind.iterator();
				final Iterator<IGraphEdge> itType = iterableType.iterator();
				return new Iterator<ModelElementNode>() {

					@Override
					public boolean hasNext() {
						return itKind.hasNext() || itType.hasNext();
					}

					@Override
					public ModelElementNode next() {
						if (itKind.hasNext()) {
							return new ModelElementNode(itKind.next().getStartNode());
						} else {
							return new ModelElementNode(itType.next().getStartNode());
						}
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	public Iterable<ModelElementNode> getAllInstances() {
		return getAll();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((node == null) ? 0 : node.hashCode());
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
		TypeNode other = (TypeNode) obj;
		if (node == null) {
			if (other.node != null)
				return false;
		} else if (!node.equals(other.node))
			return false;
		return true;
	}
}