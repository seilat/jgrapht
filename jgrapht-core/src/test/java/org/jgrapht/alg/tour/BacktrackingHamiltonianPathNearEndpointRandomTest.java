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
import java.util.function.*;

import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertHamiltonianPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-validates the {@code BacktrackingHamiltonianPath.getPathWithBest*} family against a
 * brute-force oracle on small random graphs. Per-vertex costs are assigned from random permutations
 * so that
 * every candidate endpoint pair has a distinct total cost; the unique minimum-cost feasible pair
 * is therefore unambiguous, letting the test assert the method returns a valid Hamiltonian path
 * with exactly those endpoints (or proves absence when no endpoint pair is feasible). All three
 * modes are exercised: both endpoints constrained, free start, and free end. Limited to
 * {@code n <= 6} so the suite stays fast.
 */
public class BacktrackingHamiltonianPathNearEndpointRandomTest
{

    private static final long SEED = 0x1357924680ABCDEFL; // deterministic seed
    private static final int GRAPHS_PER_CONFIG = 15;
    // Approach ranks are scaled by HIGH and departure ranks left unscaled (both ranks are in
    // 0..n-1 with n <= 6 < HIGH), so every pair cost approachRank[a]*HIGH + departureRank[b] is a
    // distinct mixed-radix value. That makes the minimum-cost feasible pair unique and the
    // expected endpoints unambiguous.
    private static final double HIGH = 100.0;

    @Test
    public void undirectedRandomGraphsMatchOracle()
    {
        Random random = new Random(SEED);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.4, 0.7 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runOne(randomUndirected(n, p, random), random);
                }
            }
        }
    }

    @Test
    public void directedRandomGraphsMatchOracle()
    {
        Random random = new Random(SEED ^ 0x5A5A5A5AL);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.4, 0.7 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runOne(randomDirected(n, p, random), random);
                }
            }
        }
    }

    private void runOne(Graph<Integer, DefaultEdge> graph, Random random)
    {
        int n = graph.vertexSet().size();
        int[] approachRank = randomPermutation(n, random);
        int[] departureRank = randomPermutation(n, random);
        ToDoubleFunction<Integer> approach = v -> approachRank[v] * HIGH;
        ToDoubleFunction<Integer> departure = v -> departureRank[v];

        checkBoth(graph, approachRank, departureRank, approach, departure);
        checkFreeEnd(graph, approachRank, approach);
        checkFreeStart(graph, departureRank, departure);
    }

    private void checkBoth(
        Graph<Integer, DefaultEdge> graph, int[] approachRank, int[] departureRank,
        ToDoubleFunction<Integer> approach, ToDoubleFunction<Integer> departure)
    {
        Integer bestA = null;
        Integer bestB = null;
        double best = Double.POSITIVE_INFINITY;
        for (Integer a : graph.vertexSet()) {
            for (Integer b : graph.vertexSet()) {
                if (!a.equals(b) && oracleExists(graph, a, b)) {
                    double cost = approachRank[a] * HIGH + departureRank[b];
                    if (cost < best) {
                        best = cost;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
        }
        assertEndpoints(graph, "both", approach, departure, bestA, bestB);
    }

    private void checkFreeEnd(
        Graph<Integer, DefaultEdge> graph, int[] approachRank, ToDoubleFunction<Integer> approach)
    {
        Integer bestA = null;
        double best = Double.POSITIVE_INFINITY;
        for (Integer a : graph.vertexSet()) {
            if (oracleExists(graph, a, null)) {
                double cost = approachRank[a] * HIGH;
                if (cost < best) {
                    best = cost;
                    bestA = a;
                }
            }
        }
        assertEndpoints(graph, "freeEnd", approach, null, bestA, null);
    }

    private void checkFreeStart(
        Graph<Integer, DefaultEdge> graph, int[] departureRank, ToDoubleFunction<Integer> departure)
    {
        Integer bestB = null;
        double best = Double.POSITIVE_INFINITY;
        for (Integer b : graph.vertexSet()) {
            if (oracleExists(graph, null, b)) {
                double cost = departureRank[b] * HIGH;
                if (cost < best) {
                    best = cost;
                    bestB = b;
                }
            }
        }
        assertEndpoints(graph, "freeStart", null, departure, null, bestB);
    }

    /**
     * Runs the method for one mode and asserts agreement with the oracle's expected outcome:
     * {@code expectedStart}/{@code expectedEnd} are {@code null} either because that endpoint is
     * free or, when both are {@code null} and no feasible pair exists, because the oracle expects
     * proven absence.
     */
    private void assertEndpoints(
        Graph<Integer, DefaultEdge> graph, String mode, ToDoubleFunction<Integer> approach,
        ToDoubleFunction<Integer> departure, Integer expectedStart, Integer expectedEnd)
    {
        BacktrackingHamiltonianPath<Integer, DefaultEdge> solver = new BacktrackingHamiltonianPath<>();
        HamiltonianPathSearchResult<Integer, DefaultEdge> result;
        if (approach != null && departure != null) {
            result = solver.getPathWithBestEndpoints(graph, approach, departure);
        } else if (approach != null) {
            result = solver.getPathWithBestStart(graph, approach);
        } else {
            result = solver.getPathWithBestEnd(graph, departure);
        }

        boolean expectFound = expectedStart != null || expectedEnd != null;
        assertEquals(
            expectFound, result.getPath().isPresent(),
            () -> "existence disagreement (" + mode + ") on graph " + graph);
        if (expectFound) {
            assertHamiltonianPath(graph, result);
            GraphPath<Integer, DefaultEdge> path = result.getPath().orElseThrow();
            if (expectedStart != null) {
                assertEquals(
                    expectedStart, path.getStartVertex(),
                    () -> "start mismatch (" + mode + ") on graph " + graph);
            }
            if (expectedEnd != null) {
                assertEquals(
                    expectedEnd, path.getEndVertex(),
                    () -> "end mismatch (" + mode + ") on graph " + graph);
            }
        }
    }

    private int[] randomPermutation(int n, Random random)
    {
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) {
            perm[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }
        return perm;
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
     * Brute-force oracle: true iff some permutation of the vertices is a valid path whose first
     * vertex equals {@code source} (when non-null) and last vertex equals {@code target} (when
     * non-null).
     */
    private boolean oracleExists(
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

    @Test
    public void permutationHelperProducesDistinctRanks()
    {
        // guards the test's own invariant: ranks are a permutation, so pair costs are distinct
        Random random = new Random(SEED);
        int[] perm = randomPermutation(6, random);
        Set<Integer> seen = new HashSet<>();
        for (int r : perm) {
            assertTrue(seen.add(r), "ranks must be distinct");
        }
        assertEquals(6, seen.size());
    }
}
