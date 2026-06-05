/*
 * (C) Copyright 2026-2026, by seilat and Contributors.
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
package org.jgrapht.alg.tour;

import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link HeldKarpLongestPath}: the maximum-weight (non-spanning) simple path, with
 * hand-computed deterministic cases and a depth-first brute-force oracle that enumerates every
 * simple path of random weighted graphs.
 */
public class HeldKarpLongestPathTest
{

    private static final double EPS = 1e-9;
    private static final long SEED = 0x13571357ACE0FF11L;
    private static final int GRAPHS_PER_CONFIG = 12;

    private HeldKarpLongestPath<Integer, DefaultWeightedEdge> solver()
    {
        return new HeldKarpLongestPath<>();
    }

    /**
     * Asserts {@code path} is a structurally valid simple path on {@code graph} (distinct vertices,
     * consecutive edges present, reported weight equal to the edge-weight sum) with the expected
     * total weight and, when given, endpoints.
     */
    private void assertSimplePath(
        Graph<Integer, DefaultWeightedEdge> graph, GraphPath<Integer, DefaultWeightedEdge> path,
        double expectedWeight, Integer expectedStart, Integer expectedEnd)
    {
        assertNotNull(path);
        List<Integer> vs = path.getVertexList();
        assertEquals(new HashSet<>(vs).size(), vs.size(), "vertices must be distinct");
        assertEquals(Math.max(0, vs.size() - 1), path.getEdgeList().size(), "edge count");
        double w = 0d;
        for (int i = 1; i < vs.size(); i++) {
            DefaultWeightedEdge e = graph.getEdge(vs.get(i - 1), vs.get(i));
            assertNotNull(e, "consecutive vertices must be adjacent");
            w += graph.getEdgeWeight(e);
        }
        assertEquals(w, path.getWeight(), EPS, "reported weight matches edges");
        assertEquals(expectedWeight, path.getWeight(), EPS);
        if (expectedStart != null) {
            assertEquals(expectedStart, path.getStartVertex());
        }
        if (expectedEnd != null) {
            assertEquals(expectedEnd, path.getEndVertex());
        }
    }

    @Test
    public void longestPathSkipsCostlyVertex()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        // high-weight triangle 0-1-2; vertex 3 only reachable through a very costly edge
        g.setEdgeWeight(g.addEdge(0, 1), 10);
        g.setEdgeWeight(g.addEdge(1, 2), 10);
        g.setEdgeWeight(g.addEdge(0, 2), 10);
        g.setEdgeWeight(g.addEdge(3, 0), -100);
        GraphPath<Integer, DefaultWeightedEdge> path = solver().getPath(g);
        assertSimplePath(g, path, 20.0, null, null); // two triangle edges, vertex 3 excluded
        assertFalse(path.getVertexList().contains(3), "must skip the costly vertex");
    }

    @Test
    public void unweightedLongestPathSpansWhenHamiltonianExists()
    {
        // default-weight (1.0) path graph 0-1-2-3-4: longest by length is the Hamiltonian path
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < 5; i++) {
            g.addVertex(i);
        }
        for (int i = 1; i < 5; i++) {
            g.addEdge(i - 1, i);
        }
        GraphPath<Integer, DefaultEdge> path = new HeldKarpLongestPath<Integer, DefaultEdge>()
            .getPath(g);
        assertNotNull(path);
        assertEquals(5, path.getVertexList().size());
        assertEquals(4.0, path.getWeight(), EPS);
    }

    @Test
    public void allNegativeWeightsYieldSingleVertex()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), -1);
        g.setEdgeWeight(g.addEdge(1, 2), -2);
        g.setEdgeWeight(g.addEdge(0, 2), -3);
        GraphPath<Integer, DefaultWeightedEdge> path = solver().getPath(g);
        assertNotNull(path);
        assertEquals(1, path.getVertexList().size());
        assertEquals(0.0, path.getWeight(), EPS);
    }

    @Test
    public void betweenReturnsNullWhenDisconnected()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), 5);
        g.setEdgeWeight(g.addEdge(2, 3), 5);
        assertNull(solver().getPathBetween(g, 0, 3));
    }

    @Test
    public void singleVertex()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(9);
        assertSimplePath(g, solver().getPath(g), 0.0, 9, 9);
    }

    @Test
    public void invalidArgumentsThrow()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(0);
        g.addVertex(1);
        g.setEdgeWeight(g.addEdge(0, 1), 1);
        assertThrows(NullPointerException.class, () -> solver().getPath(null));
        assertThrows(NullPointerException.class, () -> solver().getPathFrom(g, null));
        assertThrows(IllegalArgumentException.class, () -> solver().getPathFrom(g, 99));
        assertThrows(IllegalArgumentException.class, () -> new HeldKarpLongestPath<>(0));
        Graph<Integer, DefaultWeightedEdge> empty =
            new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        assertThrows(IllegalArgumentException.class, () -> solver().getPath(empty));
    }

    @Test
    public void undirectedRandomWeightedGraphsMatchOracle()
    {
        runRandom(new Random(SEED), false);
    }

    @Test
    public void directedRandomWeightedGraphsMatchOracle()
    {
        runRandom(new Random(SEED ^ 0x5A5A5A5AL), true);
    }

    private void runRandom(Random random, boolean directed)
    {
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.5, 0.8 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    Graph<Integer, DefaultWeightedEdge> g =
                        directed ? randomDirected(n, p, random) : randomUndirected(n, p, random);
                    List<Integer> vs = new ArrayList<>(g.vertexSet());

                    GraphPath<Integer, DefaultWeightedEdge> free = solver().getPath(g);
                    assertSimplePath(g, free, bruteMax(g, null, null, directed), null, null);

                    for (Integer s : vs) {
                        assertSimplePath(
                            g, solver().getPathFrom(g, s), bruteMax(g, s, null, directed), s, null);
                    }
                    for (Integer s : vs) {
                        for (Integer u : vs) {
                            if (!s.equals(u)) {
                                double oracle = bruteMax(g, s, u, directed);
                                GraphPath<Integer, DefaultWeightedEdge> between =
                                    solver().getPathBetween(g, s, u);
                                if (oracle == Double.NEGATIVE_INFINITY) {
                                    assertNull(between, () -> "expected no s-t path on " + g);
                                } else {
                                    assertSimplePath(g, between, oracle, s, u);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Maximum-weight simple path over a DFS enumeration of every simple path, optionally fixing the
     * first / last vertex. Returns {@link Double#NEGATIVE_INFINITY} when no path satisfies the
     * constraints.
     */
    private double bruteMax(
        Graph<Integer, DefaultWeightedEdge> g, Integer first, Integer last, boolean directed)
    {
        double best = Double.NEGATIVE_INFINITY;
        for (Integer s : g.vertexSet()) {
            if (first != null && !first.equals(s)) {
                continue;
            }
            Set<Integer> visited = new HashSet<>();
            visited.add(s);
            best = Math.max(best, dfs(g, s, visited, 0d, last, directed));
        }
        return best;
    }

    private double dfs(
        Graph<Integer, DefaultWeightedEdge> g, Integer cur, Set<Integer> visited, double w,
        Integer last, boolean directed)
    {
        double local = (last == null || last.equals(cur)) ? w : Double.NEGATIVE_INFINITY;
        List<Integer> neighbours =
            directed ? Graphs.successorListOf(g, cur) : Graphs.neighborListOf(g, cur);
        for (Integer t : neighbours) {
            if (visited.add(t)) {
                double wt = g.getEdgeWeight(g.getEdge(cur, t));
                local = Math.max(local, dfs(g, t, visited, w + wt, last, directed));
                visited.remove(t);
            }
        }
        return local;
    }

    private Graph<Integer, DefaultWeightedEdge> randomUndirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (random.nextDouble() < p) {
                    g.setEdgeWeight(g.addEdge(i, j), random.nextInt(11) - 3);
                }
            }
        }
        return g;
    }

    private Graph<Integer, DefaultWeightedEdge> randomDirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && random.nextDouble() < p) {
                    g.setEdgeWeight(g.addEdge(i, j), random.nextInt(11) - 3);
                }
            }
        }
        return g;
    }
}
