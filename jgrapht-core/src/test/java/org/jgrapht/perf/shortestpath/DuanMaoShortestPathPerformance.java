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
package org.jgrapht.perf.shortestpath;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.alg.shortestpath.*;
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;
import org.jgrapht.util.*;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Benchmark comparing the prototype {@link DuanMaoShortestPath} (the {@code O(m log^{2/3} n)}
 * "sorting barrier" algorithm) against jgrapht's {@link DijkstraShortestPath} on directed,
 * non-negatively weighted sparse graphs. The benchmark exists to make the practical gap visible: the
 * theoretical improvement only materializes at astronomically large {@code n}, so for every feasible
 * size Dijkstra is expected to win comfortably.
 *
 * <p>
 * Indicative best-of-5 wall-clock results on directed sparse graphs (average out-degree 4, full
 * single-source run, one developer machine; numbers are illustrative, not a formal JMH run):
 *
 * <pre>
 * vertices   edges      dijkstra(ms)   duanmao(ms)    ratio
 * 1000       4987       1.81           5.27           2.9x
 * 5000       24986      5.10           14.52          2.8x
 * 20000      99977      37.76          61.29          1.6x
 * 50000      249989     124.37         156.26         1.3x
 * 100000     499984     281.15         457.92         1.6x
 * 200000     999986     805.95         1028.17        1.3x
 * </pre>
 *
 * The prototype is consistently slower with no crossover in sight. This is expected: the simplified
 * {@code O(log)} data structure used here is asymptotically worse than the paper's {@code O(t)}
 * block list, and even the ideal {@code log^{2/3} n} vs {@code log n} gain is only ~2-3x at a billion
 * vertices, far too small to overcome the algorithm's large constant factors.
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class DuanMaoShortestPathPerformance
{
    @Benchmark
    public ShortestPathAlgorithm.SingleSourcePaths<Integer, DefaultWeightedEdge> testDuanMao(
        GnpDirectedState data)
    {
        return new DuanMaoShortestPath<>(data.graph).getPaths(0);
    }

    @Benchmark
    public ShortestPathAlgorithm.SingleSourcePaths<Integer, DefaultWeightedEdge> testDijkstra(
        GnpDirectedState data)
    {
        return new DijkstraShortestPath<>(data.graph).getPaths(0);
    }

    @State(Scope.Benchmark)
    public static class GnpDirectedState
    {
        @Param({ "1000", "10000", "100000" })
        int numOfVertices;

        /** Average out-degree; kept small so the graphs stay sparse. */
        @Param({ "4", "8" })
        int avgDegree;

        DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph;

        @Setup(Level.Trial)
        public void generateGraph()
        {
            graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            graph.setVertexSupplier(SupplierUtil.createIntegerSupplier());

            double p = (double) avgDegree / numOfVertices;
            new GnpRandomGraphGenerator<Integer, DefaultWeightedEdge>(numOfVertices, p, 17L, false)
                .generateGraph(graph);
            // guarantee connectivity from the source along a path
            Object[] vs = graph.vertexSet().toArray();
            for (int i = 0; i < vs.length - 1; i++) {
                if (!graph.containsEdge((Integer) vs[i], (Integer) vs[i + 1])) {
                    graph.addEdge((Integer) vs[i], (Integer) vs[i + 1]);
                }
            }
            Random rng = new Random(42);
            for (DefaultWeightedEdge e : graph.edgeSet()) {
                graph.setEdgeWeight(e, rng.nextDouble() * 10.0);
            }
        }
    }
}
