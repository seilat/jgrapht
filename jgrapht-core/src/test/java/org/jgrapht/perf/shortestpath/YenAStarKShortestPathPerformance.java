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
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.EppsteinKShortestPath;
import org.jgrapht.alg.shortestpath.YenAStarKShortestPath;
import org.jgrapht.alg.shortestpath.YenKShortestPath;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.generate.GnpRandomGraphGenerator;
import org.jgrapht.generate.GraphGenerator;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.util.CollectionUtil;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark comparing three exact $k$-shortest-paths algorithms on the same workload:
 * <ul>
 *   <li>{@link YenKShortestPath} &mdash; the standard Dijkstra-based Yen implementation.</li>
 *   <li>{@link YenAStarKShortestPath} &mdash; Yen with A* (reverse-distance heuristic) on the
 *       spur step, no bounded-pruned layer.</li>
 *   <li>{@link EppsteinKShortestPath} &mdash; Eppstein's $O(m + n \log n + k \log k)$ algorithm
 *       (note: Eppstein returns all walks/paths, not only loopless ones, so the returned path
 *       <em>set</em> differs from Yen for graphs with cycles; the benchmark still measures
 *       wall-clock cost to produce $k$ paths).</li>
 * </ul>
 *
 * <p>
 * Yen and Yen+A* are exact for the same definition of "k loopless shortest paths" and return the
 * same ordered weight sequence on graphs with non-negative weights. Eppstein solves a slightly
 * different (broader) problem and is included as the standard fast-asymptotics reference. To
 * compare against the bounded-pruned variant as well, see
 * {@link BoundedPrunedYenKShortestPathPerformance}.
 *
 * @author Shai Eilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0, jvmArgs = "--illegal-access=permit")
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class YenAStarKShortestPathPerformance
{
    private static final Random RANDOM = new Random(19L);

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
            new ArrayList<>(state.numberOfQueries);
        for (Pair<Integer, Integer> query : state.queries) {
            int source = query.getFirst();
            int target = query.getSecond();
            result.add(algorithm.getPaths(source, target, state.k));
        }
        return result;
    }

    @State(Scope.Benchmark)
    public static class YenAStarState
    {
        @Param({ "100", "300" })
        int n;
        @Param({ "0.1", "0.3" })
        double p;
        @Param({ "20", "100" })
        int k;
        @Param({ "10" })
        int numberOfQueries;

        GraphGenerator<Integer, DefaultWeightedEdge, Integer> generator;
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph;
        List<Pair<Integer, Integer>> queries;

        @Setup(Level.Iteration)
        public void generateGraph()
        {
            generator = new GnpRandomGraphGenerator<>(n, p);
            graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            graph.setVertexSupplier(SupplierUtil.createIntegerSupplier());
            generator.generateGraph(graph);
            makeConnected(graph);
            addEdgeWeights(graph);
            queries = selectQueries();
        }

        private List<Pair<Integer, Integer>> selectQueries()
        {
            Set<Pair<Integer, Integer>> result =
                CollectionUtil.newHashSetWithExpectedSize(numberOfQueries);
            Object[] vertices = graph.vertexSet().toArray();
            while (result.size() < numberOfQueries) {
                int sourceIndex = (int) (Math.random() * vertices.length);
                int targetIndex = (int) (Math.random() * vertices.length);
                while (sourceIndex == targetIndex) {
                    targetIndex = (int) (Math.random() * vertices.length);
                }
                Integer source = (Integer) vertices[sourceIndex];
                Integer target = (Integer) vertices[targetIndex];
                result.add(Pair.of(source, target));
            }
            return new ArrayList<>(result);
        }

        private void makeConnected(Graph<Integer, DefaultWeightedEdge> graph)
        {
            Object[] vertices = graph.vertexSet().toArray();
            for (int i = 0; i < vertices.length - 1; i++) {
                if (!graph.containsEdge((Integer) vertices[i], (Integer) vertices[i + 1])) {
                    graph.addEdge((Integer) vertices[i], (Integer) vertices[i + 1]);
                }
            }
        }

        private void addEdgeWeights(Graph<Integer, DefaultWeightedEdge> graph)
        {
            for (DefaultWeightedEdge edge : graph.edgeSet()) {
                double weight = 1.0 + Math.abs(RANDOM.nextInt(1000));
                graph.setEdgeWeight(edge, weight);
            }
        }
    }
}
