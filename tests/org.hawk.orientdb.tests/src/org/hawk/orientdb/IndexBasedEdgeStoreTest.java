package org.hawk.orientdb;

import static org.junit.Assert.assertEquals;

import org.hawk.core.graph.IGraphTransaction;
import org.hawk.core.util.DefaultConsole;
import org.hawk.orientdb.indexes.IndexBasedEdgeStore;
import org.junit.After;
import org.junit.Test;

public class IndexBasedEdgeStoreTest {

	protected OrientDatabase db;

	public void setup(String testCase) throws Exception {
		db = new OrientDatabase();
		db.run("memory:index_test_" + testCase, null, new DefaultConsole());
	}

	@After
	public void teardown() throws Exception {
		db.delete();
	}

	@Test
	public void fourNodes() throws Exception {
		setup("es_fourNodes");

		OrientNode left, middle, right1, right2;
		try (IGraphTransaction tx = db.beginTransaction()) {
			left = db.createNode(null, "eobject");
			middle = db.createNode(null, "eobject");
			right1 = db.createNode(null, "eobject");
			right2 = db.createNode(null, "eobject");
			db.createRelationship(left, middle, "x");
			db.createRelationship(left, middle, "y");
			db.createRelationship(middle, right1, "y");
			db.createRelationship(middle, right2, "y");
			tx.success();
		}

		IndexBasedEdgeStore edgeStore = new IndexBasedEdgeStore(db);
		assertEquals(2, edgeStore.getEdges(left).size());
		assertEquals(4, edgeStore.getEdges(middle).size());
		assertEquals(1, edgeStore.getEdges(right1).size());
		assertEquals(1, edgeStore.getEdges(right2).size());
		assertEquals(1, edgeStore.getEdges(middle, "x").size());
		assertEquals(3, edgeStore.getEdges(middle, "y").size());

		assertEquals(0, edgeStore.getIncoming(left).size());
		assertEquals(2, edgeStore.getIncoming(middle).size());
		assertEquals(1, edgeStore.getIncoming(right1).size());
		assertEquals(1, edgeStore.getIncoming(right2).size());
		assertEquals(0, edgeStore.getIncoming(left, "x").size());
		assertEquals(1, edgeStore.getIncoming(middle, "x").size());
		assertEquals(0, edgeStore.getIncoming(right1, "x").size());
		assertEquals(0, edgeStore.getIncoming(left, "y").size());
		assertEquals(1, edgeStore.getIncoming(middle, "y").size());
		assertEquals(1, edgeStore.getIncoming(right1, "y").size());

		assertEquals(2, edgeStore.getOutgoing(left).size());
		assertEquals(2, edgeStore.getOutgoing(middle).size());
		assertEquals(0, edgeStore.getOutgoing(right1).size());
		assertEquals(1, edgeStore.getOutgoing(left, "x").size());
		assertEquals(0, edgeStore.getOutgoing(middle, "x").size());
		assertEquals(0, edgeStore.getOutgoing(right1, "x").size());
		assertEquals(1, edgeStore.getOutgoing(left, "y").size());
		assertEquals(2, edgeStore.getOutgoing(middle, "y").size());
		assertEquals(0, edgeStore.getOutgoing(right1, "y").size());
	}

	@Test
	public void twoNodesAddRemove() throws Exception {
		setup("es_twoNodes");

		OrientNode left, right;
		OrientEdge edge;
		try (IGraphTransaction tx = db.beginTransaction()) {
			left = db.createNode(null, "eobject");
			right = db.createNode(null, "eobject");
			edge = db.createRelationship(left, right, "x");
			tx.success();
		}

		IndexBasedEdgeStore edgeStore = new IndexBasedEdgeStore(db);
		assertEquals(1, edgeStore.getEdges(left).size());
		assertEquals(1, edgeStore.getEdges(right).size());

		try (IGraphTransaction tx = db.beginTransaction()) {
			edge.delete();
			tx.success();
		}
		assertEquals(0, edgeStore.getEdges(left).size());
		assertEquals(0, edgeStore.getEdges(right).size());
	}
	
}
