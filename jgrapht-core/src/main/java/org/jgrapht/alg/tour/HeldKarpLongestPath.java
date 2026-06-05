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
import org.jgrapht.util.*;

import java.util.*;

/**
 * Exact dynamic-programming algorithm for the
 * <a href="https://en.wikipedia.org/wiki/Longest_path_problem">longest (simple) path problem</a> on
 * directed and undirected graphs.
 *
 * <p>
 * Unlike {@link HeldKarpLongestHamiltonianPath}, the returned path is <em>not</em> required to visit
 * every vertex: this class finds a simple path of <em>maximum total edge weight</em> over all simple
 * paths in the graph. On an unweighted graph (every edge of equal weight) the maximum-weight simple
 * path is the one with the most edges, i.e. the classic longest path by length; supply unit edge
 * weights to obtain that objective on a weighted graph. When a Hamiltonian path exists it is the
 * longest path by length, so on unweighted graphs the result then spans every vertex.
 *
 * <p>
 * The longest-path problem is NP-hard (Garey and Johnson, "Computers and Intractability", 1979,
 * problem ND29; the decision version follows from Hamiltonian path, Karp,
 * <a href="https://doi.org/10.1007/978-1-4684-2001-2_9">"Reducibility Among Combinatorial
 * Problems", 1972</a>). This exact solver uses the Bellman&ndash;Held&ndash;Karp subset dynamic
 * program (see {@link HeldKarpShortestHamiltonianPath} for the references and
 * {@link HeldKarpSubsetDp} for the shared implementation), taking the optimum over <em>all</em>
 * subsets rather than only the full vertex set. Complexity is therefore {@code O(n^2 * 2^n)} time
 * and {@code O(n * 2^n)} space, and graphs with more than {@link #getMaxVertices()} vertices are
 * refused with an {@link IllegalArgumentException}.
 *
 * <p>
 * Applications include finding the best partial route when full coverage is impossible (no
 * Hamiltonian path exists), the longest reliable chain in a network, or the longest snake-style
 * path in a grid. The algorithm is exact and deterministic, supports directed and undirected graphs
 * and arbitrary finite (including negative) edge weights, and tolerates parallel edges and
 * self-loops (self-loops are ignored; among parallel edges the maximum-weight edge is used).
 * Non-finite edge weights ({@code NaN} or infinities) are rejected with an
 * {@link IllegalArgumentException}. With negative weights the maximum-weight simple path may be a
 * single vertex (the empty-edge path of weight {@code 0}).
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author seilat
 */
public class HeldKarpLongestPath<V, E>
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
    public HeldKarpLongestPath()
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
    public HeldKarpLongestPath(int maxVertices)
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

    /**
     * Computes a maximum-weight simple path over the whole graph. For a non-empty graph this is
     * never {@code null}; in the worst case (for example all-negative weights) it is a single-vertex
     * path of weight {@code 0}.
     *
     * @param graph the input graph
     * @return a maximum-weight simple path
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, or exceeds
     *         the vertex ceiling
     */
    public GraphPath<V, E> getPath(Graph<V, E> graph)
    {
        return solve(graph, null, null);
    }

    /**
     * Computes a maximum-weight simple path that starts at {@code source}. For a graph containing
     * {@code source} this is never {@code null}; in the worst case it is the single-vertex path at
     * {@code source}.
     *
     * @param graph the input graph
     * @param source the required first vertex
     * @return a maximum-weight simple path starting at {@code source}
     * @throws NullPointerException if {@code graph} or {@code source} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or {@code source} is not a vertex of {@code graph}
     */
    public GraphPath<V, E> getPathFrom(Graph<V, E> graph, V source)
    {
        Objects.requireNonNull(source, "source must not be null");
        return solve(graph, source, null);
    }

    /**
     * Computes a maximum-weight simple path that starts at {@code source} and ends at
     * {@code target}. Returns {@code null} when no simple path connects the two vertices (in the
     * required direction, for a directed graph). When {@code source} and {@code target} are equal
     * the single-vertex path at that vertex is returned.
     *
     * @param graph the input graph
     * @param source the required first vertex
     * @param target the required last vertex
     * @return a maximum-weight simple path from {@code source} to {@code target}, or {@code null} if
     *         none exists
     * @throws NullPointerException if {@code graph}, {@code source}, or {@code target} is
     *         {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or an endpoint is not a vertex of {@code graph}
     */
    public GraphPath<V, E> getPathBetween(Graph<V, E> graph, V source, V target)
    {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        return solve(graph, source, target);
    }

    private GraphPath<V, E> solve(Graph<V, E> graph, V source, V target)
    {
        Objects.requireNonNull(graph, "graph must not be null");
        GraphTests.requireDirectedOrUndirected(graph);
        statesExpanded = 0L;
        if (graph.vertexSet().isEmpty()) {
            throw new IllegalArgumentException("Graph contains no vertices");
        }
        if (source != null && !graph.containsVertex(source)) {
            throw new IllegalArgumentException("source vertex is not in the graph");
        }
        if (target != null && !graph.containsVertex(target)) {
            throw new IllegalArgumentException("target vertex is not in the graph");
        }

        final int n = graph.vertexSet().size();
        if (n > maxVertices) {
            throw new IllegalArgumentException(
                "HeldKarpLongestPath supports at most " + maxVertices + " vertices; got " + n
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
            cost, n, true, false, source == null ? -1 : vertexMap.get(source),
            target == null ? -1 : vertexMap.get(target), null, null, states);
        statesExpanded = states[0];
        if (sequence == null) {
            return null; // only reachable for getPathBetween with no connecting simple path
        }
        List<V> vertices = new ArrayList<>(sequence.length);
        for (int idx : sequence) {
            vertices.add(indexList.get(idx));
        }
        return HeldKarpSubsetDp.buildPath(graph, vertices, true);
    }
}
