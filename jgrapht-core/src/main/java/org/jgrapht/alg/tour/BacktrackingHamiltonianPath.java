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
import org.jgrapht.alg.connectivity.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.graph.*;
import org.jgrapht.traverse.*;
import org.jgrapht.util.*;

import java.util.*;
import java.util.function.*;

/**
 * Exact backtracking algorithm for the
 * <a href="https://en.wikipedia.org/wiki/Hamiltonian_path">Hamiltonian path problem</a> on
 * directed and undirected graphs.
 *
 * <p>
 * The algorithm performs depth-first search over simple paths. From each candidate start vertex
 * it tries to extend the current path by an unused adjacent vertex; the first path that visits
 * every vertex exactly once is returned. If the exhaustive search proves that no such path
 * exists, a {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT} result is returned.
 *
 * <p>
 * The unbounded {@link #getPath(Graph)} entry point only returns {@code PATH_FOUND} or
 * {@code PROVEN_ABSENT}. The bounded {@link #searchWithStateLimit(Graph, long)} variant may
 * additionally return {@link HamiltonianPathSearchResult.Status#ABORTED} when the search hits
 * its state budget before completing.
 *
 * <p>
 * The endpoint-constrained variants {@link #getPathFrom(Graph, Object)},
 * {@link #getPathTo(Graph, Object)} and {@link #getPathBetween(Graph, Object, Object)} restrict
 * the search to Hamiltonian paths that begin at a given vertex, end at a given vertex, or run
 * between a given pair of vertices respectively. They use the same exact search and report
 * {@code PROVEN_ABSENT} when no path satisfying the endpoint constraints exists.
 *
 * <p>
 * For navigation-style queries where the endpoints are not fixed but should be cheap to reach and
 * leave, {@link #getPathNearEndpoints(Graph, ToDoubleFunction, ToDoubleFunction)} chooses the
 * endpoint pair that minimises caller-supplied approach and departure costs among all pairs that
 * admit a Hamiltonian path.
 *
 * <p>
 * This implementation is a straightforward exact DFS / backtracking solver for Hamiltonian
 * path existence, using standard pruning and candidate-ordering ideas rather than reproducing
 * any single published algorithm verbatim. For background see Rubin, F., "A Search Procedure
 * for Hamilton Paths and Circuits", JACM 21(4), 1974 (backtracking search); the
 * minimum-remaining-values candidate ordering is in the spirit of Warnsdorff's rule (Warnsdorff,
 * "Des R&ouml;sselsprunges einfachste und allgemeinste L&ouml;sung", 1823) and of
 * constraint-satisfaction reachability propagation (Tsang, "Foundations of Constraint
 * Satisfaction", 1993); the structural prechecks (block / cut-vertex / bridge / SCC) are
 * well-known necessary conditions from graph theory (see Diestel, "Graph Theory", chapter 3,
 * for the underlying decompositions).
 *
 * <p>
 * The general Hamiltonian path problem is NP-complete. This implementation is exact and runs in
 * exponential time in the worst case, so callers should expect it to be suitable for relatively
 * small graphs (the practical limit depends heavily on graph structure: sparse and highly
 * constrained graphs are typically tractable for much larger {@code n} than dense random
 * graphs).
 *
 * <p>
 * The implementation applies the following correctness-preserving prechecks and search
 * heuristics, none of which can cause a false negative:
 * <ul>
 * <li>If the graph contains exactly one vertex, the singleton path is returned.</li>
 * <li>If an undirected graph is not connected, no Hamiltonian path can exist, so a
 * {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT} result is returned without DFS
 * search.</li>
 * <li>If an undirected graph has more than two vertices of degree 1, no Hamiltonian path can
 * exist (a Hamiltonian path has at most two endpoints, and any degree-1 vertex must be one of
 * them).</li>
 * <li>For undirected graphs, every cut vertex (articulation point) must belong to at most two
 * biconnected blocks. A Hamiltonian path visits each vertex once with at most two path-edges
 * incident to it, so it cannot enter more than two blocks meeting at a single cut vertex.</li>
 * <li>For undirected graphs, the bridge tree (whose nodes are the 2-edge-connected components
 * and whose edges are the original graph's bridges) must itself be a path. Equivalently, no
 * 2-edge-connected component may have more than two incident bridges, since a Hamiltonian
 * path enters and leaves each such component at most once.</li>
 * <li>For directed graphs, the strongly connected component condensation must itself admit a
 * Hamiltonian path. Any Hamiltonian path in the original directed graph projects to one on the
 * condensation DAG; when that condensation has no Hamiltonian path, the original graph cannot
 * either.</li>
 * <li>At every search step the current endpoint must be able to reach every still-unvisited
 * vertex through unvisited intermediaries. Otherwise the branch is pruned.</li>
 * <li>Candidate next vertices are tried in ascending order of their remaining (unvisited)
 * onward degree. This is a minimum-remaining-values style heuristic: vertices with few onward
 * options tend to fail or commit early, which generally reduces search.</li>
 * </ul>
 *
 * <p>
 * Empty graphs are rejected with an {@link IllegalArgumentException}, matching the convention
 * used by other Hamiltonian / TSP solvers in JGraphT (for example
 * {@link org.jgrapht.alg.tour.HeldKarpTSP}). Graphs with self-loops are accepted but self-loops
 * are ignored, since they cannot be part of a simple path. In multigraphs, parallel edges
 * between the same pair of vertices collapse into a single DFS branch and the returned path
 * picks an arbitrary representative edge via {@link Graph#getEdge}; the result is not
 * weight-optimised across parallel edges.
 *
 * <p>
 * The returned {@link GraphPath} is a {@link GraphWalk} whose vertex list contains every vertex
 * of the graph exactly once, whose consecutive vertices are connected by an edge of the graph
 * (respecting direction in directed graphs), and whose weight is the sum of the chosen edges'
 * weights according to {@link Graph#getEdgeWeight(Object)}.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author seilat
 */
public class BacktrackingHamiltonianPath<V, E>
    extends HamiltonianPathAlgorithmBase<V, E>
{

    private long statesExpanded;
    private long maxStatesLimit;
    private boolean aborted;
    // Per-search scratch buffers for the reachability BFS, reused across the (exponentially many)
    // DFS nodes of a single search to avoid an allocation at every node.
    private boolean[] reachableScratch;
    private int[] reachQueueScratch;

    /**
     * Constructs a new instance.
     */
    public BacktrackingHamiltonianPath()
    {
    }

    /**
     * Returns the number of DFS nodes the search explored during the most recent call to
     * {@link #getPath(Graph)}. A "state" corresponds to one entry into the recursive extension
     * routine, i.e. one partial path the solver considered. The counter is reset at the start
     * of every {@code getPath} invocation.
     *
     * <p>
     * After a {@link #getPathNearEndpoints(Graph, java.util.function.ToDoubleFunction,
     * java.util.function.ToDoubleFunction)} call, which performs several internal searches, the
     * value is the sum of the states explored across all of them.
     *
     * <p>
     * This value is intended for diagnostics and benchmarking, similar to
     * {@code AStarShortestPath#getNumberOfExpandedNodes()}. The exact counting semantics may
     * change if the implementation changes.
     *
     * @return states (partial paths) explored during the last search
     */
    public long getStatesExpanded()
    {
        return statesExpanded;
    }

    @Override
    public HamiltonianPathSearchResult<V, E> getPath(Graph<V, E> graph)
    {
        return search(graph, 0L, null, null);
    }

    /**
     * Computes a Hamiltonian path that <em>starts</em> at {@code source}, i.e. a Hamiltonian path
     * whose first vertex is {@code source}. The remaining endpoint may be any other vertex.
     *
     * <p>
     * In an undirected graph this is equivalent to requiring {@code source} to be one of the two
     * path endpoints (a path and its reverse are the same path). In a directed graph it
     * specifically requires {@code source} to be the head of the path, with every edge traversed
     * in its forward direction.
     *
     * <p>
     * The search is otherwise identical to {@link #getPath(Graph)}: it is exact, unbounded, and
     * returns either {@link HamiltonianPathSearchResult.Status#PATH_FOUND} or
     * {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT}.
     *
     * @param graph the input graph
     * @param source the required first vertex of the path
     * @return a {@link HamiltonianPathSearchResult} describing the outcome
     * @throws NullPointerException if {@code graph} or {@code source} is {@code null}
     * @throws IllegalArgumentException if the graph is empty or not directed/undirected, or if
     *         {@code source} is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathFrom(Graph<V, E> graph, V source)
    {
        Objects.requireNonNull(source, "source must not be null");
        return search(graph, 0L, source, null);
    }

    /**
     * Computes a Hamiltonian path that <em>ends</em> at {@code target}, i.e. a Hamiltonian path
     * whose last vertex is {@code target}. The other endpoint may be any other vertex.
     *
     * <p>
     * In an undirected graph this is equivalent to requiring {@code target} to be one of the two
     * path endpoints. In a directed graph it specifically requires {@code target} to be the tail
     * of the path, with every edge traversed in its forward direction.
     *
     * <p>
     * The search is otherwise identical to {@link #getPath(Graph)}: it is exact, unbounded, and
     * returns either {@link HamiltonianPathSearchResult.Status#PATH_FOUND} or
     * {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT}.
     *
     * @param graph the input graph
     * @param target the required last vertex of the path
     * @return a {@link HamiltonianPathSearchResult} describing the outcome
     * @throws NullPointerException if {@code graph} or {@code target} is {@code null}
     * @throws IllegalArgumentException if the graph is empty or not directed/undirected, or if
     *         {@code target} is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathTo(Graph<V, E> graph, V target)
    {
        Objects.requireNonNull(target, "target must not be null");
        return search(graph, 0L, null, target);
    }

    /**
     * Computes a Hamiltonian path whose two endpoints are exactly {@code source} (first vertex)
     * and {@code target} (last vertex).
     *
     * <p>
     * For directed graphs the path runs from {@code source} to {@code target} following edge
     * directions. For undirected graphs it is an open path with {@code source} and {@code target}
     * as its two ends.
     *
     * <p>
     * If {@code source} and {@code target} are equal and the graph has a single vertex, the
     * trivial singleton path is returned. If they are equal and the graph has more than one
     * vertex, no such path can exist (a Hamiltonian path on more than one vertex has two distinct
     * endpoints), so {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT} is returned.
     *
     * <p>
     * The search is otherwise identical to {@link #getPath(Graph)}: it is exact, unbounded, and
     * returns either {@link HamiltonianPathSearchResult.Status#PATH_FOUND} or
     * {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT}.
     *
     * @param graph the input graph
     * @param source the required first vertex of the path
     * @param target the required last vertex of the path
     * @return a {@link HamiltonianPathSearchResult} describing the outcome
     * @throws NullPointerException if {@code graph}, {@code source}, or {@code target} is
     *         {@code null}
     * @throws IllegalArgumentException if the graph is empty or not directed/undirected, or if
     *         {@code source} or {@code target} is not a vertex of {@code graph}
     */
    public HamiltonianPathSearchResult<V, E> getPathBetween(Graph<V, E> graph, V source, V target)
    {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        return search(graph, 0L, source, target);
    }

    /**
     * Computes a Hamiltonian path whose two endpoints are chosen to minimise the off-tour travel
     * needed to enter the tour at its first vertex and to leave it from its last vertex.
     *
     * <p>
     * This is a convenience variant for navigation-style queries: you are positioned somewhere and
     * want to visit every vertex of {@code graph}, entering the tour at whichever vertex is
     * cheapest to reach and leaving from whichever vertex is cheapest to depart towards your
     * destination. The two cost functions supply, per vertex {@code v}, the cost of starting the
     * tour at {@code v} ({@code approachCost}) and of ending the tour at {@code v}
     * ({@code departureCost}); how those costs are computed (straight-line distance to an external
     * point, a precomputed shortest-path distance in the same graph, a lookup table, ...) is
     * entirely up to the caller. Because the costs are supplied per vertex, the source and target
     * being approached need not be vertices of {@code graph} at all.
     *
     * <p>
     * Either cost function may be {@code null} to leave that endpoint unconstrained:
     * <ul>
     * <li>{@code approachCost == null}: the start vertex is free; only the end vertex is chosen to
     * minimise {@code departureCost}.</li>
     * <li>{@code departureCost == null}: the end vertex is free; only the start vertex is chosen to
     * minimise {@code approachCost}.</li>
     * <li>both {@code null}: the search is unconstrained and this is equivalent to
     * {@link #getPath(Graph)}.</li>
     * </ul>
     *
     * <p>
     * Candidate endpoint pairs are tried in ascending order of total off-tour cost
     * ({@code approachCost(a) + departureCost(b)}) until one yields a Hamiltonian path; that path
     * is returned. Because endpoint selection is decoupled from path existence, the single nearest
     * pair may not admit a Hamiltonian path even when another pair does, so this method keeps
     * trying in cost order rather than giving up after the nearest pair. The returned path
     * therefore minimises the off-tour legs <em>among endpoint pairs that admit a Hamiltonian
     * path</em>; it does <em>not</em> minimise the weight of the tour itself, which is a separate,
     * NP-hard optimisation problem.
     *
     * <p>
     * The result is {@link HamiltonianPathSearchResult.Status#PATH_FOUND} for the cheapest feasible
     * endpoint pair, or {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT} when the graph has
     * no Hamiltonian path at all. Ties in total cost are broken deterministically by vertex
     * iteration order. Each candidate pair triggers a full (worst-case exponential) Hamiltonian
     * path search; see the {@code maxEndpointAttempts} overload to bound how many are attempted.
     *
     * @param graph the input graph
     * @param approachCost cost of starting the tour at a given vertex, or {@code null} to leave the
     *        start endpoint free
     * @param departureCost cost of ending the tour at a given vertex, or {@code null} to leave the
     *        end endpoint free
     * @return a {@link HamiltonianPathSearchResult} describing the outcome
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if the graph is empty or not directed/undirected
     */
    public HamiltonianPathSearchResult<V, E> getPathNearEndpoints(
        Graph<V, E> graph, ToDoubleFunction<V> approachCost, ToDoubleFunction<V> departureCost)
    {
        return getPathNearEndpoints(graph, approachCost, departureCost, Integer.MAX_VALUE);
    }

    /**
     * Bounded variant of {@link #getPathNearEndpoints(Graph, ToDoubleFunction, ToDoubleFunction)}
     * that tries at most {@code maxEndpointAttempts} candidate endpoint pairs, in ascending cost
     * order, before giving up.
     *
     * <p>
     * If a feasible endpoint pair is found within the budget its path is returned
     * ({@link HamiltonianPathSearchResult.Status#PATH_FOUND}); if the entire candidate space is
     * exhausted without success the result is
     * {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT}; if the budget is reached before the
     * candidate space is exhausted the result is
     * {@link HamiltonianPathSearchResult.Status#ABORTED}. Use this to bound the number of
     * individually exponential Hamiltonian path searches on graphs where many cheap endpoint pairs
     * may be infeasible.
     *
     * @param graph the input graph
     * @param approachCost cost of starting the tour at a vertex, or {@code null} for a free start
     * @param departureCost cost of ending the tour at a vertex, or {@code null} for a free end
     * @param maxEndpointAttempts the maximum number of candidate endpoint pairs to try; must be
     *        positive
     * @return a {@link HamiltonianPathSearchResult} describing the outcome
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if {@code maxEndpointAttempts} is not positive, or the graph
     *         is empty or not directed/undirected
     */
    public HamiltonianPathSearchResult<V, E> getPathNearEndpoints(
        Graph<V, E> graph, ToDoubleFunction<V> approachCost, ToDoubleFunction<V> departureCost,
        int maxEndpointAttempts)
    {
        Objects.requireNonNull(graph, "graph must not be null");
        if (maxEndpointAttempts <= 0) {
            throw new IllegalArgumentException(
                "maxEndpointAttempts must be positive, got " + maxEndpointAttempts);
        }
        if (approachCost == null && departureCost == null) {
            return getPath(graph);
        }
        // If the graph has no Hamiltonian path at all, no endpoint choice can produce one. This
        // existence check is a single search that short-circuits the otherwise quadratic pair
        // enumeration on hopeless graphs (trying every infeasible pair would run one exponential
        // search per pair). It costs one extra search when a path does exist, which is a sound
        // trade for avoiding the n^2-search blow-up when none does.
        //
        // statesExpanded is accumulated across every internal search so that getStatesExpanded()
        // reflects the total work of this call, not just the last sub-search.
        long totalStates = 0L;
        HamiltonianPathSearchResult<V, E> existence = getPath(graph);
        totalStates += statesExpanded;
        if (existence.getPath().isEmpty()) {
            statesExpanded = totalStates;
            return existence;
        }
        List<V> vertices = new ArrayList<>(graph.vertexSet());
        if (vertices.size() == 1) {
            statesExpanded = totalStates;
            return existence; // the lone vertex is the only possible endpoint
        }

        List<RankedEndpoints<V>> ranked =
            rankEndpointCandidates(vertices, approachCost, departureCost);
        int attempts = 0;
        for (RankedEndpoints<V> candidate : ranked) {
            if (attempts >= maxEndpointAttempts) {
                statesExpanded = totalStates;
                return HamiltonianPathSearchResult.aborted(totalStates);
            }
            attempts++;
            HamiltonianPathSearchResult<V, E> result;
            if (candidate.start != null && candidate.end != null) {
                result = getPathBetween(graph, candidate.start, candidate.end);
            } else if (candidate.start != null) {
                result = getPathFrom(graph, candidate.start);
            } else {
                result = getPathTo(graph, candidate.end);
            }
            totalStates += statesExpanded;
            if (result.getPath().isPresent()) {
                statesExpanded = totalStates;
                return HamiltonianPathSearchResult.found(result.getPath().get(), totalStates);
            }
        }
        statesExpanded = totalStates;
        return HamiltonianPathSearchResult.provenAbsent(totalStates);
    }

    /**
     * Builds the candidate endpoint list for {@link #getPathNearEndpoints}, sorted ascending by
     * total off-tour cost with vertex iteration order as a deterministic tie-breaker. When only
     * one cost function is supplied the candidates are single vertices (the other endpoint is
     * left free); when both are supplied the candidates are ordered vertex pairs with distinct
     * endpoints.
     */
    private List<RankedEndpoints<V>> rankEndpointCandidates(
        List<V> vertices, ToDoubleFunction<V> approachCost, ToDoubleFunction<V> departureCost)
    {
        final int n = vertices.size();
        List<RankedEndpoints<V>> ranked = new ArrayList<>();
        if (approachCost != null && departureCost != null) {
            double[] approach = new double[n];
            double[] departure = new double[n];
            for (int i = 0; i < n; i++) {
                approach[i] = approachCost.applyAsDouble(vertices.get(i));
                departure[i] = departureCost.applyAsDouble(vertices.get(i));
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        ranked.add(
                            new RankedEndpoints<>(
                                vertices.get(i), vertices.get(j), approach[i] + departure[j], i,
                                j));
                    }
                }
            }
        } else if (approachCost != null) {
            for (int i = 0; i < n; i++) {
                ranked.add(
                    new RankedEndpoints<>(
                        vertices.get(i), null, approachCost.applyAsDouble(vertices.get(i)), i, -1));
            }
        } else {
            for (int j = 0; j < n; j++) {
                ranked.add(
                    new RankedEndpoints<>(
                        null, vertices.get(j), departureCost.applyAsDouble(vertices.get(j)), -1,
                        j));
            }
        }
        ranked.sort(
            Comparator.comparingDouble((RankedEndpoints<V> c) -> c.cost)
                .thenComparingInt(c -> c.startIndex).thenComparingInt(c -> c.endIndex));
        return ranked;
    }

    /**
     * Performs a Hamiltonian path search with an upper bound on the number of DFS states the
     * solver may explore. The structural prechecks (connectivity, leaf count, cut vertex
     * degree, bridge tree degree, SCC condensation) run before the limited DFS and are not
     * counted against the budget; graphs they reject return
     * {@link HamiltonianPathSearchResult.Status#PROVEN_ABSENT}.
     *
     * @param graph the input graph
     * @param maxStates the maximum number of DFS states (partial paths) the search is allowed
     *        to explore; must be positive
     * @return a {@link HamiltonianPathSearchResult} describing the outcome
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if {@code maxStates} is not positive or the graph is
     *         empty or not directed/undirected
     */
    public HamiltonianPathSearchResult<V, E> searchWithStateLimit(
        Graph<V, E> graph, long maxStates)
    {
        if (maxStates <= 0L) {
            throw new IllegalArgumentException("maxStates must be positive, got " + maxStates);
        }
        return search(graph, maxStates, null, null);
    }

    /**
     * Core search routine shared by all public entry points. When {@code source} is non-null the
     * path must start there; when {@code target} is non-null the path must end there; either or
     * both may be {@code null} to leave the corresponding endpoint unconstrained. The endpoint
     * constraints only restrict which vertex sequences count as a solution; they never weaken the
     * exactness of the search.
     */
    private HamiltonianPathSearchResult<V, E> search(
        Graph<V, E> graph, long maxStates, V source, V target)
    {
        Objects.requireNonNull(graph, "graph must not be null");
        GraphTests.requireDirectedOrUndirected(graph);
        statesExpanded = 0L;
        aborted = false;
        maxStatesLimit = maxStates;
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

        // A Hamiltonian path on more than one vertex has two distinct endpoints, so identical
        // required endpoints are unsatisfiable.
        if (source != null && source.equals(target)) {
            return HamiltonianPathSearchResult.provenAbsent(0L);
        }

        final boolean directed = graph.getType().isDirected();
        if (!directed && !cheapUndirectedPrechecks(graph)) {
            return HamiltonianPathSearchResult.provenAbsent(statesExpanded);
        }
        if (directed && !cheapDirectedPrechecks(graph)) {
            return HamiltonianPathSearchResult.provenAbsent(statesExpanded);
        }

        VertexToIntegerMapping<V> mapping = Graphs.getVertexToIntegerMapping(graph);
        List<V> indexToVertex = mapping.getIndexList();
        Map<V, Integer> vertexToIndex = mapping.getVertexMap();

        int[][] adjacency = buildAdjacency(graph, indexToVertex, vertexToIndex, directed);

        final int sourceIdx = source == null ? -1 : vertexToIndex.get(source);
        final int targetIdx = target == null ? -1 : vertexToIndex.get(target);

        int[] pathIdx = new int[n];
        boolean[] visited = new boolean[n];
        reachableScratch = new boolean[n];
        reachQueueScratch = new int[n];

        final int startLo = sourceIdx >= 0 ? sourceIdx : 0;
        final int startHi = sourceIdx >= 0 ? sourceIdx : n - 1;
        for (int start = startLo; start <= startHi; start++) {
            if (start == targetIdx) {
                // the required end vertex cannot also be the start vertex (n >= 2)
                continue;
            }
            pathIdx[0] = start;
            visited[start] = true;
            if (extend(adjacency, pathIdx, visited, 1, n, targetIdx)) {
                return HamiltonianPathSearchResult.found(
                    buildResult(graph, indexToVertex, pathIdx), statesExpanded);
            }
            visited[start] = false;
            if (aborted) {
                return HamiltonianPathSearchResult.aborted(statesExpanded);
            }
        }
        return HamiltonianPathSearchResult.provenAbsent(statesExpanded);
    }

    /**
     * Cheap necessary conditions for the existence of a Hamiltonian path in an undirected graph.
     * Returns {@code false} if the graph trivially has no Hamiltonian path, {@code true}
     * otherwise. A {@code true} result does not guarantee a path exists.
     */
    private boolean cheapUndirectedPrechecks(Graph<V, E> graph)
    {
        ConnectivityInspector<V, E> inspector = new ConnectivityInspector<>(graph);
        if (!inspector.isConnected()) {
            return false;
        }
        int leaves = 0;
        for (V v : graph.vertexSet()) {
            if (effectiveUndirectedDegree(graph, v) == 1) {
                leaves++;
                if (leaves > 2) {
                    return false;
                }
            }
        }
        BiconnectivityInspector<V, E> bcc = new BiconnectivityInspector<>(graph);
        return cutVertexBlockDegreeWithinPathBudget(bcc)
            && bridgeTreeDegreeWithinPathBudget(graph, bcc);
    }

    /**
     * Necessary condition: in the block-cut tree of an undirected graph, every cut vertex must
     * belong to at most two biconnected blocks. A cut vertex incident to more than two blocks
     * disconnects the graph into more than two components on removal, but a Hamiltonian path
     * uses at most two edges at any internal vertex, so it cannot weave through more than two
     * such components.
     */
    private boolean cutVertexBlockDegreeWithinPathBudget(BiconnectivityInspector<V, E> bcc)
    {
        for (V cut : bcc.getCutpoints()) {
            if (bcc.getBlocks(cut).size() > 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Necessary condition: in the bridge tree (where 2-edge-connected components are nodes and
     * bridges are edges) every component must have at most two incident bridges. A Hamiltonian
     * path passing through a 2-edge-connected component enters and leaves it at most once, so
     * it can use at most two of the component's incident bridges. This prune catches some
     * graphs the cut-vertex check misses, in particular those where each bridge attaches to a
     * different vertex inside a single component.
     */
    private boolean bridgeTreeDegreeWithinPathBudget(
        Graph<V, E> graph, BiconnectivityInspector<V, E> bcc)
    {
        Set<E> bridges = bcc.getBridges();
        if (bridges.size() <= 2) {
            return true; // with at most 2 bridges, the bridge tree has degree <= 2 at every node
        }
        Set<E> bridgeSet =
            bridges instanceof HashSet ? bridges : new HashSet<>(bridges);
        Graph<V, E> bridgeFree = new MaskSubgraph<>(graph, v -> false, bridgeSet::contains);
        ConnectivityInspector<V, E> components = new ConnectivityInspector<>(bridgeFree);
        List<Set<V>> componentSets = components.connectedSets();
        Map<V, Integer> vertexToComponent = new HashMap<>(graph.vertexSet().size());
        for (int i = 0; i < componentSets.size(); i++) {
            for (V v : componentSets.get(i)) {
                vertexToComponent.put(v, i);
            }
        }
        int[] incident = new int[componentSets.size()];
        for (E bridge : bridgeSet) {
            int cu = vertexToComponent.get(graph.getEdgeSource(bridge));
            int cv = vertexToComponent.get(graph.getEdgeTarget(bridge));
            incident[cu]++;
            if (cu != cv) {
                incident[cv]++;
            }
            if (incident[cu] > 2 || incident[cv] > 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cheap necessary conditions for the existence of a Hamiltonian path in a directed graph.
     * The condensation DAG of strongly connected components must itself admit a Hamiltonian
     * path; otherwise the original graph cannot. Returns {@code true} when this necessary
     * condition is satisfied (a Hamiltonian path may or may not exist in the original) and
     * {@code false} when the condensation rules it out.
     */
    private boolean cheapDirectedPrechecks(Graph<V, E> graph)
    {
        KosarajuStrongConnectivityInspector<V, E> scc =
            new KosarajuStrongConnectivityInspector<>(graph);
        List<Graph<V, E>> components = scc.getStronglyConnectedComponents();
        if (components.size() <= 1) {
            // single SCC: condensation is one vertex, no further pruning possible here
            return true;
        }
        Graph<Graph<V, E>, DefaultEdge> condensation = scc.getCondensation();
        return condensationAdmitsHamiltonianPath(condensation);
    }

    /**
     * Linear-time Hamiltonian path existence test on the SCC condensation DAG. Uses the
     * standard longest-path-in-DAG DP over a topological order; if the longest path has fewer
     * vertices than the condensation does, no Hamiltonian projection exists.
     */
    private boolean condensationAdmitsHamiltonianPath(Graph<Graph<V, E>, DefaultEdge> condensation)
    {
        final int n = condensation.vertexSet().size();
        if (n <= 1) {
            return true;
        }
        Map<Graph<V, E>, Integer> position = new HashMap<>(n);
        List<Graph<V, E>> topo = new ArrayList<>(n);
        new TopologicalOrderIterator<>(condensation).forEachRemaining(c -> {
            position.put(c, topo.size());
            topo.add(c);
        });
        int[] longest = new int[n];
        Arrays.fill(longest, 1);
        int best = 1;
        for (int i = 0; i < n; i++) {
            Graph<V, E> v = topo.get(i);
            for (DefaultEdge e : condensation.incomingEdgesOf(v)) {
                int u = position.get(condensation.getEdgeSource(e));
                if (longest[u] + 1 > longest[i]) {
                    longest[i] = longest[u] + 1;
                }
            }
            if (longest[i] > best) {
                best = longest[i];
                if (best == n) {
                    return true;
                }
            }
        }
        return best == n;
    }

    /**
     * Degree of {@code v} in an undirected graph, ignoring self-loops since they cannot
     * participate in a simple path.
     */
    private int effectiveUndirectedDegree(Graph<V, E> graph, V v)
    {
        int degree = 0;
        for (E e : graph.edgesOf(v)) {
            V other = Graphs.getOppositeVertex(graph, e, v);
            if (!other.equals(v)) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * Recursive DFS extension. Returns {@code true} and leaves {@code pathIdx} filled with a
     * Hamiltonian vertex sequence as soon as one is discovered. Applies reachability pruning
     * and minimum-remaining-values candidate ordering.
     *
     * <p>
     * When {@code targetIdx} is non-negative the search is constrained to paths ending at that
     * vertex: it is withheld from every position except the last, where it becomes the only
     * admissible candidate. This never causes a false negative, because a Hamiltonian path ending
     * at {@code targetIdx} cannot use that vertex anywhere but its final position.
     */
    private boolean extend(
        int[][] adjacency, int[] pathIdx, boolean[] visited, int depth, int n, int targetIdx)
    {
        if (aborted) {
            return false;
        }
        if (maxStatesLimit > 0L && statesExpanded >= maxStatesLimit) {
            aborted = true;
            return false;
        }
        statesExpanded++;
        if (depth == n) {
            return targetIdx < 0 || pathIdx[n - 1] == targetIdx;
        }
        int current = pathIdx[depth - 1];

        int remaining = n - depth;
        if (!allRemainingReachable(adjacency, visited, current, remaining)) {
            return false;
        }

        final boolean placingLast = depth == n - 1;
        int[] neighbours = adjacency[current];
        int[] candidates = new int[neighbours.length];
        int[] onwardDegrees = new int[neighbours.length];
        int k = 0;
        for (int next : neighbours) {
            if (visited[next]) {
                continue;
            }
            if (targetIdx >= 0) {
                // the required end vertex is admissible only at the final position
                if (placingLast ? next != targetIdx : next == targetIdx) {
                    continue;
                }
            }
            candidates[k] = next;
            onwardDegrees[k] = onwardDegree(adjacency, visited, next);
            k++;
        }
        // Insertion sort: ascending by onward degree, with vertex index as tie-breaker
        // (insertion sort is stable, so candidates ordered by graph index already serve as
        // the secondary key).
        for (int i = 1; i < k; i++) {
            int cv = candidates[i];
            int dv = onwardDegrees[i];
            int j = i - 1;
            while (j >= 0 && onwardDegrees[j] > dv) {
                onwardDegrees[j + 1] = onwardDegrees[j];
                candidates[j + 1] = candidates[j];
                j--;
            }
            onwardDegrees[j + 1] = dv;
            candidates[j + 1] = cv;
        }

        for (int i = 0; i < k; i++) {
            int next = candidates[i];
            visited[next] = true;
            pathIdx[depth] = next;
            if (extend(adjacency, pathIdx, visited, depth + 1, n, targetIdx)) {
                return true;
            }
            visited[next] = false;
        }
        return false;
    }

    /**
     * Returns {@code true} when every still-unvisited vertex is reachable from {@code start}
     * through unvisited vertices using the supplied adjacency (which respects edge direction in
     * directed graphs). Returning {@code false} proves the current branch cannot be completed
     * into a Hamiltonian path. The check itself never causes a false negative because Hamiltonian
     * path existence from the current endpoint requires every remaining vertex to be reachable
     * from it.
     */
    private boolean allRemainingReachable(
        int[][] adjacency, boolean[] visited, int start, int remaining)
    {
        if (remaining == 0) {
            return true;
        }
        boolean[] reached = reachableScratch;
        int[] queue = reachQueueScratch;
        Arrays.fill(reached, false);
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        reached[start] = true;
        int found = 0;
        while (head < tail) {
            int u = queue[head++];
            for (int v : adjacency[u]) {
                if (!reached[v] && !visited[v]) {
                    reached[v] = true;
                    queue[tail++] = v;
                    found++;
                    if (found == remaining) {
                        return true;
                    }
                }
            }
        }
        return found == remaining;
    }

    /**
     * Counts the unvisited neighbours of {@code v} reachable in one step along the supplied
     * adjacency.
     */
    private int onwardDegree(int[][] adjacency, boolean[] visited, int v)
    {
        int degree = 0;
        for (int w : adjacency[v]) {
            if (!visited[w]) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * Materialises a found vertex-index sequence as a {@link GraphPath} via
     * {@link HamiltonianPathAlgorithmBase#vertexListToPath}.
     */
    private GraphPath<V, E> buildResult(Graph<V, E> graph, List<V> indexToVertex, int[] pathIdx)
    {
        List<V> vertices = new ArrayList<>(pathIdx.length);
        for (int i : pathIdx) {
            vertices.add(indexToVertex.get(i));
        }
        return vertexListToPath(vertices, graph);
    }

    /**
     * Immutable candidate endpoint descriptor used to rank near-endpoint searches. A {@code null}
     * {@code start} or {@code end} denotes a free endpoint; {@code startIndex} / {@code endIndex}
     * are the vertices' positions in the iteration order (or {@code -1} for a free endpoint) and
     * provide a deterministic tie-breaker when costs are equal.
     *
     * @param <V> the graph vertex type
     */
    private static final class RankedEndpoints<V>
    {
        final V start;
        final V end;
        final double cost;
        final int startIndex;
        final int endIndex;

        RankedEndpoints(V start, V end, double cost, int startIndex, int endIndex)
        {
            this.start = start;
            this.end = end;
            this.cost = cost;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }
}
