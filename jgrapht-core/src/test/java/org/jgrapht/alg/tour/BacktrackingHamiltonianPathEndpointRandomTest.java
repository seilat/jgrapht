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
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertHamiltonianPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-validates the endpoint-constrained search variants of
 * {@link BacktrackingHamiltonianPath} ({@code getPathFrom}, {@code getPathTo},
 * {@code getPathBetween}) against an endpoint-aware permutation brute-force oracle on small random
 * graphs. For every random graph it checks every source-only, target-only and source/target pair
 * constraint, guarding the endpoint pruning against false negatives and verifying that any path
 * the solver reports both satisfies the requested endpoints and is structurally valid. The oracle
 * is exponential by construction and limited to {@code n <= 6} so the suite stays fast.
 */
public class BacktrackingHamiltonianPathEndpointRandomTest
{

    private static final long SEED = 0xBADC0DE0FF1CE001L; // deterministic seed
    private static final int GRAPHS_PER_CONFIG = 20;

    @Test
    public void undirectedRandomGraphsMatchOracle()
    {
        Random random = new Random(SEED);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.3, 0.5, 0.8 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runAllEndpointConfigs(randomUndirected(n, p, random));
                }
            }
        }
    }

    @Test
    public void directedRandomGraphsMatchOracle()
    {
        Random random = new Random(SEED ^ 0x5A5A5A5AL);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.3, 0.5, 0.8 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runAllEndpointConfigs(randomDirected(n, p, random));
                }
            }
        }
    }

    private void runAllEndpointConfigs(Graph<Integer, DefaultEdge> graph)
    {
        List<Integer> vertices = new ArrayList<>(graph.vertexSet());
        for (Integer s : vertices) {
            check(graph, s, null, new BacktrackingHamiltonianPath<Integer, DefaultEdge>()
                .getPathFrom(graph, s));
            check(graph, null, s, new BacktrackingHamiltonianPath<Integer, DefaultEdge>()
                .getPathTo(graph, s));
        }
        for (Integer s : vertices) {
            for (Integer t : vertices) {
                if (!s.equals(t)) {
                    check(graph, s, t, new BacktrackingHamiltonianPath<Integer, DefaultEdge>()
                        .getPathBetween(graph, s, t));
                }
            }
        }
    }

    private void check(
        Graph<Integer, DefaultEdge> graph, Integer source, Integer target,
        HamiltonianPathSearchResult<Integer, DefaultEdge> result)
    {
        boolean oracle = bruteForceExists(graph, source, target);
        boolean found = result.getPath().isPresent();
        assertEquals(
            oracle, found,
            () -> "disagreement on graph " + graph + " source=" + source + " target=" + target);
        if (found) {
            assertHamiltonianPath(graph, result);
            GraphPath<Integer, DefaultEdge> path = result.getPath().orElseThrow();
            if (source != null) {
                assertEquals(source, path.getStartVertex(), "start vertex mismatch");
            }
            if (target != null) {
                assertEquals(target, path.getEndVertex(), "end vertex mismatch");
            }
        }
    }

    private Graph<Integer, DefaultEdge> randomUndirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            graph.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (random.nextDouble() < p) {
                    graph.addEdge(i, j);
                }
            }
        }
        return graph;
    }

    private Graph<Integer, DefaultEdge> randomDirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            graph.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && random.nextDouble() < p) {
                    graph.addEdge(i, j);
                }
            }
        }
        return graph;
    }

    /**
     * Brute-force oracle: returns true iff some permutation of the vertices is a valid path in
     * {@code graph} whose first vertex equals {@code source} (when non-null) and whose last vertex
     * equals {@code target} (when non-null).
     */
    private boolean bruteForceExists(
        Graph<Integer, DefaultEdge> graph, Integer source, Integer target)
    {
        List<Integer> vertices = new ArrayList<>(graph.vertexSet());
        if (vertices.size() == 1) {
            Integer only = vertices.get(0);
            return (source == null || source.equals(only))
                && (target == null || target.equals(only));
        }
        if (source != null && source.equals(target)) {
            return false;
        }
        return permute(graph, vertices, 0, source, target);
    }

    private boolean permute(
        Graph<Integer, DefaultEdge> graph, List<Integer> v, int from, Integer source,
        Integer target)
    {
        if (from == v.size()) {
            if (source != null && !v.get(0).equals(source)) {
                return false;
            }
            if (target != null && !v.get(v.size() - 1).equals(target)) {
                return false;
            }
            for (int i = 1; i < v.size(); i++) {
                if (!graph.containsEdge(v.get(i - 1), v.get(i))) {
                    return false;
                }
            }
            return true;
        }
        for (int i = from; i < v.size(); i++) {
            Collections.swap(v, from, i);
            if (permute(graph, v, from + 1, source, target)) {
                return true;
            }
            Collections.swap(v, from, i);
        }
        return false;
    }
}
