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
import org.jgrapht.util.*;

import java.util.*;

/**
 * Lazily enumerates every <a href="https://en.wikipedia.org/wiki/Hamiltonian_path">Hamiltonian
 * path</a> of a directed or undirected graph.
 *
 * <p>
 * The enumerator is {@link Iterable}: each {@link #iterator()} performs an independent depth-first
 * backtracking traversal that produces Hamiltonian paths one at a time, so callers may inspect,
 * filter, or stop early without materialising the (potentially exponential) full set. For an
 * undirected graph a path and its reverse denote the same Hamiltonian path and are emitted only
 * once (the orientation whose first vertex precedes its last in the internal vertex ordering is
 * chosen); for a directed graph the two orientations are distinct paths and both are emitted when
 * both exist.
 *
 * <p>
 * The traversal is the classic backtracking enumeration (Rubin, F., "A Search Procedure for
 * Hamilton Paths and Circuits", JACM 21(4), 1974), generating solutions in the spirit of Knuth,
 * "The Art of Computer Programming", Vol. 4A, &sect;7.2.1.2 (backtrack programming). Forward
 * reachability pruning (a partial path is abandoned once some unvisited vertex becomes unreachable
 * from the current endpoint) cuts dead branches without changing the set of paths produced.
 *
 * <p>
 * Because the number of Hamiltonian paths can be exponential in the number of vertices (the
 * complete graph {@code K_n} has {@code n!/2} undirected Hamiltonian paths), callers should treat a
 * full enumeration as exponential work and prefer {@link Iterator}-based early termination. The
 * enumerator itself uses only {@code O(n)} working memory per iterator and imposes no vertex-count
 * ceiling.
 *
 * <p>
 * Self-loops are ignored and parallel edges between the same pair of vertices collapse to a single
 * step: the enumeration ranges over distinct vertex orderings, not over edge multiplicities, and
 * each emitted {@link GraphPath} uses an arbitrary representative edge for each step via
 * {@link Graph#getEdge(Object, Object)}.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author seilat
 */
public class HamiltonianPathEnumerator<V, E>
    implements Iterable<GraphPath<V, E>>
{

    private final Graph<V, E> graph;
    private final int n;
    private final boolean directed;
    private final List<V> indexToVertex;
    private final int[][] adjacency;

    /**
     * Constructs an enumerator over the Hamiltonian paths of {@code graph}.
     *
     * @param graph the input graph; must be directed or undirected and contain at least one vertex
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if {@code graph} has no vertices or is neither directed nor
     *         undirected
     */
    public HamiltonianPathEnumerator(Graph<V, E> graph)
    {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        GraphTests.requireDirectedOrUndirected(graph);
        if (graph.vertexSet().isEmpty()) {
            throw new IllegalArgumentException("Graph contains no vertices");
        }
        this.n = graph.vertexSet().size();
        this.directed = graph.getType().isDirected();
        VertexToIntegerMapping<V> mapping = Graphs.getVertexToIntegerMapping(graph);
        this.indexToVertex = mapping.getIndexList();
        this.adjacency = buildAdjacency(mapping.getVertexMap());
    }

    /**
     * Returns an iterator that lazily produces each Hamiltonian path of the graph exactly once.
     * Successive calls return independent iterators.
     *
     * @return a lazy iterator over the graph's Hamiltonian paths
     */
    @Override
    public Iterator<GraphPath<V, E>> iterator()
    {
        return new PathIterator();
    }

    /**
     * Counts the Hamiltonian paths of the graph by exhausting a fresh enumeration. This runs in
     * time proportional to the number of paths (which may be exponential) but uses only
     * {@code O(n)} memory; prefer {@link #iterator()} with early termination when a full count is
     * not required.
     *
     * @return the number of Hamiltonian paths (undirected paths counted once)
     */
    public long count()
    {
        long count = 0L;
        Iterator<GraphPath<V, E>> it = iterator();
        while (it.hasNext()) {
            it.next();
            count++;
        }
        return count;
    }

    private int[][] buildAdjacency(Map<V, Integer> vertexToIndex)
    {
        int[][] adj = new int[n][];
        for (int u = 0; u < n; u++) {
            V uVertex = indexToVertex.get(u);
            Set<Integer> neighbours = new LinkedHashSet<>();
            Iterable<E> edges = directed ? graph.outgoingEdgesOf(uVertex) : graph.edgesOf(uVertex);
            for (E e : edges) {
                V other = directed
                    ? graph.getEdgeTarget(e) : Graphs.getOppositeVertex(graph, e, uVertex);
                if (!other.equals(uVertex)) {
                    neighbours.add(vertexToIndex.get(other));
                }
            }
            int[] row = new int[neighbours.size()];
            int idx = 0;
            for (int v : neighbours) {
                row[idx++] = v;
            }
            adj[u] = row;
        }
        return adj;
    }

    /**
     * Stateful depth-first backtracking iterator. The {@code cursor} array records, per path
     * position, the next candidate index to try, so the traversal can be resumed across
     * {@link #next()} calls.
     */
    private final class PathIterator
        implements Iterator<GraphPath<V, E>>
    {
        private final int[] pathIdx = new int[n];
        private final boolean[] visited = new boolean[n];
        private final int[] cursor = new int[n + 1];
        private final boolean[] reachedBuffer = new boolean[n];
        private final int[] queueBuffer = new int[n];
        private int len;
        private int unvisitedCount = n;
        private boolean exhausted;
        private GraphPath<V, E> nextPath;

        PathIterator()
        {
            this.len = 0;
            this.cursor[0] = 0;
        }

        @Override
        public boolean hasNext()
        {
            if (nextPath == null && !exhausted) {
                nextPath = computeNext();
            }
            return nextPath != null;
        }

        @Override
        public GraphPath<V, E> next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException("no more Hamiltonian paths");
            }
            GraphPath<V, E> result = nextPath;
            nextPath = null;
            return result;
        }

        private GraphPath<V, E> computeNext()
        {
            while (true) {
                if (len == n) {
                    boolean canonical = directed || pathIdx[0] <= pathIdx[n - 1];
                    GraphPath<V, E> complete = canonical ? snapshot() : null;
                    len--;
                    visited[pathIdx[len]] = false;
                    unvisitedCount++;
                    if (complete != null) {
                        return complete;
                    }
                    continue;
                }
                if (len == 0) {
                    if (cursor[0] >= n) {
                        exhausted = true;
                        return null;
                    }
                    int start = cursor[0]++;
                    visited[start] = true;
                    unvisitedCount--;
                    pathIdx[0] = start;
                    len = 1;
                    cursor[1] = 0;
                    if (n > 1 && !canReachAllUnvisited(start)) {
                        visited[start] = false;
                        unvisitedCount++;
                        len = 0;
                    }
                    continue;
                }
                if (!extendOnce()) {
                    len--;
                    visited[pathIdx[len]] = false;
                    unvisitedCount++;
                }
            }
        }

        /**
         * Attempts to place one more vertex at the current position from the neighbours of the
         * current endpoint, applying reachability pruning. Returns {@code true} and advances
         * {@code len} on success, {@code false} when no admissible candidate remains.
         */
        private boolean extendOnce()
        {
            int[] neighbours = adjacency[pathIdx[len - 1]];
            while (cursor[len] < neighbours.length) {
                int u = neighbours[cursor[len]++];
                if (visited[u]) {
                    continue;
                }
                visited[u] = true;
                unvisitedCount--;
                // skip the reachability check at the final position: there is nothing left to
                // reach, so the check would vacuously pass
                if (len + 1 < n && !canReachAllUnvisited(u)) {
                    visited[u] = false;
                    unvisitedCount++;
                    continue;
                }
                pathIdx[len] = u;
                len++;
                cursor[len] = 0;
                return true;
            }
            return false;
        }

        /**
         * Returns {@code true} when every currently unvisited vertex is reachable from {@code start}
         * through unvisited vertices. {@code start} is assumed already marked visited.
         */
        private boolean canReachAllUnvisited(int start)
        {
            // start is already marked visited, so unvisitedCount is exactly the number of
            // vertices that must still be reached
            int remaining = unvisitedCount;
            if (remaining == 0) {
                return true;
            }
            Arrays.fill(reachedBuffer, false);
            int head = 0;
            int tail = 0;
            queueBuffer[tail++] = start;
            reachedBuffer[start] = true;
            int found = 0;
            while (head < tail) {
                int x = queueBuffer[head++];
                for (int y : adjacency[x]) {
                    if (!reachedBuffer[y] && !visited[y]) {
                        reachedBuffer[y] = true;
                        queueBuffer[tail++] = y;
                        found++;
                        if (found == remaining) {
                            return true;
                        }
                    }
                }
            }
            return found == remaining;
        }

        private GraphPath<V, E> snapshot()
        {
            List<V> vertices = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                vertices.add(indexToVertex.get(pathIdx[i]));
            }
            if (n == 1) {
                V only = vertices.get(0);
                return new GraphWalk<>(
                    graph, only, only, vertices, Collections.emptyList(), 0d);
            }
            List<E> edges = new ArrayList<>(n - 1);
            double weight = 0d;
            for (int i = 1; i < n; i++) {
                E edge = graph.getEdge(vertices.get(i - 1), vertices.get(i));
                edges.add(edge);
                weight += graph.getEdgeWeight(edge);
            }
            return new GraphWalk<>(graph, vertices.get(0), vertices.get(n - 1), vertices, edges,
                weight);
        }
    }
}
