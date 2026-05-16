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
package org.jgrapht.perf.shortestpath;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.EppsteinKShortestPath;
import org.jgrapht.alg.shortestpath.YenAStarKShortestPath;
import org.jgrapht.alg.shortestpath.YenKShortestPath;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.generate.GnpRandomGraphGenerator;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.util.SupplierUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark comparing three exact $k$-shortest-paths algorithms on a fixed set of graph
 * families:
 * <ul>
 *   <li>{@link YenKShortestPath} &mdash; the standard Dijkstra-based Yen implementation.</li>
 *   <li>{@link YenAStarKShortestPath} &mdash; Yen with A* (reverse-distance heuristic) on the
 *       spur step, no bounded-pruned layer.</li>
 *   <li>{@link EppsteinKShortestPath} &mdash; Eppstein's $O(m + n \log n + k \log k)$ algorithm
 *       (note: Eppstein returns the $k$ shortest walks, not only loopless paths, so its result
 *       set differs from Yen on graphs with cycles; the benchmark still measures wall-clock cost
 *       to produce $k$ paths on the same input).</li>
 * </ul>
 *
 * <h3>Graph families</h3>
 *
 * <ul>
 *   <li>{@code GNP_SPARSE}: $G_{n,0.1}$ random digraph with a chain backbone for connectivity.
 *       Cyclic; tests "random sparse" workloads.</li>
 *   <li>{@code GNP_DENSE}: $G_{n,0.3}$ random digraph with a chain backbone. Cyclic; tests dense
 *       workloads where Yen's per-spur Dijkstra/A* matters most.</li>
 *   <li>{@code LAYERED_DAG}: layered DAG with $\sim n$ vertices, configurable width and layers.
 *       Strong forward progress &mdash; A*'s sweet spot. No cycles, so all three algorithms
 *       return the same path set.</li>
 *   <li>{@code GRID}: directed grid where every edge advances either one column or one row
 *       toward the sink. $\sqrt{n} \times \sqrt{n}$ vertices. Classical A* benchmark with a
 *       Manhattan-like admissible heuristic. No cycles.</li>
 * </ul>
 *
 * <p>
 * To compare against the bounded-pruned variant as well, see
 * {@link BoundedPrunedYenKShortestPathPerformance}.
 *
 * @author Shai Eilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0, jvmArgs = "--illegal-access=permit")
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class YenAStarKShortestPathPerformance
{
    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> testYenKShortestPath(
        YenAStarState state)
    {
        return computeResult(new YenKShortestPath<>(state.graph), state);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> testYenAStarKShortestPath(
        YenAStarState state)
    {
        return computeResult(new YenAStarKShortestPath<>(state.graph), state);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> testEppsteinKShortestPath(
        YenAStarState state)
    {
        return computeResult(new EppsteinKShortestPath<>(state.graph), state);
    }

    private List<List<GraphPath<Integer, DefaultWeightedEdge>>> computeResult(
        KShortestPathAlgorithm<Integer, DefaultWeightedEdge> algorithm, YenAStarState state)
    {
        List<List<GraphPath<Integer, DefaultWeightedEdge>>> result =
            new ArrayList<>(state.queries.size());
        for (Pair<Integer, Integer> query : state.queries) {
            result.add(algorithm.getPaths(query.getFirst(), query.getSecond(), state.k));
        }
        return result;
    }

    public enum Family
    {
        GNP_SPARSE,
        GNP_DENSE,
        LAYERED_DAG,
        GRID
    }

    @State(Scope.Benchmark)
    public static class YenAStarState
    {
        // Deterministic seed for the per-iteration graph so all three benchmarks see the same
        // structure on a given (family, n, k) cell.
        private static final long GRAPH_SEED = 19L;
        private static final long QUERY_SEED = 23L;

        @Param({ "GNP_SPARSE", "GNP_DENSE", "LAYERED_DAG", "GRID" })
        Family family;

        @Param({ "200" })
        int n;

        @Param({ "20", "100" })
        int k;

        @Param({ "10" })
        int numberOfQueries;

        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph;
        List<Pair<Integer, Integer>> queries;

        @Setup(Level.Iteration)
        public void generateGraph()
        {
            Random rnd = new Random(GRAPH_SEED);
            graph = buildGraph(family, n, rnd);
            queries = selectQueries(family);
        }

        private static SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> buildGraph(
            Family family, int n, Random rnd)
        {
            switch (family) {
            case GNP_SPARSE:
                return gnp(n, 0.1, rnd);
            case GNP_DENSE:
                return gnp(n, 0.3, rnd);
            case LAYERED_DAG:
                return layeredDag(layersFor(n), widthFor(n), rnd);
            case GRID:
                int side = (int) Math.round(Math.sqrt(n));
                return grid(side, side, rnd);
            default:
                throw new IllegalArgumentException("Unknown family: " + family);
            }
        }

        /** ~sqrt(n) layers, ~sqrt(n) wide -- total ~n+2 vertices. */
        private static int layersFor(int n)
        {
            return Math.max(2, (int) Math.round(Math.sqrt(n)));
        }

        private static int widthFor(int n)
        {
            return Math.max(2, (int) Math.round(Math.sqrt(n)));
        }

        private List<Pair<Integer, Integer>> selectQueries(Family family)
        {
            Random rnd = new Random(QUERY_SEED);
            List<Integer> verts = new ArrayList<>(graph.vertexSet());
            verts.sort(Integer::compareTo);
            int vN = verts.size();
            List<Pair<Integer, Integer>> result = new ArrayList<>(numberOfQueries);
            while (result.size() < numberOfQueries) {
                int aIdx = rnd.nextInt(vN);
                int bIdx = rnd.nextInt(vN);
                if (aIdx == bIdx) {
                    continue;
                }
                int sourceIdx;
                int targetIdx;
                if (family == Family.LAYERED_DAG || family == Family.GRID) {
                    // For DAGs, ensure source comes before target in topological order
                    // (vertex index is also topological order in these generators).
                    sourceIdx = Math.min(aIdx, bIdx);
                    targetIdx = Math.max(aIdx, bIdx);
                } else {
                    sourceIdx = aIdx;
                    targetIdx = bIdx;
                }
                result.add(Pair.of(verts.get(sourceIdx), verts.get(targetIdx)));
            }
            return result;
        }

        // ----------------------------------------------------------------------------------------
        // Graph generators (inlined here so the perf class has no dependency on the test sources)
        // ----------------------------------------------------------------------------------------

        private static SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> gnp(
            int n, double p, Random rnd)
        {
            SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> g =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            g.setVertexSupplier(SupplierUtil.createIntegerSupplier());
            new GnpRandomGraphGenerator<Integer, DefaultWeightedEdge>(n, p, rnd.nextLong())
                .generateGraph(g);
            // Chain backbone for connectivity along the natural vertex order.
            List<Integer> verts = new ArrayList<>(g.vertexSet());
            verts.sort(Integer::compareTo);
            for (int i = 0; i + 1 < verts.size(); i++) {
                if (!g.containsEdge(verts.get(i), verts.get(i + 1))) {
                    g.addEdge(verts.get(i), verts.get(i + 1));
                }
            }
            addWeights(g, rnd);
            return g;
        }

        private static SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> layeredDag(
            int layers, int width, Random rnd)
        {
            SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> g =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            int src = 0;
            g.addVertex(src);
            int next = 1;
            int[][] layerVerts = new int[layers][width];
            for (int l = 0; l < layers; l++) {
                for (int j = 0; j < width; j++) {
                    layerVerts[l][j] = next;
                    g.addVertex(next++);
                }
            }
            int sink = next;
            g.addVertex(sink);
            // src -> first layer
            for (int j = 0; j < width; j++) {
                Graphs.addEdge(g, src, layerVerts[0][j], 1.0 + rnd.nextInt(10));
            }
            // between layers; ~50% edge probability
            for (int l = 0; l + 1 < layers; l++) {
                for (int j = 0; j < width; j++) {
                    for (int j2 = 0; j2 < width; j2++) {
                        if (rnd.nextDouble() < 0.5) {
                            Graphs.addEdge(
                                g, layerVerts[l][j], layerVerts[l + 1][j2], 1.0 + rnd.nextInt(10));
                        }
                    }
                }
            }
            // last layer -> sink
            for (int j = 0; j < width; j++) {
                Graphs.addEdge(g, layerVerts[layers - 1][j], sink, 1.0 + rnd.nextInt(10));
            }
            return g;
        }

        private static SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> grid(
            int rows, int cols, Random rnd)
        {
            SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> g =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            for (int i = 0; i < rows * cols; i++) {
                g.addVertex(i);
            }
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int v = r * cols + c;
                    if (c + 1 < cols) {
                        Graphs.addEdge(g, v, v + 1, 1.0 + rnd.nextInt(10));
                    }
                    if (r + 1 < rows) {
                        Graphs.addEdge(g, v, v + cols, 1.0 + rnd.nextInt(10));
                    }
                }
            }
            return g;
        }

        private static void addWeights(
            SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> g, Random rnd)
        {
            for (DefaultWeightedEdge e : g.edgeSet()) {
                g.setEdgeWeight(e, 1.0 + rnd.nextInt(1000));
            }
        }
    }
}
