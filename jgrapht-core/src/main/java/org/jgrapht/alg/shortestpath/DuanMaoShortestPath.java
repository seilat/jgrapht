/*
 * (C) Copyright 2026-2026, by Contributors.
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

import org.jgrapht.*;
import org.jgrapht.alg.util.*;

import java.util.*;

/**
 * Prototype implementation of the deterministic {@code O(m log^{2/3} n)} single-source shortest
 * path algorithm for directed graphs with non-negative real edge weights, due to Duan, Mao, Mao,
 * Shu and Yin, <i>"Breaking the Sorting Barrier for Directed Single-Source Shortest Paths"</i>
 * (arXiv:2504.17033, 2025).
 *
 * <p>
 * The algorithm merges Dijkstra's and Bellman-Ford's ideas through a recursive partitioning of the
 * vertex set. The recursion ({@code BMSSP}, "bounded multi-source shortest path") repeatedly shrinks
 * the Dijkstra "frontier" using a {@code FindPivots} step (a bounded Bellman-Ford relaxation that
 * keeps only the roots of large shortest-path subtrees) and a partial-sorting data structure, so the
 * expensive per-vertex work only applies to a {@code 1/log^{Ω(1)} n} fraction of frontier vertices.
 * This breaks the {@code O(m + n log n)} "sorting barrier" of Dijkstra's algorithm on sparse graphs.
 *
 * <p>
 * <b>Status / caveats.</b> This is a faithful but un-tuned prototype intended for correctness checks
 * and for empirically comparing against {@link DijkstraShortestPath}; it is <em>not</em> a
 * production replacement:
 * <ul>
 * <li>The partial-sorting data structure of Lemma 3.3 (a block-based linked list giving amortized
 * {@code O(t)} inserts) is replaced here by a simpler balanced-tree-backed structure with the same
 * semantics but {@code O(log)} per-operation cost. The asymptotic {@code O(m log^{2/3} n)} bound is
 * therefore <em>not</em> realized by this prototype.</li>
 * <li>The constant-in/out-degree graph transformation from the paper's preliminaries is skipped; the
 * algorithm runs directly on the input graph. This only affects the running-time bound, not
 * correctness.</li>
 * <li>The improvement only matters for astronomically large sparse graphs; for every realistic input
 * a well-tuned Dijkstra (such as jgrapht's {@link DijkstraShortestPath}) is faster in wall-clock
 * time because of the large hidden constant factors. See the benchmark
 * {@code org.jgrapht.perf.shortestpath.DuanMaoShortestPathPerformance}.</li>
 * </ul>
 *
 * <p>
 * To satisfy the paper's "total order of paths" assumption (Assumption 2.1) in the presence of
 * zero-weight edges and equal-length paths, paths are compared by the composite key
 * {@code (length, number-of-hops, vertex-id)} rather than by length alone, which makes all path keys
 * distinct and keeps the predecessor structure a tree.
 *
 * <p>
 * The algorithm only supports non-negative edge weights. For graphs with negative weights use
 * {@link BellmanFordShortestPath}.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 */
public class DuanMaoShortestPath<V, E>
    extends BaseShortestPathAlgorithm<V, E>
{
    private static final String NEGATIVE_EDGE_WEIGHT_NOT_ALLOWED =
        "Negative edge weight not allowed";

    /* ---- immutable graph view (CSR) ---- */
    private final int n;
    private final List<V> vertexList;
    private final Map<V, Integer> index;
    private int[] adjStart;
    private int[] adjTo;
    private double[] adjW;
    private Object[] adjE;

    /* ---- algorithm parameters (set per run) ---- */
    private int k;
    private int t;
    private int topLevel;

    /* ---- mutable algorithm state (set per run) ---- */
    private double[] dist;
    private int[] hops;
    private int[] predV;
    private Object[] predE;

    /**
     * Constructs a new instance of the algorithm for a given graph.
     *
     * @param graph the graph
     */
    public DuanMaoShortestPath(Graph<V, E> graph)
    {
        super(graph);
        this.n = graph.vertexSet().size();
        this.vertexList = new ArrayList<>(graph.vertexSet());
        this.index = new HashMap<>(n);
        for (int i = 0; i < n; i++) {
            index.put(vertexList.get(i), i);
        }
        buildCsr();
    }

    private void buildCsr()
    {
        adjStart = new int[n + 1];
        for (int u = 0; u < n; u++) {
            adjStart[u + 1] = adjStart[u] + graph.outDegreeOf(vertexList.get(u));
        }
        int m = adjStart[n];
        adjTo = new int[m];
        adjW = new double[m];
        adjE = new Object[m];
        int[] pos = Arrays.copyOf(adjStart, n + 1);
        for (int u = 0; u < n; u++) {
            V uv = vertexList.get(u);
            for (E e : graph.outgoingEdgesOf(uv)) {
                double w = graph.getEdgeWeight(e);
                if (w < 0.0) {
                    throw new IllegalArgumentException(NEGATIVE_EDGE_WEIGHT_NOT_ALLOWED);
                }
                V ov = Graphs.getOppositeVertex(graph, e, uv);
                int j = pos[u]++;
                adjTo[j] = index.get(ov);
                adjW[j] = w;
                adjE[j] = e;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GraphPath<V, E> getPath(V source, V sink)
    {
        if (!graph.containsVertex(source)) {
            throw new IllegalArgumentException(GRAPH_MUST_CONTAIN_THE_SOURCE_VERTEX);
        }
        if (!graph.containsVertex(sink)) {
            throw new IllegalArgumentException(GRAPH_MUST_CONTAIN_THE_SINK_VERTEX);
        }
        return getPaths(source).getPath(sink);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SingleSourcePaths<V, E> getPaths(V source)
    {
        if (!graph.containsVertex(source)) {
            throw new IllegalArgumentException(GRAPH_MUST_CONTAIN_THE_SOURCE_VERTEX);
        }

        dist = new double[n];
        hops = new int[n];
        predV = new int[n];
        predE = new Object[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(hops, Integer.MAX_VALUE);
        Arrays.fill(predV, -1);
        int s = index.get(source);
        dist[s] = 0.0;
        hops[s] = 0;

        if (n > 1) {
            double logn = Math.log(n) / Math.log(2.0);
            k = Math.max(1, (int) Math.floor(Math.cbrt(logn)));
            t = Math.max(1, (int) Math.floor(Math.pow(logn, 2.0 / 3.0)));
            topLevel = Math.max(1, (int) Math.ceil(logn / t));

            List<Integer> rootSet = new ArrayList<>(1);
            rootSet.add(s);
            bmssp(topLevel, Key.INF, rootSet);
        }

        Map<V, Pair<Double, E>> map = new HashMap<>(n);
        for (int v = 0; v < n; v++) {
            if (!Double.isInfinite(dist[v])) {
                @SuppressWarnings("unchecked") E e = (E) predE[v];
                map.put(vertexList.get(v), Pair.of(dist[v], e));
            }
        }
        return new TreeSingleSourcePathsImpl<>(graph, source, map);
    }

    /* =============================== key / ordering =============================== */

    /**
     * A total order on path labels: lexicographic by (length, number of hops, vertex id). This
     * realizes Assumption 2.1 of the paper, making all path keys distinct even with zero-weight or
     * equal-length paths.
     */
    private static final class Key
        implements Comparable<Key>
    {
        static final Key INF =
            new Key(Double.POSITIVE_INFINITY, Integer.MAX_VALUE, Integer.MAX_VALUE);

        final double d;
        final int hops;
        final int v;

        Key(double d, int hops, int v)
        {
            this.d = d;
            this.hops = hops;
            this.v = v;
        }

        @Override
        public int compareTo(Key o)
        {
            int c = Double.compare(d, o.d);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(hops, o.hops);
            if (c != 0) {
                return c;
            }
            return Integer.compare(v, o.v);
        }
    }

    private Key currentKey(int v)
    {
        return new Key(dist[v], hops[v], v);
    }

    private static Key min(Key a, Key b)
    {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /* =============================== core recursion =============================== */

    /**
     * Bounded multi-source shortest path (Algorithm 3 of the paper).
     */
    private Result bmssp(int level, Key b, List<Integer> s)
    {
        if (level == 0) {
            return baseCase(b, s.get(0));
        }

        Pivots pivots = findPivots(b, s);
        List<Integer> p = pivots.p;
        Set<Integer> w = pivots.w;

        long m = pow2Capped((long) (level - 1) * t);
        BlockDS d = new BlockDS(m, b);
        Key bPrime0 = b;
        for (int x : p) {
            Key kx = currentKey(x);
            d.insert(x, kx);
            bPrime0 = min(bPrime0, kx);
        }

        long sizeLimit = mulCapped(k, pow2Capped((long) level * t));
        Set<Integer> u = new LinkedHashSet<>();
        // Vertices whose outgoing edges have already been relaxed at this recursion level. Every
        // vertex returned in a child's U is complete (final distance), so it never needs to be
        // relaxed more than once per level; deduplicating enforces the disjointness of the children
        // result sets (Remark 3.8 of the paper) and guarantees termination of this loop.
        Set<Integer> relaxedAtThisLevel = new HashSet<>();
        Key lastBPrime = bPrime0;

        while (u.size() < sizeLimit && !d.isEmpty()) {
            BlockDS.Pulled pulled = d.pull();
            Key bi = pulled.separator;
            List<Integer> si = pulled.keys;

            Result sub = bmssp(level - 1, bi, si);
            Key biPrime = sub.bPrime;
            lastBPrime = biPrime;
            u.addAll(sub.u);

            List<Integer> kBatch = new ArrayList<>();
            for (int uu : sub.u) {
                if (!relaxedAtThisLevel.add(uu)) {
                    continue; // already relaxed at this level; nothing new can come of it
                }
                for (int j = adjStart[uu]; j < adjStart[uu + 1]; j++) {
                    int v = adjTo[j];
                    Key cand = new Key(dist[uu] + adjW[j], hops[uu] + 1, v);
                    if (cand.compareTo(currentKey(v)) <= 0) {
                        if (cand.compareTo(currentKey(v)) < 0) {
                            dist[v] = cand.d;
                            hops[v] = cand.hops;
                            predV[v] = uu;
                            predE[v] = adjE[j];
                        }
                        // route the (now current) label of v by where it falls
                        if (cand.compareTo(bi) >= 0 && cand.compareTo(b) < 0) {
                            d.insert(v, currentKey(v));
                        } else if (cand.compareTo(biPrime) >= 0 && cand.compareTo(bi) < 0) {
                            kBatch.add(v);
                        }
                    }
                }
            }
            // batch prepend K together with the leftover pulled keys still in [biPrime, bi)
            for (int x : si) {
                Key kx = currentKey(x);
                if (kx.compareTo(biPrime) >= 0 && kx.compareTo(bi) < 0) {
                    kBatch.add(x);
                }
            }
            d.batchPrepend(this, kBatch);
        }

        Key bPrime = min(lastBPrime, b);
        for (int x : w) {
            if (currentKey(x).compareTo(bPrime) < 0) {
                u.add(x);
            }
        }
        return new Result(bPrime, u);
    }

    /**
     * Base case of BMSSP (Algorithm 2): a bounded mini-Dijkstra from the single complete vertex
     * {@code x}, collecting up to {@code k + 1} closest vertices.
     */
    private Result baseCase(Key b, int x)
    {
        Set<Integer> u0 = new LinkedHashSet<>();
        Set<Integer> inHeap = new HashSet<>();
        PriorityQueue<Key> heap = new PriorityQueue<>();
        heap.add(currentKey(x));
        inHeap.add(x);

        while (!heap.isEmpty() && u0.size() < k + 1) {
            Key top = heap.poll();
            int uu = top.v;
            if (u0.contains(uu) || top.compareTo(currentKey(uu)) != 0) {
                continue; // already settled, or a stale (superseded) heap entry
            }
            inHeap.remove(uu);
            u0.add(uu);
            for (int j = adjStart[uu]; j < adjStart[uu + 1]; j++) {
                int v = adjTo[j];
                Key cand = new Key(dist[uu] + adjW[j], hops[uu] + 1, v);
                // Per Remark 3.4 the relaxation uses "<=" so that an edge already relaxed by an
                // ancestor's FindPivots step is re-discovered into this ball.
                if (cand.compareTo(b) < 0 && cand.compareTo(currentKey(v)) <= 0
                    && !u0.contains(v))
                {
                    boolean improved = cand.compareTo(currentKey(v)) < 0;
                    if (improved) {
                        dist[v] = cand.d;
                        hops[v] = cand.hops;
                        predV[v] = uu;
                        predE[v] = adjE[j];
                    }
                    if (!inHeap.contains(v)) {
                        heap.add(currentKey(v));
                        inHeap.add(v);
                    } else if (improved) {
                        heap.add(currentKey(v));
                    }
                }
            }
        }

        if (u0.size() <= k) {
            return new Result(b, u0);
        }
        Key bPrime = null;
        for (int v : u0) {
            Key kv = currentKey(v);
            if (bPrime == null || kv.compareTo(bPrime) > 0) {
                bPrime = kv;
            }
        }
        Set<Integer> u = new LinkedHashSet<>();
        for (int v : u0) {
            if (currentKey(v).compareTo(bPrime) < 0) {
                u.add(v);
            }
        }
        return new Result(bPrime, u);
    }

    /**
     * Finding pivots (Algorithm 1): relax {@code k} Bellman-Ford steps from {@code s} bounded by
     * {@code b}; the pivots are the roots in {@code s} of predecessor-forest subtrees of size
     * {@code >= k}.
     */
    private Pivots findPivots(Key b, List<Integer> s)
    {
        Set<Integer> w = new LinkedHashSet<>(s);
        List<Integer> wPrev = new ArrayList<>(s);

        for (int i = 1; i <= k; i++) {
            Set<Integer> wi = new LinkedHashSet<>();
            for (int uu : wPrev) {
                for (int j = adjStart[uu]; j < adjStart[uu + 1]; j++) {
                    int v = adjTo[j];
                    Key cand = new Key(dist[uu] + adjW[j], hops[uu] + 1, v);
                    if (cand.compareTo(currentKey(v)) <= 0) {
                        if (cand.compareTo(currentKey(v)) < 0) {
                            dist[v] = cand.d;
                            hops[v] = cand.hops;
                            predV[v] = uu;
                            predE[v] = adjE[j];
                        }
                        if (cand.compareTo(b) < 0) {
                            wi.add(v);
                        }
                    }
                }
            }
            w.addAll(wi);
            wPrev = new ArrayList<>(wi);
            if (w.size() > (long) k * s.size()) {
                return new Pivots(new ArrayList<>(s), w);
            }
        }

        // Build the predecessor forest restricted to W and find the roots in S whose subtree has at
        // least k vertices. The predecessor relation is a forest by construction.
        Map<Integer, List<Integer>> children = new HashMap<>();
        Set<Integer> hasParentInW = new HashSet<>();
        for (int v : w) {
            int pu = predV[v];
            if (pu >= 0 && pu != v && w.contains(pu)) {
                children.computeIfAbsent(pu, kk -> new ArrayList<>()).add(v);
                hasParentInW.add(v);
            }
        }
        List<Integer> p = new ArrayList<>();
        for (int root : s) {
            if (hasParentInW.contains(root)) {
                continue; // not a root of a tree in the forest
            }
            if (subtreeSize(root, children) >= k) {
                p.add(root);
            }
        }
        return new Pivots(p, w);
    }

    private int subtreeSize(int root, Map<Integer, List<Integer>> children)
    {
        // iterative DFS, capped at k (we only need the >= k test)
        int count = 0;
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty() && count < k) {
            int x = stack.pop();
            count++;
            List<Integer> ch = children.get(x);
            if (ch != null) {
                for (int c : ch) {
                    stack.push(c);
                }
            }
        }
        return count;
    }

    /* =============================== helpers =============================== */

    private static long pow2Capped(long exp)
    {
        if (exp >= 62) {
            return Long.MAX_VALUE / 4;
        }
        return 1L << exp;
    }

    private long mulCapped(long a, long b)
    {
        long cap = (long) (n + 1);
        if (b >= cap) {
            return Long.MAX_VALUE / 4;
        }
        return a * b;
    }

    private static final class Result
    {
        final Key bPrime;
        final Set<Integer> u;

        Result(Key bPrime, Set<Integer> u)
        {
            this.bPrime = bPrime;
            this.u = u;
        }
    }

    private static final class Pivots
    {
        final List<Integer> p;
        final Set<Integer> w;

        Pivots(List<Integer> p, Set<Integer> w)
        {
            this.p = p;
            this.w = w;
        }
    }

    /**
     * Simplified stand-in for the partial-sorting data structure of Lemma 3.3. It supports
     * {@code Insert} (with decrease-key semantics, keeping the smallest key per vertex),
     * {@code BatchPrepend} and {@code Pull} (extract up to {@code M} smallest keys together with a
     * separating upper bound). Backed by a balanced tree, so each operation is {@code O(log size)}
     * rather than the amortized {@code O(t)} of the paper's block list.
     */
    private static final class BlockDS
    {
        private final long m;
        private final Key b;
        private final TreeMap<Key, Integer> tree = new TreeMap<>();
        private final Map<Integer, Key> present = new HashMap<>();

        BlockDS(long m, Key b)
        {
            this.m = m;
            this.b = b;
        }

        boolean isEmpty()
        {
            return tree.isEmpty();
        }

        void insert(int vertex, Key key)
        {
            Key existing = present.get(vertex);
            if (existing != null) {
                if (key.compareTo(existing) >= 0) {
                    return;
                }
                tree.remove(existing);
            }
            tree.put(key, vertex);
            present.put(vertex, key);
        }

        void batchPrepend(DuanMaoShortestPath<?, ?> owner, List<Integer> vertices)
        {
            for (int v : vertices) {
                insert(v, owner.currentKey(v));
            }
        }

        Pulled pull()
        {
            List<Integer> result = new ArrayList<>();
            while (result.size() < m && !tree.isEmpty()) {
                Map.Entry<Key, Integer> e = tree.pollFirstEntry();
                present.remove(e.getValue());
                result.add(e.getValue());
            }
            Key separator = tree.isEmpty() ? b : tree.firstKey();
            return new Pulled(separator, result);
        }

        static final class Pulled
        {
            final Key separator;
            final List<Integer> keys;

            Pulled(Key separator, List<Integer> keys)
            {
                this.separator = separator;
                this.keys = keys;
            }
        }
    }
}
