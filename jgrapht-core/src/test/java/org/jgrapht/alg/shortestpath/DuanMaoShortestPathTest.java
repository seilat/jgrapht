/*
 * (C) Copyright 2026-2026, by Contributors.
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
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.*;
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;
import org.jgrapht.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Correctness tests for {@link DuanMaoShortestPath}. Distances are validated against
 * {@link DijkstraShortestPath} (the ground truth) on a variety of hand-built and randomly generated
 * graphs.
 */
public class DuanMaoShortestPathTest
{
    private static final double EPS = 1e-9;

    @Test
    public void testSingleVertex()
    {
        Graph<String, DefaultWeightedEdge> g =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex("a");
        SingleSourcePaths<String, DefaultWeightedEdge> paths =
            new DuanMaoShortestPath<>(g).getPaths("a");
        assertEquals(0.0, paths.getWeight("a"), EPS);
    }

    @Test
    public void testSimpleDirected()
    {
        Graph<String, DefaultWeightedEdge> g =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graphs.addAllVertices(g, Arrays.asList("s", "a", "b", "c"));
        g.setEdgeWeight(g.addEdge("s", "a"), 1.0);
        g.setEdgeWeight(g.addEdge("a", "b"), 2.0);
        g.setEdgeWeight(g.addEdge("s", "b"), 4.0);
        g.setEdgeWeight(g.addEdge("b", "c"), 1.0);

        SingleSourcePaths<String, DefaultWeightedEdge> paths =
            new DuanMaoShortestPath<>(g).getPaths("s");
        assertEquals(0.0, paths.getWeight("s"), EPS);
        assertEquals(1.0, paths.getWeight("a"), EPS);
        assertEquals(3.0, paths.getWeight("b"), EPS);
        assertEquals(4.0, paths.getWeight("c"), EPS);

        GraphPath<String, DefaultWeightedEdge> p = paths.getPath("c");
        assertEquals(Arrays.asList("s", "a", "b", "c"), p.getVertexList());
    }

    @Test
    public void testUnreachable()
    {
        Graph<String, DefaultWeightedEdge> g =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graphs.addAllVertices(g, Arrays.asList("s", "a", "x"));
        g.setEdgeWeight(g.addEdge("s", "a"), 1.0);
        SingleSourcePaths<String, DefaultWeightedEdge> paths =
            new DuanMaoShortestPath<>(g).getPaths("s");
        assertEquals(1.0, paths.getWeight("a"), EPS);
        assertEquals(Double.POSITIVE_INFINITY, paths.getWeight("x"), EPS);
        assertNull(paths.getPath("x"));
    }

    @Test
    public void testZeroWeightEdges()
    {
        Graph<String, DefaultWeightedEdge> g =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graphs.addAllVertices(g, Arrays.asList("s", "a", "b", "c"));
        g.setEdgeWeight(g.addEdge("s", "a"), 0.0);
        g.setEdgeWeight(g.addEdge("a", "b"), 0.0);
        g.setEdgeWeight(g.addEdge("b", "c"), 5.0);
        g.setEdgeWeight(g.addEdge("s", "c"), 6.0);
        SingleSourcePaths<String, DefaultWeightedEdge> paths =
            new DuanMaoShortestPath<>(g).getPaths("s");
        assertEquals(0.0, paths.getWeight("a"), EPS);
        assertEquals(0.0, paths.getWeight("b"), EPS);
        assertEquals(5.0, paths.getWeight("c"), EPS);
    }

    @Test
    public void testNegativeEdgeRejected()
    {
        Graph<String, DefaultWeightedEdge> g =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graphs.addAllVertices(g, Arrays.asList("s", "a"));
        g.setEdgeWeight(g.addEdge("s", "a"), -1.0);
        assertThrows(IllegalArgumentException.class, () -> new DuanMaoShortestPath<>(g));
    }

    @Test
    public void testRandomDirectedAgainstDijkstra()
    {
        long seed = 42L;
        for (int trial = 0; trial < 40; trial++) {
            int vertices = 5 + (trial * 7) % 200;
            double p = 0.02 + (trial % 5) * 0.03;
            Graph<Integer, DefaultWeightedEdge> g = randomDirected(vertices, p, seed + trial);
            assertSameDistances(g, 0);
        }
    }

    @Test
    public void testRandomDenseAgainstDijkstra()
    {
        for (int trial = 0; trial < 10; trial++) {
            Graph<Integer, DefaultWeightedEdge> g = randomDirected(60, 0.3, 1000L + trial);
            assertSameDistances(g, 0);
        }
    }

    @Test
    public void testRandomSparseLargeAgainstDijkstra()
    {
        for (int trial = 0; trial < 5; trial++) {
            Graph<Integer, DefaultWeightedEdge> g = randomDirected(1500, 0.004, 7000L + trial);
            assertSameDistances(g, 0);
        }
    }

    @Test
    public void testTieHeavyAgainstDijkstra()
    {
        // small integer weights in {0,1,2} produce many equal-length paths, exercising the
        // (length, hops, vertex) tie-breaking that realizes the paper's Assumption 2.1
        for (int nv = 3; nv <= 18; nv++) {
            for (long seed = 0; seed < 50; seed++) {
                assertSameDistances(tieHeavyDirected(nv, seed), 0);
            }
        }
    }

    private DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge> tieHeavyDirected(
        int nv, long seed)
    {
        Random rng = new Random(seed * 2654435761L + nv);
        DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge> g =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < nv; i++) {
            g.addVertex(i);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 1; i < nv; i++) {
            order.add(i);
        }
        Collections.shuffle(order, rng);
        int prev = 0;
        for (int x : order) {
            DefaultWeightedEdge e = g.addEdge(prev, x);
            if (e != null) {
                g.setEdgeWeight(e, rng.nextInt(3));
            }
            prev = x;
        }
        int extra = rng.nextInt(nv * 3 + 1);
        for (int i = 0; i < extra; i++) {
            int a = rng.nextInt(nv), bb = rng.nextInt(nv);
            if (a != bb && !g.containsEdge(a, bb)) {
                DefaultWeightedEdge e = g.addEdge(a, bb);
                if (e != null) {
                    g.setEdgeWeight(e, rng.nextInt(3));
                }
            }
        }
        return g;
    }

    private void assertSameDistances(Graph<Integer, DefaultWeightedEdge> g, Integer source)
    {
        SingleSourcePaths<Integer, DefaultWeightedEdge> expected =
            new DijkstraShortestPath<>(g).getPaths(source);
        SingleSourcePaths<Integer, DefaultWeightedEdge> actual =
            new DuanMaoShortestPath<>(g).getPaths(source);
        for (Integer v : g.vertexSet()) {
            assertEquals(
                expected.getWeight(v), actual.getWeight(v), EPS,
                "distance mismatch for vertex " + v + " in graph with " + g.vertexSet().size()
                    + " vertices");
        }
    }

    private Graph<Integer, DefaultWeightedEdge> randomDirected(int vertices, double p, long seed)
    {
        Random rng = new Random(seed);
        DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge> g =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        g.setVertexSupplier(SupplierUtil.createIntegerSupplier());
        new GnpRandomGraphGenerator<Integer, DefaultWeightedEdge>(vertices, p, seed, false)
            .generateGraph(g);
        // ensure the source can reach a reasonable part of the graph and weights are set
        for (DefaultWeightedEdge e : g.edgeSet()) {
            g.setEdgeWeight(e, rng.nextDouble() * 10.0);
        }
        return g;
    }
}
