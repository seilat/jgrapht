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
import java.util.function.*;

/**
 * Exact dynamic-programming algorithm for the <b>minimum-weight Hamiltonian path</b> problem (also
 * known as the <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">path / open
 * Traveling Salesman Problem</a>) on directed and undirected graphs.
 *
 * <p>
 * Where {@link HeldKarpHamiltonianPath} only decides <em>existence</em> of a Hamiltonian path, this
 * class returns one of <em>minimum total edge weight</em>. It is the open-path counterpart of the
 * Hamiltonian-cycle / tour solvers such as {@link HeldKarpTSP}: it visits every vertex exactly once
 * but, unlike a tour, does not return to its start, so its two endpoints may differ.
 *
 * <p>
 * The implementation is the classic Bellman&ndash;Held&ndash;Karp subset dynamic program, applied
 * to the optimisation (rather than existence) objective. The state {@code dp[subset][v]} holds the
 * minimum weight of a simple path that visits exactly the vertices in {@code subset} and ends at
 * {@code v}; the transition relaxes {@code dp[subset ∪ {u}][u]} with
 * {@code dp[subset][v] + w(v, u)} for every edge {@code (v, u)} leaving the current subset. The DP
 * formulation is due to Bellman, <a href="https://doi.org/10.1145/321105.321111">"Dynamic
 * Programming Treatment of the Travelling Salesman Problem", J. ACM 9(1):61&ndash;63, 1962</a>, and
 * independently to Held and Karp, <a href="https://doi.org/10.1137/0110015">"A Dynamic Programming
 * Approach to Sequencing Problems", J. SIAM 10(1):196&ndash;210, 1962</a>; the open-path adaptation
 * (no closing edge back to the start, and a free choice of end vertex) is a standard textbook
 * variant, see CLRS, "Introduction to Algorithms" (4th ed.), the dynamic-programming chapter, and
 * Korte and Vygen, "Combinatorial Optimization" (6th ed.), chapter 21 (the Traveling Salesman
 * Problem). The underlying decision problem is NP-complete, Karp,
 * <a href="https://doi.org/10.1007/978-1-4684-2001-2_9">"Reducibility Among Combinatorial
 * Problems", 1972</a>.
 *
 * <p>
 * Complexity is {@code O(n^2 * 2^n)} time and {@code O(n * 2^n)} space, where {@code n} is the
 * number of vertices. Because both grow exponentially, this class refuses graphs with more than
 * {@link #getMaxVertices()} vertices by throwing {@link IllegalArgumentException}. The default
 * ceiling {@link #DEFAULT_MAX_VERTICES} is lower than that of {@link HeldKarpHamiltonianPath}
 * because the optimisation DP stores a {@code double} cost per cell rather than a single byte.
 * Callers handling larger graphs should fall back to a heuristic / approximation; for the metric
 * case a long line of work brings the path-TSP approximation ratio down to {@code 1.5} (Hoogeveen's
 * {@code 5/3} Christofides variant, 1991; An, Kleinberg and Shmoys'
 * {@code (1 + √5)/2 ≈ 1.618}, J. ACM 2015; Seb&#337;'s {@code 8/5}, 2013; and Zenklusen's
 * {@code 1.5}, <a href="https://arxiv.org/abs/1805.04131">2019</a>), none of which is implemented
 * here.
 *
 * <p>
 * The algorithm is exact and deterministic. It supports directed and undirected graphs, arbitrary
 * finite (including negative) edge weights, and tolerates parallel edges and self-loops: self-loops
 * are ignored because they cannot extend a simple path, and among parallel edges between the same
 * pair of vertices the minimum-weight edge is used, both in the DP and in the reconstructed path.
 * Non-finite edge weights ({@code NaN} or infinities) are rejected with an
 * {@link IllegalArgumentException}.
 *
 * <p>
 * In addition to the free-endpoint {@link #getPath(Graph)}, the class offers endpoint-constrained
 * variants ({@link #getPathFrom(Graph, Object)}, {@link #getPathTo(Graph, Object)},
 * {@link #getPathBetween(Graph, Object, Object)}) that fix the first and/or last vertex, and a
 * navigation-oriented family
 * {@link #getShortestPathWithBestEndpoints(Graph, ToDoubleFunction, ToDoubleFunction)} (with
 * single-endpoint cousins {@link #getShortestPathWithBestStart} and
 * {@link #getShortestPathWithBestEnd}) that, given caller-supplied per-vertex approach and departure
 * costs, returns the tour minimising the <em>total</em> journey cost
 * {@code approach(a) + weight(a … b) + departure(b)}. That family is the exact, weight-optimal
 * counterpart of
 * {@link BacktrackingHamiltonianPath#getPathWithBestEndpoints(Graph, ToDoubleFunction, ToDoubleFunction)};
 * it is realised by the classic super-source / super-sink reduction, folded into the DP by biasing
 * each start state by its approach cost and each terminal state by its departure cost (rather than
 * by materialising dummy vertices).
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author seilat
 */
public class HeldKarpShortestHamiltonianPath<V, E>
    extends HamiltonianPathAlgorithmBase<V, E>
{

    /**
     * Default ceiling on the number of vertices. With {@code n = 18} the {@code double} DP table
     * needs roughly {@code 18 * 2^18 ≈ 4.7M} cells (about 38&nbsp;MB), which keeps the default
     * within typical heap limits. This is lower than {@link HeldKarpHamiltonianPath}'s default
     * because that class stores one byte per cell rather than a {@code double}.
     */
    public static final int DEFAULT_MAX_VERTICES = 18;

    /**
     * Hard upper bound on the number of vertices. The subset bitmask is a signed {@code int}, so
     * {@code 1 << 31} would overflow to a negative value; {@code 30} is the largest safe exponent.
     * In practice memory is the binding constraint far below this ceiling: the {@code double} DP
     * table needs about {@code 8 * n * 2^n} bytes (roughly 38&nbsp;MB at {@code n = 18},
     * 800&nbsp;MB at {@code n = 22}, 3.4&nbsp;GB at {@code n = 24}), so raising the ceiling much
     * beyond {@link #DEFAULT_MAX_VERTICES} risks {@link OutOfMemoryError}.
     */
    public static final int HARD_MAX_VERTICES = 30;

    private final int maxVertices;
    private long statesExpanded;

    /**
     * Constructs a new instance with the default vertex ceiling ({@link #DEFAULT_MAX_VERTICES}).
     */
    public HeldKarpShortestHamiltonianPath()
    {
        this(DEFAULT_MAX_VERTICES);
    }

    /**
     * Constructs a new instance that accepts graphs with up to {@code maxVertices} vertices.
     *
     * @param maxVertices upper bound on the number of vertices the algorithm will accept; must be
     *        at least 1 and at most {@link #HARD_MAX_VERTICES}
     * @throws IllegalArgumentException if {@code maxVertices} is outside the allowed range
     */
    public HeldKarpShortestHamiltonianPath(int maxVertices)
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
     * Returns the number of DP states (cost cells relaxed) the algorithm filled during the most
     * recent search. Intended for diagnostics; the exact counting semantics may change if the
     * implementation changes.
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
        return solve(graph, null, null, null, null);
    }

    /**
     * Computes a minimum-weight Hamiltonian path that starts at {@code source}. In a directed graph
     * {@code source} is the head of the path; in an undirected graph it is one of the two
     * endpoints.
     *
     * @param graph the input graph
     * @param source the required first vertex
     * @return a {@link HamiltonianPathSearchResult} with the minimum-weight path, or
     *         {@code PROVEN_ABSENT}
     * @throws NullPointerException if {@code graph} or {@code source} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or {@code source} is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathFrom(Graph<V, E> graph, V source)
    {
        Objects.requireNonNull(source, "source must not be null");
        return solve(graph, source, null, null, null);
    }

    /**
     * Computes a minimum-weight Hamiltonian path that ends at {@code target}. In a directed graph
     * {@code target} is the tail of the path; in an undirected graph it is one of the two
     * endpoints.
     *
     * @param graph the input graph
     * @param target the required last vertex
     * @return a {@link HamiltonianPathSearchResult} with the minimum-weight path, or
     *         {@code PROVEN_ABSENT}
     * @throws NullPointerException if {@code graph} or {@code target} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or {@code target} is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathTo(Graph<V, E> graph, V target)
    {
        Objects.requireNonNull(target, "target must not be null");
        return solve(graph, null, target, null, null);
    }

    /**
     * Computes a minimum-weight Hamiltonian path whose endpoints are exactly {@code source} (first
     * vertex) and {@code target} (last vertex) &mdash; the fixed-endpoint (s&ndash;t) path-TSP.
     *
     * <p>
     * If {@code source} and {@code target} are equal and the graph has a single vertex the singleton
     * path is returned; if they are equal and the graph has more than one vertex no such path can
     * exist and {@code PROVEN_ABSENT} is returned.
     *
     * @param graph the input graph
     * @param source the required first vertex
     * @param target the required last vertex
     * @return a {@link HamiltonianPathSearchResult} with the minimum-weight path, or
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
        return solve(graph, source, target, null, null);
    }

    /**
     * Computes the Hamiltonian path that minimises the <em>total</em> journey cost
     * {@code approachCost(a) + weight(a … b) + departureCost(b)}, where {@code a} and {@code b}
     * are the (freely chosen) first and last vertices of the tour. This is the weight-optimal,
     * navigation-oriented counterpart of
     * {@link BacktrackingHamiltonianPath#getPathWithBestEndpoints(Graph, ToDoubleFunction, ToDoubleFunction)}:
     * rather than only minimising the off-tour legs among feasible endpoint pairs, it minimises the
     * approach leg, the full tour weight, and the departure leg jointly, in a single dynamic program.
     *
     * <p>
     * The two cost functions supply, per vertex {@code v}, the cost of starting the tour at
     * {@code v} ({@code approachCost}) and of ending it at {@code v} ({@code departureCost}); how
     * those are computed (straight-line distance to an external position, a precomputed
     * shortest-path distance, a lookup table, ...) is up to the caller, so the source and target
     * being approached need not be vertices of {@code graph}. To leave one leg free, use
     * {@link #getShortestPathWithBestStart} (free end) or {@link #getShortestPathWithBestEnd} (free
     * start). The cost functions must return finite values.
     *
     * <p>
     * The returned {@link GraphPath} is the optimal tour itself; its {@link GraphPath#getWeight()
     * weight} is the tour weight (excluding the off-graph approach and departure legs, which the
     * caller can recover from its own cost functions and the path's endpoints).
     *
     * @param graph the input graph
     * @param approachCost cost of starting the tour at a vertex
     * @param departureCost cost of ending the tour at a vertex
     * @return a {@link HamiltonianPathSearchResult} with the total-cost-optimal tour, or
     *         {@code PROVEN_ABSENT}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or a cost function returns a non-finite value
     */
    public HamiltonianPathSearchResult<V, E> getShortestPathWithBestEndpoints(
        Graph<V, E> graph, ToDoubleFunction<V> approachCost, ToDoubleFunction<V> departureCost)
    {
        Objects.requireNonNull(approachCost, "approachCost must not be null");
        Objects.requireNonNull(departureCost, "departureCost must not be null");
        return solve(graph, null, null, approachCost, departureCost);
    }

    /**
     * Total-cost-optimal tour with a free end vertex: minimises
     * {@code approachCost(a) + weight(a … b)} over all start vertices {@code a} and end vertices
     * {@code b}. See {@link #getShortestPathWithBestEndpoints} for the full description.
     *
     * @param graph the input graph
     * @param approachCost cost of starting the tour at a vertex
     * @return a {@link HamiltonianPathSearchResult} with the optimal tour, or {@code PROVEN_ABSENT}
     * @throws NullPointerException if {@code graph} or {@code approachCost} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or {@code approachCost} returns a non-finite value
     */
    public HamiltonianPathSearchResult<V, E> getShortestPathWithBestStart(
        Graph<V, E> graph, ToDoubleFunction<V> approachCost)
    {
        Objects.requireNonNull(approachCost, "approachCost must not be null");
        return solve(graph, null, null, approachCost, null);
    }

    /**
     * Total-cost-optimal tour with a free start vertex: minimises
     * {@code weight(a … b) + departureCost(b)} over all start vertices {@code a} and end vertices
     * {@code b}. See {@link #getShortestPathWithBestEndpoints} for the full description.
     *
     * @param graph the input graph
     * @param departureCost cost of ending the tour at a vertex
     * @return a {@link HamiltonianPathSearchResult} with the optimal tour, or {@code PROVEN_ABSENT}
     * @throws NullPointerException if {@code graph} or {@code departureCost} is {@code null}
     * @throws IllegalArgumentException if the graph is empty, not directed/undirected, exceeds the
     *         vertex ceiling, or {@code departureCost} returns a non-finite value
     */
    public HamiltonianPathSearchResult<V, E> getShortestPathWithBestEnd(
        Graph<V, E> graph, ToDoubleFunction<V> departureCost)
    {
        Objects.requireNonNull(departureCost, "departureCost must not be null");
        return solve(graph, null, null, null, departureCost);
    }

    /**
     * Core dynamic program shared by every entry point. {@code source} / {@code target}, when
     * non-null, fix the first / last vertex; {@code approachCost} / {@code departureCost}, when
     * non-null, bias each start / terminal state so that the optimum accounts for off-tour legs
     * (the super-source / super-sink reduction). The reported path weight is always the true sum of
     * its tour edges, independent of those biases.
     */
    private HamiltonianPathSearchResult<V, E> solve(
        Graph<V, E> graph, V source, V target, ToDoubleFunction<V> approachCost,
        ToDoubleFunction<V> departureCost)
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
                "HeldKarpShortestHamiltonianPath supports at most " + maxVertices
                    + " vertices; got " + n
                    + ". Use a heuristic / approximation for larger graphs or construct this class"
                    + " with a higher maxVertices ceiling.");
        }

        VertexToIntegerMapping<V> mapping = Graphs.getVertexToIntegerMapping(graph);
        List<V> indexList = mapping.getIndexList();
        Map<V, Integer> vertexMap = mapping.getVertexMap();
        final boolean directed = graph.getType().isDirected();

        double[][] cost = HeldKarpSubsetDp.costMatrix(graph, vertexMap, directed, false, n);
        long[] states = new long[1];
        int[] sequence = HeldKarpSubsetDp.solve(
            cost, n, false, true, source == null ? -1 : vertexMap.get(source),
            target == null ? -1 : vertexMap.get(target), bias(indexList, approachCost, n),
            bias(indexList, departureCost, n), states);
        statesExpanded = states[0];
        if (sequence == null) {
            return HamiltonianPathSearchResult.provenAbsent(statesExpanded);
        }
        List<V> vertices = new ArrayList<>(sequence.length);
        for (int idx : sequence) {
            vertices.add(indexList.get(idx));
        }
        return HamiltonianPathSearchResult
            .found(HeldKarpSubsetDp.buildPath(graph, vertices, false), statesExpanded);
    }

    /**
     * Materialises a per-vertex cost function as a {@code double[]} bias indexed by vertex position,
     * or {@code null} when no function is supplied. A non-finite cost would corrupt the DP
     * comparisons, so it is rejected with an {@link IllegalArgumentException}.
     */
    private double[] bias(List<V> indexList, ToDoubleFunction<V> cost, int n)
    {
        if (cost == null) {
            return null;
        }
        double[] bias = new double[n];
        for (int i = 0; i < n; i++) {
            double c = cost.applyAsDouble(indexList.get(i));
            if (!Double.isFinite(c)) {
                throw new IllegalArgumentException(
                    "endpoint cost must be finite, got " + c);
            }
            bias[i] = c;
        }
        return bias;
    }
}
