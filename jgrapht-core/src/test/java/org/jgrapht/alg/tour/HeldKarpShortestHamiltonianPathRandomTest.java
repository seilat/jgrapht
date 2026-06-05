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
import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertProvenAbsent;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-validates {@link HeldKarpShortestHamiltonianPath} against a permutation brute-force oracle
 * that computes the true minimum-weight Hamiltonian path on small random weighted graphs (with
 * negative weights included). All modes are checked: free endpoints, fixed start, fixed end, fixed
 * pair, and the navigation objective (approach + tour + departure). For each the DP's optimal
 * weight (or total journey cost) must equal the oracle's, the reported path must be structurally
 * valid, and pinned endpoints must match. Limited to {@code n <= 6} so the oracle stays fast.
 */
public class HeldKarpShortestHamiltonianPathRandomTest
{

    private static final long SEED = 0x0FEEDBEEFCAFE123L; // deterministic seed
    private static final int GRAPHS_PER_CONFIG = 12;
    private static final double EPS = 1e-9;

    @Test
    public void undirectedRandomWeightedGraphsMatchOracle()
    {
        Random random = new Random(SEED);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.5, 0.8 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runOne(randomUndirected(n, p, random), random);
                }
            }
        }
    }

    @Test
    public void directedRandomWeightedGraphsMatchOracle()
    {
        Random random = new Random(SEED ^ 0x5A5A5A5AL);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.5, 0.8 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runOne(randomDirected(n, p, random), random);
                }
            }
        }
    }

    private void runOne(Graph<Integer, DefaultWeightedEdge> graph, Random random)
    {
        List<Integer> vertices = new ArrayList<>(graph.vertexSet());
        HeldKarpShortestHamiltonianPath<Integer, DefaultWeightedEdge> solver =
            new HeldKarpShortestHamiltonianPath<>();

        // free endpoints
        checkWeight(graph, solver.getPath(graph), bruteForce(graph, null, null, null, null), null,
            null);

        // fixed start / fixed end / fixed pair
        for (Integer s : vertices) {
            checkWeight(
                graph, solver.getPathFrom(graph, s), bruteForce(graph, s, null, null, null), s,
                null);
            checkWeight(
                graph, solver.getPathTo(graph, s), bruteForce(graph, null, s, null, null), null, s);
        }
        for (Integer s : vertices) {
            for (Integer t : vertices) {
                if (!s.equals(t)) {
                    checkWeight(
                        graph, solver.getPathBetween(graph, s, t),
                        bruteForce(graph, s, t, null, null), s, t);
                }
            }
        }

        // navigation: random per-vertex approach / departure costs
        int n = vertices.size();
        double[] approach = new double[n];
        double[] departure = new double[n];
        for (int i = 0; i < n; i++) {
            approach[i] = random.nextInt(21) - 5;
            departure[i] = random.nextInt(21) - 5;
        }
        ToDoubleFunction<Integer> ap = v -> approach[v];
        ToDoubleFunction<Integer> dep = v -> departure[v];
        checkNavigation(
            graph, solver.getShortestPathWithBestEndpoints(graph, ap, dep),
            bruteForce(graph, null, null, ap, dep), approach, departure);
    }

    private void checkWeight(
        Graph<Integer, DefaultWeightedEdge> graph,
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> result, double oracle,
        Integer expectedStart, Integer expectedEnd)
    {
        if (oracle == Double.POSITIVE_INFINITY) {
            assertProvenAbsent(result);
            return;
        }
        assertHamiltonianPath(graph, result);
        GraphPath<Integer, DefaultWeightedEdge> path = result.getPath().orElseThrow();
        assertEquals(oracle, path.getWeight(), EPS, () -> "weight mismatch on " + graph);
        if (expectedStart != null) {
            assertEquals(expectedStart, path.getStartVertex(), () -> "start mismatch on " + graph);
        }
        if (expectedEnd != null) {
            assertEquals(expectedEnd, path.getEndVertex(), () -> "end mismatch on " + graph);
        }
    }

    private void checkNavigation(
        Graph<Integer, DefaultWeightedEdge> graph,
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> result, double oracleTotal,
        double[] approach, double[] departure)
    {
        if (oracleTotal == Double.POSITIVE_INFINITY) {
            assertProvenAbsent(result);
            return;
        }
        assertHamiltonianPath(graph, result);
        GraphPath<Integer, DefaultWeightedEdge> path = result.getPath().orElseThrow();
        double total =
            path.getWeight() + approach[path.getStartVertex()] + departure[path.getEndVertex()];
        assertEquals(oracleTotal, total, EPS, () -> "total journey mismatch on " + graph);
    }

    /**
     * Brute-force minimum total journey cost {@code approach(first) + tourWeight + departure(last)}
     * over all vertex permutations that form a valid path and satisfy the optional fixed first /
     * last vertices. Returns {@link Double#POSITIVE_INFINITY} when no such path exists.
     */
    private double bruteForce(
        Graph<Integer, DefaultWeightedEdge> graph, Integer first, Integer last,
        ToDoubleFunction<Integer> approach, ToDoubleFunction<Integer> departure)
    {
        List<Integer> vertices = new ArrayList<>(graph.vertexSet());
        if (vertices.size() == 1) {
            Integer only = vertices.get(0);
            if ((first != null && !first.equals(only)) || (last != null && !last.equals(only))) {
                return Double.POSITIVE_INFINITY;
            }
            return cost(only, approach) + cost(only, departure);
        }
        if (first != null && first.equals(last)) {
            return Double.POSITIVE_INFINITY;
        }
        int[] perm = new int[vertices.size()];
        for (int i = 0; i < perm.length; i++) {
            perm[i] = vertices.get(i);
        }
        return permuteMin(graph, perm, 0, first, last, approach, departure);
    }

    private double permuteMin(
        Graph<Integer, DefaultWeightedEdge> graph, int[] perm, int from, Integer first, Integer last,
        ToDoubleFunction<Integer> approach, ToDoubleFunction<Integer> departure)
    {
        if (from == perm.length) {
            if (first != null && perm[0] != first) {
                return Double.POSITIVE_INFINITY;
            }
            if (last != null && perm[perm.length - 1] != last) {
                return Double.POSITIVE_INFINITY;
            }
            double weight = 0d;
            for (int i = 1; i < perm.length; i++) {
                DefaultWeightedEdge e = graph.getEdge(perm[i - 1], perm[i]);
                if (e == null) {
                    return Double.POSITIVE_INFINITY;
                }
                weight += graph.getEdgeWeight(e);
            }
            return weight + cost(perm[0], approach) + cost(perm[perm.length - 1], departure);
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = from; i < perm.length; i++) {
            int tmp = perm[from];
            perm[from] = perm[i];
            perm[i] = tmp;
            best = Math.min(
                best, permuteMin(graph, perm, from + 1, first, last, approach, departure));
            tmp = perm[from];
            perm[from] = perm[i];
            perm[i] = tmp;
        }
        return best;
    }

    private double cost(int vertex, ToDoubleFunction<Integer> f)
    {
        return f == null ? 0d : f.applyAsDouble(vertex);
    }

    private Graph<Integer, DefaultWeightedEdge> randomUndirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultWeightedEdge> graph =
            new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            graph.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (random.nextDouble() < p) {
                    graph.setEdgeWeight(graph.addEdge(i, j), random.nextInt(11) - 3);
                }
            }
        }
        return graph;
    }

    private Graph<Integer, DefaultWeightedEdge> randomDirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultWeightedEdge> graph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            graph.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && random.nextDouble() < p) {
                    graph.setEdgeWeight(graph.addEdge(i, j), random.nextInt(11) - 3);
                }
            }
        }
        return graph;
    }
}
