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

import java.util.*;

/**
 * Package-private shared Bellman&ndash;Held&ndash;Karp subset dynamic program backing the exact
 * shortest / longest Hamiltonian path and longest simple path solvers in this package
 * ({@link HeldKarpShortestHamiltonianPath}, {@link HeldKarpLongestHamiltonianPath},
 * {@link HeldKarpLongestPath}).
 *
 * <p>
 * The state {@code dp[subset][v]} is the optimal (minimum or maximum) total of a simple path that
 * visits exactly the vertices in {@code subset} and ends at {@code v}; the transition relaxes
 * {@code dp[subset ∪ {u}][u]} with {@code dp[subset][v] + cost(v, u)}. The same machinery serves
 * every variant by toggling two flags: {@code maximize} (minimise vs maximise the total) and
 * {@code spanning} (require the optimum over the full vertex set, i.e. a Hamiltonian path, vs the
 * optimum over every subset, i.e. an arbitrary simple path). Optional fixed endpoints and additive
 * per-endpoint biases (the super-source / super-sink reduction) are supported.
 *
 * <p>
 * The DP itself operates on an {@code int}-indexed cost matrix so it carries no generics; the
 * generic helpers {@link #costMatrix} and {@link #buildPath} translate between a JGraphT
 * {@link Graph} and that matrix. See {@link HeldKarpShortestHamiltonianPath} for the literature
 * references shared by all three solvers.
 */
final class HeldKarpSubsetDp
{
    private static final byte UNVISITED = -1;
    private static final byte START_SENTINEL = -2;

    private HeldKarpSubsetDp()
    {
    }

    /**
     * Builds the {@code n x n} extremal edge-weight matrix used by the DP. {@code cost[i][j]} is the
     * weight of the edge usable to step from {@code i} to {@code j} (an outgoing edge in a directed
     * graph, an incident edge in an undirected one); among parallel edges the minimum-weight edge
     * is kept when {@code maximize} is {@code false} and the maximum-weight edge when it is
     * {@code true}, so the matrix is consistent with the optimisation sense. Absent edges are
     * {@link Double#POSITIVE_INFINITY} and self-loops are ignored.
     *
     * <p>
     * Edge weights must be finite: an infinite weight would be indistinguishable from the
     * absent-edge sentinel, and {@code NaN} would corrupt the {@code min}/{@code max} reductions
     * and the DP comparisons. Non-finite weights are therefore rejected with an
     * {@link IllegalArgumentException}.
     */
    static <V, E> double[][] costMatrix(
        Graph<V, E> graph, Map<V, Integer> vertexMap, boolean directed, boolean maximize, int n)
    {
        double[][] cost = new double[n][n];
        for (double[] row : cost) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (E e : graph.edgeSet()) {
            V a = graph.getEdgeSource(e);
            V b = graph.getEdgeTarget(e);
            if (a.equals(b)) {
                continue; // self-loop cannot extend a simple path
            }
            double w = graph.getEdgeWeight(e);
            if (!Double.isFinite(w)) {
                throw new IllegalArgumentException(
                    "edge weights must be finite; found " + w + " on edge " + e);
            }
            int i = vertexMap.get(a);
            int j = vertexMap.get(b);
            cost[i][j] = pick(cost[i][j], w, maximize);
            if (!directed) {
                cost[j][i] = pick(cost[j][i], w, maximize);
            }
        }
        return cost;
    }

    private static double pick(double current, double candidate, boolean maximize)
    {
        if (current == Double.POSITIVE_INFINITY) {
            return candidate; // first edge seen for this ordered pair
        }
        return maximize ? Math.max(current, candidate) : Math.min(current, candidate);
    }

    /**
     * Runs the subset DP over the given cost matrix and returns the optimal vertex-index sequence,
     * or {@code null} when no admissible path exists.
     *
     * @param cost extremal edge-weight matrix from {@link #costMatrix}
     * @param n number of vertices
     * @param maximize {@code true} to maximise the total, {@code false} to minimise it
     * @param spanning {@code true} to require a Hamiltonian path (full vertex set); {@code false}
     *        to allow any simple path and optimise over all subsets
     * @param sourceIdx required first vertex index, or {@code -1} to leave the start free
     * @param targetIdx required last vertex index, or {@code -1} to leave the end free
     * @param approachBias additive per-start bias of length {@code n}, or {@code null} for none
     * @param departureBias additive per-end bias of length {@code n}, or {@code null} for none
     * @param statesOut single-element array receiving the number of DP states relaxed
     * @return the optimal vertex-index sequence, or {@code null} if none exists
     */
    static int[] solve(
        double[][] cost, int n, boolean maximize, boolean spanning, int sourceIdx, int targetIdx,
        double[] approachBias, double[] departureBias, long[] statesOut)
    {
        final double worst = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        final int fullMask = (1 << n) - 1;
        long states = 0L;

        double[][] dp = new double[1 << n][n];
        for (double[] row : dp) {
            Arrays.fill(row, worst);
        }
        byte[][] pred = new byte[1 << n][n];
        for (byte[] row : pred) {
            Arrays.fill(row, UNVISITED);
        }

        for (int v = 0; v < n; v++) {
            if (sourceIdx >= 0 && v != sourceIdx) {
                continue;
            }
            dp[1 << v][v] = approachBias == null ? 0d : approachBias[v];
            pred[1 << v][v] = START_SENTINEL;
            states++;
        }

        for (int mask = 1; mask <= fullMask; mask++) {
            for (int v = 0; v < n; v++) {
                if (((mask >> v) & 1) == 0 || dp[mask][v] == worst) {
                    continue;
                }
                double base = dp[mask][v];
                double[] costV = cost[v];
                for (int u = 0; u < n; u++) {
                    if (((mask >> u) & 1) != 0 || costV[u] == Double.POSITIVE_INFINITY) {
                        continue;
                    }
                    int newMask = mask | (1 << u);
                    double candidate = base + costV[u];
                    if (better(candidate, dp[newMask][u], maximize)) {
                        dp[newMask][u] = candidate;
                        pred[newMask][u] = (byte) v;
                        states++;
                    }
                }
            }
        }

        int bestMask = -1;
        int bestEnd = -1;
        double bestValue = worst;
        for (int mask = 1; mask <= fullMask; mask++) {
            if (spanning && mask != fullMask) {
                continue;
            }
            for (int v = 0; v < n; v++) {
                if (((mask >> v) & 1) == 0 || dp[mask][v] == worst) {
                    continue;
                }
                if (targetIdx >= 0 && v != targetIdx) {
                    continue;
                }
                double value = dp[mask][v] + (departureBias == null ? 0d : departureBias[v]);
                if (bestEnd == -1 || better(value, bestValue, maximize)) {
                    bestValue = value;
                    bestMask = mask;
                    bestEnd = v;
                }
            }
        }

        statesOut[0] = states;
        if (bestEnd == -1) {
            return null;
        }
        return reconstruct(pred, bestMask, bestEnd);
    }

    private static boolean better(double candidate, double incumbent, boolean maximize)
    {
        return maximize ? candidate > incumbent : candidate < incumbent;
    }

    private static int[] reconstruct(byte[][] pred, int bestMask, int bestEnd)
    {
        List<Integer> reversed = new ArrayList<>();
        int mask = bestMask;
        int cur = bestEnd;
        while (cur != -1) {
            reversed.add(cur);
            byte p = pred[mask][cur];
            mask ^= (1 << cur);
            cur = (p == START_SENTINEL) ? -1 : (p & 0xFF);
        }
        Collections.reverse(reversed);
        int[] sequence = new int[reversed.size()];
        for (int i = 0; i < sequence.length; i++) {
            sequence[i] = reversed.get(i);
        }
        return sequence;
    }

    /**
     * Materialises a vertex-index sequence as a {@link GraphPath}, selecting for each consecutive
     * pair the extremal connecting edge that matches the optimisation sense (minimum-weight edge
     * when {@code maximize} is {@code false}, maximum-weight edge when {@code true}), so the
     * reported weight equals the optimised total even in multigraphs.
     */
    static <V, E> GraphPath<V, E> buildPath(
        Graph<V, E> graph, List<V> vertices, boolean maximize)
    {
        final int n = vertices.size();
        if (n == 1) {
            V only = vertices.get(0);
            return new GraphWalk<>(
                graph, only, only, Collections.singletonList(only), Collections.emptyList(), 0d);
        }
        List<E> edges = new ArrayList<>(n - 1);
        double weight = 0d;
        for (int i = 1; i < n; i++) {
            V u = vertices.get(i - 1);
            V v = vertices.get(i);
            E edge = extremeEdge(graph, u, v, maximize);
            edges.add(edge);
            weight += graph.getEdgeWeight(edge);
        }
        return new GraphWalk<>(
            graph, vertices.get(0), vertices.get(n - 1), vertices, edges, weight);
    }

    private static <V, E> E extremeEdge(Graph<V, E> graph, V u, V v, boolean maximize)
    {
        E best = null;
        double bestWeight = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (E e : graph.getAllEdges(u, v)) {
            double w = graph.getEdgeWeight(e);
            if (best == null || (maximize ? w > bestWeight : w < bestWeight)) {
                best = e;
                bestWeight = w;
            }
        }
        return best;
    }
}
