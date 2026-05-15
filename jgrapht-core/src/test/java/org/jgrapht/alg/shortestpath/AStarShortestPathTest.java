/*
 * (C) Copyright 2015-2026, by Joris Kinable, Jon Robison, Thomas Breitbart and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * See the CONTRIBUTORS.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the
 * GNU Lesser General Public License v2.1 or later
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR LGPL-2.1-or-later
 */
package org.jgrapht.alg.shortestpath;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for AStarShortestPath implementation
 *
 * @author Joris Kinable
 */
public class AStarShortestPathTest extends BaseHeuristicSearchTest
{

    /**
     * Test on a graph with a path from the source node to the target node.
     */
    @Test
    public void testLabyrinth1()
    {
        this.readLabyrinth(labyrinth1);

        AStarShortestPath<Node, DefaultWeightedEdge> aStarShortestPath =
            new AStarShortestPath<>(graph, new ManhattanDistance());
        GraphPath<Node, DefaultWeightedEdge> path =
            aStarShortestPath.getPath(sourceNode, targetNode);
        assertNotNull(path);
        assertEquals((int) path.getWeight(), 47);
        assertEquals(path.getEdgeList().size(), 47);
        assertEquals(path.getLength() + 1, 48);

        AStarShortestPath<Node, DefaultWeightedEdge> aStarShortestPath2 =
            new AStarShortestPath<>(graph, new EuclideanDistance());
        GraphPath<Node, DefaultWeightedEdge> path2 =
            aStarShortestPath2.getPath(sourceNode, targetNode);
        assertNotNull(path2);
        assertEquals((int) path2.getWeight(), 47);
        assertEquals(path2.getEdgeList().size(), 47);
    }

    /**
     * Test on a graph where there is no path from the source node to the target node.
     */
    @Test
    public void testLabyrinth2()
    {
        this.readLabyrinth(labyrinth2);
        AStarShortestPath<Node, DefaultWeightedEdge> aStarShortestPath =
            new AStarShortestPath<>(graph, new ManhattanDistance());
        GraphPath<Node, DefaultWeightedEdge> path =
            aStarShortestPath.getPath(sourceNode, targetNode);
        assertNull(path);
    }

    /**
     * This test verifies whether multigraphs are processed correctly. In a multigraph, there are
     * multiple edges between the same vertex pair. Each of these edges can have a different cost.
     * Here we create a simple multigraph A-B-C with multiple edges between (A,B) and (B,C) and
     * query the shortest path, which is simply the cheapest edge between (A,B) plus the cheapest
     * edge between (B,C). The admissible heuristic in this test is not important.
     */
    @Test
    public void testMultiGraph()
    {
        Graph<Node, DefaultWeightedEdge> multigraph = getMultigraph();
        AStarShortestPath<Node, DefaultWeightedEdge> aStarShortestPath =
            new AStarShortestPath<>(multigraph, new ManhattanDistance());
        GraphPath<Node, DefaultWeightedEdge> path = aStarShortestPath.getPath(n1, n3);
        assertNotNull(path);
        assertEquals((int) path.getWeight(), 6);
        assertEquals(path.getEdgeList().size(), 2);
    }

    @Test
    public void testInconsistentHeuristic()
    {
        Graph<Integer, DefaultWeightedEdge> g = getInconsistentHeuristicTestGraph();
        AStarAdmissibleHeuristic<Integer> h = getInconsistentHeuristic();

        AStarShortestPath<Integer, DefaultWeightedEdge> alg = new AStarShortestPath<>(g, h);

        // shortest path from 3 to 2 is 3->0->1->2 with weight 0.9641320715228003
        assertEquals(0.9641320715228003, alg.getPath(3, 2).getWeight(), 1e-9);
    }

    /**
     * Regression: with an inconsistent heuristic, A* can reopen a closed node and discover an
     * even better g-score for it later via a third path. Prior to the fix, the closed-node-reopen
     * branch inserted a fresh heap handle but never wrote it back to {@code vertexToHeapNodeMap},
     * so the subsequent {@code decreaseKey} acted on the stale (already-deleted) handle and the
     * underlying heap threw {@code IllegalArgumentException: Invalid handle!}. This test
     * constructs the smallest graph + heuristic that triggers exactly that sequence.
     */
    @Test
    public void testReopenedNodeCanBeDecreasedAgain()
    {
        // Vertices: S, A, B, C, T.
        // Edges and weights designed so A is visited and closed early with a non-optimal g, then
        // reopened via a shorter path through B, then decreased again via an even shorter path
        // through C. The reopen sequence is what stresses the heap-handle bookkeeping.
        Graph<String, DefaultWeightedEdge> g = new DefaultDirectedWeightedGraph<>(
            DefaultWeightedEdge.class);
        for (String v : new String[] { "S", "A", "B", "C", "T" }) {
            g.addVertex(v);
        }
        DefaultWeightedEdge sa = g.addEdge("S", "A");
        g.setEdgeWeight(sa, 10.0); // S -> A direct, expensive
        DefaultWeightedEdge sb = g.addEdge("S", "B");
        g.setEdgeWeight(sb, 1.0);
        DefaultWeightedEdge ba = g.addEdge("B", "A");
        g.setEdgeWeight(ba, 1.0); // B -> A: reopens A with g=2 (better than 10)
        DefaultWeightedEdge sc = g.addEdge("S", "C");
        g.setEdgeWeight(sc, 0.5);
        DefaultWeightedEdge ca = g.addEdge("C", "A");
        g.setEdgeWeight(ca, 0.1); // C -> A: better still with g=0.6 — triggers second update
        DefaultWeightedEdge at = g.addEdge("A", "T");
        g.setEdgeWeight(at, 1.0);

        // Inconsistent heuristic: heavily favours A first (so it is expanded and closed early
        // with g=10), then on later reopens the bug bites.
        AStarAdmissibleHeuristic<String> badHeuristic = (s, t) -> {
            switch (s) {
            case "S":
                return 0.0;
            case "A":
                return 1.0; // admissible: true cost A->T is 1.0
            case "B":
                return 0.0; // would need to be >= 1.0 for consistency given B->A weight 1
            case "C":
                return 0.0; // would need to be >= 0.9 for consistency given C->A weight 0.1
            case "T":
                return 0.0;
            default:
                return 0.0;
            }
        };

        AStarShortestPath<String, DefaultWeightedEdge> alg =
            new AStarShortestPath<>(g, badHeuristic);
        GraphPath<String, DefaultWeightedEdge> p = alg.getPath("S", "T");
        assertNotNull(p);
        // Optimal path: S -> C -> A -> T with weight 0.5 + 0.1 + 1.0 = 1.6
        assertEquals(1.6, p.getWeight(), 1e-9);
    }
}
