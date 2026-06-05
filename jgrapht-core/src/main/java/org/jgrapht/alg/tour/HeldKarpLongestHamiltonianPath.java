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
import org.jgrapht.util.*;

import java.util.*;

/**
 * Exact dynamic-programming algorithm for the <b>maximum-weight (longest) Hamiltonian path</b>
 * problem on directed and undirected graphs &mdash; the maximisation twin of
 * {@link HeldKarpShortestHamiltonianPath}.
 *
 * <p>
 * It returns a Hamiltonian path (visiting every vertex exactly once) of <em>maximum</em> total edge
 * weight, using the same Bellman&ndash;Held&ndash;Karp subset dynamic program as the shortest-path
 * solver but optimising for the maximum; see {@link HeldKarpShortestHamiltonianPath} for the shared
 * literature references and {@link HeldKarpSubsetDp} for the implementation. On an unweighted graph
 * (all edge weights equal) every Hamiltonian path has the same weight, so the result is then simply
 * <em>some</em> Hamiltonian path. If a path that need not visit every vertex is wanted, use
 * {@link HeldKarpLongestPath} instead.
 *
 * <p>
 * Typical applications are maximum-weight sequencing problems: ordering all of a set of items so
 * that the total weight of adjacent pairs is maximised, for example overlap maximisation in
 * sequence assembly or similarity-maximising seriation.
 *
 * <p>
 * Complexity is {@code O(n^2 * 2^n)} time and {@code O(n * 2^n)} space. As with the shortest-path
 * solver, graphs with more than {@link #getMaxVertices()} vertices are refused with an
 * {@link IllegalArgumentException}. The algorithm is exact and deterministic, supports directed and
 * undirected graphs and arbitrary finite (including negative) edge weights, and tolerates parallel
 * edges and self-loops (self-loops are ignored; among parallel edges the maximum-weight edge is
 * used in both the DP and the reconstructed path). Non-finite edge weights ({@code NaN} or
 * infinities) are rejected with an {@link IllegalArgumentException}.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author seilat
 */
public class HeldKarpLongestHamiltonianPath<V, E>
    extends HamiltonianPathAlgorithmBase<V, E>
{

    /**
     * Default ceiling on the number of vertices; see
     * {@link HeldKarpShortestHamiltonianPath#DEFAULT_MAX_VERTICES}.
     */
    public static final int DEFAULT_MAX_VERTICES = 18;

    /**
     * Hard upper bound on the number of vertices, fixed by the {@code int} subset bitmask.
     */
    public static final int HARD_MAX_VERTICES = 30;

    private final int maxVertices;
    private long statesExpanded;

    /**
     * Constructs a new instance with the default vertex ceiling ({@link #DEFAULT_MAX_VERTICES}).
     */
    public HeldKarpLongestHamiltonianPath()
    {
        this(DEFAULT_MAX_VERTICES);
    }

    /**
     * Constructs a new instance that accepts graphs with up to {@code maxVertices} vertices.
     *
     * @param maxVertices upper bound on the number of vertices; must be at least 1 and at most
     *        {@link #HARD_MAX_VERTICES}
     * @throws IllegalArgumentException if {@code maxVertices} is outside the allowed range
     */
    public HeldKarpLongestHamiltonianPath(int maxVertices)
    {
        if (maxVertices < 1) {
            throw new IllegalArgumentException(
                "maxVertices must be at least 1, got " + maxVertices);
        }
        if (maxVertices > HARD_MAX_VERTICES) {
            throw new IllegalArgumentException(
                "maxVertices must be at most " + HARD_MAX_VERTICES
                    + " (the int-bitmask hard limit); got " + maxVertices);
        }
        this.maxVertices = maxVertices;
    }

    /**
     * Returns the maximum number of vertices this instance will accept.
     *
     * @return configured vertex ceiling
     */
    public int getMaxVertices()
    {
        return maxVertices;
    }

    /**
     * Returns the number of DP states relaxed during the most recent search. Intended for
     * diagnostics; the exact counting semantics may change if the implementation changes.
     *
     * @return DP states relaxed during the last search
     */
    public long getStatesExpanded()
    {
        return statesExpanded;
    }

    @Override
    public HamiltonianPathSearchResult<V, E> getPath(Graph<V, E> graph)
    {
        return solve(graph, null, null);
    }

    /**
     * Computes a maximum-weight Hamiltonian path that starts at {@code source}.
     *
     * @param graph the input graph
     * @param source the required first vertex
     * @return a {@link HamiltonianPathSearchResult} with the maximum-weight path, or
     *         {@code PROVEN_ABSENT}
     * @throws NullPointerException if {@code graph} or {@code source} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or {@code source} is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathFrom(Graph<V, E> graph, V source)
    {
        Objects.requireNonNull(source, "source must not be null");
        return solve(graph, source, null);
    }

    /**
     * Computes a maximum-weight Hamiltonian path that ends at {@code target}.
     *
     * @param graph the input graph
     * @param target the required last vertex
     * @return a {@link HamiltonianPathSearchResult} with the maximum-weight path, or
     *         {@code PROVEN_ABSENT}
     * @throws NullPointerException if {@code graph} or {@code target} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or {@code target} is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathTo(Graph<V, E> graph, V target)
    {
        Objects.requireNonNull(target, "target must not be null");
        return solve(graph, null, target);
    }

    /**
     * Computes a maximum-weight Hamiltonian path whose endpoints are exactly {@code source} (first)
     * and {@code target} (last).
     *
     * @param graph the input graph
     * @param source the required first vertex
     * @param target the required last vertex
     * @return a {@link HamiltonianPathSearchResult} with the maximum-weight path, or
     *         {@code PROVEN_ABSENT}
     * @throws NullPointerException if {@code graph}, {@code source}, or {@code target} is
     *         {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or an endpoint is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathBetween(Graph<V, E> graph, V source, V target)
    {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        return solve(graph, source, target);
    }

    private HamiltonianPathSearchResult<V, E> solve(Graph<V, E> graph, V source, V target)
    {
        Objects.requireNonNull(graph, "graph must not be null");
        GraphTests.requireDirectedOrUndirected(graph);
        statesExpanded = 0L;
        requireNotEmpty(graph);
        if (source != null && !graph.containsVertex(source)) {
            throw new IllegalArgumentException("source vertex is not in the graph");
        }
        if (target != null && !graph.containsVertex(target)) {
            throw new IllegalArgumentException("target vertex is not in the graph");
        }

        final int n = graph.vertexSet().size();
        if (n == 1) {
            V only = graph.vertexSet().iterator().next();
            if ((source != null && !source.equals(only))
                || (target != null && !target.equals(only)))
            {
                return HamiltonianPathSearchResult.provenAbsent(0L);
            }
            return HamiltonianPathSearchResult.found(singletonPath(graph), 0L);
        }
        if (source != null && source.equals(target)) {
            return HamiltonianPathSearchResult.provenAbsent(0L);
        }
        if (n > maxVertices) {
            throw new IllegalArgumentException(
                "HeldKarpLongestHamiltonianPath supports at most " + maxVertices
                    + " vertices; got " + n
                    + ". Use a heuristic for larger graphs or construct this class with a higher"
                    + " maxVertices ceiling.");
        }

        VertexToIntegerMapping<V> mapping = Graphs.getVertexToIntegerMapping(graph);
        List<V> indexList = mapping.getIndexList();
        Map<V, Integer> vertexMap = mapping.getVertexMap();
        final boolean directed = graph.getType().isDirected();

        double[][] cost = HeldKarpSubsetDp.costMatrix(graph, vertexMap, directed, true, n);
        long[] states = new long[1];
        int[] sequence = HeldKarpSubsetDp.solve(
            cost, n, true, true, source == null ? -1 : vertexMap.get(source),
            target == null ? -1 : vertexMap.get(target), null, null, states);
        statesExpanded = states[0];
        if (sequence == null) {
            return HamiltonianPathSearchResult.provenAbsent(statesExpanded);
        }
        List<V> vertices = new ArrayList<>(sequence.length);
        for (int idx : sequence) {
            vertices.add(indexList.get(idx));
        }
        return HamiltonianPathSearchResult
            .found(HeldKarpSubsetDp.buildPath(graph, vertices, true), statesExpanded);
    }
}
