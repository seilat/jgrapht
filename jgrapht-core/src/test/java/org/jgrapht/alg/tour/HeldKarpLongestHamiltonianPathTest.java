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
import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertProvenAbsent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link HeldKarpLongestHamiltonianPath}: the maximum-weight (longest) Hamiltonian path,
 * with hand-computed deterministic optima and a permutation brute-force oracle over random weighted
 * graphs.
 */
public class HeldKarpLongestHamiltonianPathTest
{

    private static final double EPS = 1e-9;
    private static final long SEED = 0x7A6B5C4D3E2F1009L;
    private static final int GRAPHS_PER_CONFIG = 12;

    private HeldKarpLongestHamiltonianPath<Integer, DefaultWeightedEdge> solver()
    {
        return new HeldKarpLongestHamiltonianPath<>();
    }

    private double weightOf(HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r)
    {
        return r.getPath().orElseThrow().getWeight();
    }

    /** Weighted undirected triangle: w(0,1)=1, w(1,2)=2, w(0,2)=5. */
    private Graph<Integer, DefaultWeightedEdge> weightedTriangle()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), 1);
        g.setEdgeWeight(g.addEdge(1, 2), 2);
        g.setEdgeWeight(g.addEdge(0, 2), 5);
        return g;
    }

    @Test
    public void freeEndpointsReturnsGlobalMaximum()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(7.0, weightOf(r), EPS); // 0-2-1: 5 + 2
    }

    @Test
    public void betweenEndpointsMaximisesWeightForThosePins()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        assertEquals(3.0, weightOf(solver().getPathBetween(g, 0, 2)), EPS); // 0-1-2
        assertEquals(6.0, weightOf(solver().getPathBetween(g, 1, 2)), EPS); // 1-0-2
        assertEquals(7.0, weightOf(solver().getPathBetween(g, 0, 1)), EPS); // 0-2-1
    }

    @Test
    public void multigraphUsesMaximumWeightParallelEdge()
    {
        Graph<Integer, DefaultWeightedEdge> g = new WeightedMultigraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), 1);
        DefaultWeightedEdge dear = g.addEdge(0, 1); // parallel edge
        g.setEdgeWeight(dear, 9);
        g.setEdgeWeight(g.addEdge(1, 2), 3);
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(12.0, weightOf(r), EPS); // uses the weight-9 parallel edge: 9 + 3
    }

    @Test
    public void negativeWeightsHandled()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), -5);
        g.setEdgeWeight(g.addEdge(1, 2), 2);
        g.setEdgeWeight(g.addEdge(0, 2), 1);
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(3.0, weightOf(r), EPS); // 0-2-1: 1 + 2 is the largest of {-3, -4, 3}
    }

    @Test
    public void noHamiltonianPathProvenAbsent()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        g.addEdge(0, 1);
        g.addEdge(2, 3);
        assertProvenAbsent(solver().getPath(g));
    }

    @Test
    public void invalidArgumentsThrow()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        assertThrows(NullPointerException.class, () -> solver().getPath(null));
        assertThrows(NullPointerException.class, () -> solver().getPathFrom(g, null));
        assertThrows(IllegalArgumentException.class, () -> solver().getPathFrom(g, 99));
        assertThrows(IllegalArgumentException.class, () -> new HeldKarpLongestHamiltonianPath<>(0));
    }

    @Test
    public void undirectedRandomWeightedGraphsMatchOracle()
    {
        Random random = new Random(SEED);
        runRandom(random, false);
    }

    @Test
    public void directedRandomWeightedGraphsMatchOracle()
    {
        Random random = new Random(SEED ^ 0x5A5A5A5AL);
        runRandom(random, true);
    }

    private void runRandom(Random random, boolean directed)
    {
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.5, 0.8 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    Graph<Integer, DefaultWeightedEdge> g =
                        directed ? randomDirected(n, p, random) : randomUndirected(n, p, random);
                    List<Integer> vs = new ArrayList<>(g.vertexSet());
                    check(g, solver().getPath(g), bruteMaxHamiltonian(g, null, null), null, null);
                    for (Integer s : vs) {
                        check(
                            g, solver().getPathFrom(g, s), bruteMaxHamiltonian(g, s, null), s, null);
                        check(g, solver().getPathTo(g, s), bruteMaxHamiltonian(g, null, s), null, s);
                    }
                    for (Integer s : vs) {
                        for (Integer u : vs) {
                            if (!s.equals(u)) {
                                check(
                                    g, solver().getPathBetween(g, s, u),
                                    bruteMaxHamiltonian(g, s, u), s, u);
                            }
                        }
                    }
                }
            }
        }
    }

    private void check(
        Graph<Integer, DefaultWeightedEdge> g,
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> result, double oracle,
        Integer expectedStart, Integer expectedEnd)
    {
        if (oracle == Double.NEGATIVE_INFINITY) {
            assertProvenAbsent(result);
            return;
        }
        assertHamiltonianPath(g, result);
        GraphPath<Integer, DefaultWeightedEdge> path = result.getPath().orElseThrow();
        assertEquals(oracle, path.getWeight(), EPS, () -> "weight mismatch on " + g);
        if (expectedStart != null) {
            assertEquals(expectedStart, path.getStartVertex());
        }
        if (expectedEnd != null) {
            assertEquals(expectedEnd, path.getEndVertex());
        }
    }

    /** Maximum-weight Hamiltonian path over permutations, or NEGATIVE_INFINITY if none. */
    private double bruteMaxHamiltonian(
        Graph<Integer, DefaultWeightedEdge> g, Integer first, Integer last)
    {
        List<Integer> vs = new ArrayList<>(g.vertexSet());
        if (vs.size() == 1) {
            Integer only = vs.get(0);
            boolean ok = (first == null || first.equals(only)) && (last == null || last.equals(only));
            return ok ? 0d : Double.NEGATIVE_INFINITY;
        }
        if (first != null && first.equals(last)) {
            return Double.NEGATIVE_INFINITY;
        }
        int[] perm = new int[vs.size()];
        for (int i = 0; i < perm.length; i++) {
            perm[i] = vs.get(i);
        }
        return permuteMax(g, perm, 0, first, last);
    }

    private double permuteMax(
        Graph<Integer, DefaultWeightedEdge> g, int[] perm, int from, Integer first, Integer last)
    {
        if (from == perm.length) {
            if (first != null && perm[0] != first) {
                return Double.NEGATIVE_INFINITY;
            }
            if (last != null && perm[perm.length - 1] != last) {
                return Double.NEGATIVE_INFINITY;
            }
            double w = 0d;
            for (int i = 1; i < perm.length; i++) {
                DefaultWeightedEdge e = g.getEdge(perm[i - 1], perm[i]);
                if (e == null) {
                    return Double.NEGATIVE_INFINITY;
                }
                w += g.getEdgeWeight(e);
            }
            return w;
        }
        double best = Double.NEGATIVE_INFINITY;
        for (int i = from; i < perm.length; i++) {
            int tmp = perm[from];
            perm[from] = perm[i];
            perm[i] = tmp;
            best = Math.max(best, permuteMax(g, perm, from + 1, first, last));
            tmp = perm[from];
            perm[from] = perm[i];
            perm[i] = tmp;
        }
        return best;
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
