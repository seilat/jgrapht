/*
 * (C) Copyright 2026-2026, by Shai Eilat and Contributors.
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
package org.jgrapht.alg.shortestpath;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.GraphWalk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Yen's $k$ shortest loopless paths algorithm with A* used for the inner spur shortest-path
 * computations.
 *
 * <p>
 * Behaviour is identical to {@link YenKShortestPath}: the returned ordered sequence of path
 * weights matches it exactly on graphs with non-negative edge weights. The only difference is the
 * spur step. Standard Yen runs a Dijkstra under banning to compute each spur shortest path; this
 * class instead runs A* with an admissible heuristic
 *
 * <pre>
 *   h(v) = d(v, sink) on the original graph
 * </pre>
 *
 * obtained by a single Dijkstra from {@code sink} on the edge-reversed input graph. Because
 * removing vertices/edges can only increase shortest-path distance, these reverse distances stay
 * valid lower bounds under any subsequent bans &mdash; so the heuristic is admissible for every
 * spur subproblem.
 *
 * <p>
 * Edge weights must be non-negative (a precondition of both Dijkstra and A* with an admissible
 * non-negative heuristic).
 *
 * <h3>What this class is</h3>
 *
 * <ul>
 *   <li>A drop-in alternative to {@link YenKShortestPath} when the underlying graph has a
 *       meaningful "distance-to-sink" lower bound &mdash; e.g. dense graphs or large $k$, where
 *       A*'s goal-directed pruning visits substantially fewer vertices per spur query than
 *       Dijkstra.</li>
 *   <li>The non-bounded sibling of {@link BoundedPrunedYenKShortestPath}: it isolates the
 *       speedup contributed by the A* spur back-end alone, without the deferred-task /
 *       lower-bound-certificate machinery of the bounded variant. If both classes are faster
 *       than the standard implementation on a given workload, comparing the two tells you how
 *       much of the speedup comes from the heuristic alone versus from the deferred-task
 *       layer.</li>
 * </ul>
 *
 * <h3>What this class does NOT claim</h3>
 *
 * <ul>
 *   <li>No improved worst-case complexity over standard Yen: $O(k\,n\,(m + n \log n))$ remains
 *       the upper bound. The benefit, when it appears, is per-query: A* expands fewer vertices
 *       than Dijkstra on the same masked subgraph.</li>
 *   <li>No path validator hook. {@link YenKShortestPath} supports a {@code PathValidator}; this
 *       class does not. If validation is required, use the standard implementation.</li>
 * </ul>
 *
 * <h3>References</h3>
 *
 * <ul>
 *   <li>Yen, J. Y. (1971). Finding the k shortest loopless paths in a network.
 *       <i>Management Science</i>, 17(11), 712&ndash;716.</li>
 *   <li>Hart, P. E., Nilsson, N. J., &amp; Raphael, B. (1968). A formal basis for the heuristic
 *       determination of minimum cost paths. <i>IEEE Transactions on Systems Science and
 *       Cybernetics</i>, 4(2), 100&ndash;107. The A* algorithm whose admissibility property
 *       this class relies on.</li>
 * </ul>
 *
 * @param <V> graph vertex type
 * @param <E> graph edge type
 * @see YenKShortestPath
 * @see BoundedPrunedYenKShortestPath
 * @see AStarSpurEngine
 */
public class YenAStarKShortestPath<V, E>
    implements KShortestPathAlgorithm<V, E>
{
    private final Graph<V, E> graph;
    private final AStarSpurEngine<V, E> engine;

    /**
     * Constructs an instance of the algorithm for the given {@code graph}.
     *
     * @param graph the input graph (must not be {@code null})
     */
    public YenAStarKShortestPath(Graph<V, E> graph)
    {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null!");
        this.engine = new AStarSpurEngine<>();
    }

    @Override
    public List<GraphPath<V, E>> getPaths(V source, V sink, int k)
    {
        if (k < 0) {
            throw new IllegalArgumentException("k should be positive");
        }
        if (!graph.containsVertex(source)) {
            throw new IllegalArgumentException("Graph should contain source vertex!");
        }
        if (!graph.containsVertex(sink)) {
            throw new IllegalArgumentException("Graph should contain sink vertex!");
        }
        engine.resetCounters();

        if (k == 0) {
            return new ArrayList<>();
        }

        Map<V, Double> reverseDistances = computeReverseDistances(sink);

        GraphPath<V, E> first = engine.findPath(
            graph, source, sink, java.util.Collections.emptySet(),
            java.util.Collections.emptySet(), reverseDistances);
        if (first == null) {
            return new ArrayList<>();
        }

        return runYenLoop(first, k, reverseDistances);
    }

    private Map<V, Double> computeReverseDistances(V sink)
    {
        // Single Dijkstra on the edge-reversed graph from sink. Gives h(v) = d(v, sink) on the
        // original graph. Unreachable vertices map to POSITIVE_INFINITY in the SingleSourcePaths;
        // we keep them out of the result map so the engine falls back to 0.0 for them (still
        // admissible).
        SingleSourcePaths<V, E> ssp =
            new DijkstraShortestPath<>(new EdgeReversedGraph<>(graph)).getPaths(sink);
        Map<V, Double> dist = new HashMap<>();
        for (V v : graph.vertexSet()) {
            double d = ssp.getWeight(v);
            if (!Double.isInfinite(d)) {
                dist.put(v, d);
            }
        }
        return dist;
    }

    private List<GraphPath<V, E>> runYenLoop(
        GraphPath<V, E> first, int k, Map<V, Double> reverseDistances)
    {
        List<GraphPath<V, E>> accepted = new ArrayList<>();
        // For each accepted path, the index of its first deviation vertex in its parent path.
        // The first path "deviates" at the source (index 0). For subsequent paths, only spur
        // indices >= the path's deviation index are eligible.
        List<Integer> firstDeviationIndex = new ArrayList<>();

        // Stable ordinal for tiebreaking ties on equal weights, matching standard Yen's behaviour
        // of breaking ties by insertion order.
        long[] ord = { 0L };
        PriorityQueue<Candidate> candHeap = new PriorityQueue<>();
        // Dedup: avoid pushing the same vertex sequence twice (can happen when multiple parents
        // share a prefix that leads to the same spur).
        Set<List<V>> seenCandidates = new HashSet<>();

        accepted.add(first);
        firstDeviationIndex.add(0);
        seenCandidates.add(first.getVertexList());
        generateCandidates(
            first, 0, firstDeviationIndex, accepted, reverseDistances, candHeap, seenCandidates,
            ord);

        while (accepted.size() < k && !candHeap.isEmpty()) {
            Candidate next = candHeap.poll();
            accepted.add(next.path);
            firstDeviationIndex.add(next.firstDeviationIndex);
            int newIdx = accepted.size() - 1;
            generateCandidates(
                next.path, newIdx, firstDeviationIndex, accepted, reverseDistances, candHeap,
                seenCandidates, ord);
        }

        return accepted;
    }

    /**
     * Eagerly enumerate every legal spur from {@code path}, materialize each into a candidate, and
     * push it into the candidate heap. Mirrors the textbook Yen spur step exactly.
     */
    private void generateCandidates(
        GraphPath<V, E> path, int pathIndex, List<Integer> firstDeviationIndex,
        List<GraphPath<V, E>> accepted, Map<V, Double> reverseDistances,
        PriorityQueue<Candidate> candHeap, Set<List<V>> seenCandidates, long[] ord)
    {
        List<V> vertices = path.getVertexList();
        List<E> edges = path.getEdgeList();
        int n = vertices.size();
        if (n < 2) {
            return;
        }
        int devIndex = firstDeviationIndex.get(pathIndex);

        Set<V> bannedVerticesPrefix = new LinkedHashSet<>();
        double rootCost = 0.0;
        for (int i = 0; i <= n - 2; i++) {
            if (i >= devIndex) {
                V spurNode = vertices.get(i);
                Set<E> bannedEdges = computeYenBannedEdges(vertices, i, accepted);
                GraphPath<V, E> spur = engine.findPath(
                    graph, spurNode, path.getEndVertex(), bannedVerticesPrefix, bannedEdges,
                    reverseDistances);
                if (spur != null) {
                    Candidate cand =
                        stitchCandidate(path, i, spur, rootCost, ord[0]);
                    if (seenCandidates.add(cand.path.getVertexList())) {
                        candHeap.offer(cand);
                        ord[0]++;
                    }
                }
            }
            bannedVerticesPrefix.add(vertices.get(i));
            rootCost += graph.getEdgeWeight(edges.get(i));
        }
    }

    private Candidate stitchCandidate(
        GraphPath<V, E> parent, int spurIndex, GraphPath<V, E> spur, double rootCost, long ordinal)
    {
        List<V> pv = parent.getVertexList();
        List<E> pe = parent.getEdgeList();
        List<V> spurV = spur.getVertexList();
        List<E> spurE = spur.getEdgeList();
        List<V> candVerts = new ArrayList<>(spurIndex + spurV.size());
        List<E> candEdges = new ArrayList<>(spurIndex + spurE.size());
        for (int i = 0; i < spurIndex; i++) {
            candVerts.add(pv.get(i));
            candEdges.add(pe.get(i));
        }
        candVerts.addAll(spurV);
        candEdges.addAll(spurE);
        double cost = rootCost + spur.getWeight();
        GraphPath<V, E> candidate = new GraphWalk<>(
            graph, parent.getStartVertex(), parent.getEndVertex(), candVerts, candEdges, cost);
        return new Candidate(candidate, cost, spurIndex, ordinal);
    }

    /**
     * Yen rule: when generating a spur from index {@code spurIndex} of {@code parentVertices}, ban
     * the next edge of every previously accepted path whose first {@code spurIndex+1} vertices
     * match {@code parentVertices}.
     */
    private Set<E> computeYenBannedEdges(
        List<V> parentVertices, int spurIndex, List<GraphPath<V, E>> accepted)
    {
        Set<E> bannedEdges = new HashSet<>();
        for (GraphPath<V, E> other : accepted) {
            List<V> ov = other.getVertexList();
            if (ov.size() <= spurIndex + 1) {
                continue;
            }
            boolean prefixMatch = true;
            for (int i = 0; i <= spurIndex; i++) {
                if (!parentVertices.get(i).equals(ov.get(i))) {
                    prefixMatch = false;
                    break;
                }
            }
            if (prefixMatch) {
                bannedEdges.add(other.getEdgeList().get(spurIndex));
            }
        }
        return bannedEdges;
    }

    private final class Candidate
        implements Comparable<Candidate>
    {
        final GraphPath<V, E> path;
        final double cost;
        final int firstDeviationIndex;
        final long ord;

        Candidate(GraphPath<V, E> path, double cost, int firstDeviationIndex, long ord)
        {
            this.path = path;
            this.cost = cost;
            this.firstDeviationIndex = firstDeviationIndex;
            this.ord = ord;
        }

        @Override
        public int compareTo(Candidate o)
        {
            int c = Double.compare(this.cost, o.cost);
            if (c != 0) {
                return c;
            }
            return Long.compare(this.ord, o.ord);
        }
    }
}
