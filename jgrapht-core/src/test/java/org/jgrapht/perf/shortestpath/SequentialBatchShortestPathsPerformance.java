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

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.BatchShortestPathAlgorithm.*;
import org.jgrapht.alg.shortestpath.*;
import org.jgrapht.alg.util.*;
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;
import org.jgrapht.util.*;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * JMH benchmark for {@link SequentialBatchShortestPaths}.
 *
 * <p>
 * Compares three modes in the same JVM on the same query set:
 * <ul>
 *   <li>{@code testNaiveLoop} — naive {@code for}-loop calling
 *       {@code new DijkstraShortestPath(graph).getPath(s,t)} per query.</li>
 *   <li>{@code testSequentialBatchUngrouped} — sequential adapter with
 *       {@code groupBySource=false}.</li>
 *   <li>{@code testSequentialBatchGrouped} — sequential adapter with
 *       {@code groupBySource=true}; should win when several queries share a source.</li>
 * </ul>
 *
 * <p>
 * All three are exact and equivalent in result; the benchmark measures wall-clock cost.
 * Cell sizes are bounded so the smallest cell ({@code n=100, queries=64}) runs first
 * and the entire matrix completes in under a minute on a modern laptop.
 *
 * @author Shai Eilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0, jvmArgs = "--illegal-access=permit")
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SequentialBatchShortestPathsPerformance
{

    private static final long SEED = 19L;

    @Benchmark
    public Map<Pair<Integer, Integer>, Double> testNaiveLoop(BatchState state)
    {
        Map<Pair<Integer, Integer>, Double> out = new HashMap<>(state.queries.size());
        for (Pair<Integer, Integer> q : state.queries) {
            GraphPath<Integer, DefaultWeightedEdge> p =
                new DijkstraShortestPath<>(state.graph).getPath(q.getFirst(), q.getSecond());
            out.put(q, p == null ? Double.POSITIVE_INFINITY : p.getWeight());
        }
        return out;
    }

    @Benchmark
    public Map<Pair<Integer, Integer>, Double> testSequentialBatchUngrouped(BatchState state)
    {
        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            new SequentialBatchShortestPaths<>(
                state.graph, DijkstraShortestPath::new, false)
                .getBatchPaths(state.queries);
        return result.asWeightMap();
    }

    @Benchmark
    public Map<Pair<Integer, Integer>, Double> testSequentialBatchGrouped(BatchState state)
    {
        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            new SequentialBatchShortestPaths<>(
                state.graph, DijkstraShortestPath::new, true)
                .getBatchPaths(state.queries);
        return result.asWeightMap();
    }

    @State(Scope.Benchmark)
    public static class BatchState
    {
        @Param({ "100", "200" })
        int n;
        @Param({ "0.1" })
        double p;
        @Param({ "64", "256" })
        int queryCount;
        @Param({ "4" })
        int distinctSources;

        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph;
        List<Pair<Integer, Integer>> queries;

        @Setup(Level.Iteration)
        public void generate()
        {
            Random rng = new Random(SEED);
            graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            graph.setVertexSupplier(SupplierUtil.createIntegerSupplier());
            new GnpRandomGraphGenerator<Integer, DefaultWeightedEdge>(n, p, SEED)
                .generateGraph(graph);
            makeConnected();
            for (DefaultWeightedEdge edge : graph.edgeSet()) {
                graph.setEdgeWeight(edge, 1.0 + rng.nextInt(1000));
            }

            Integer[] vertices = graph.vertexSet().toArray(new Integer[0]);
            Arrays.sort(vertices);
            List<Integer> sources = new ArrayList<>(distinctSources);
            for (int i = 0; i < distinctSources; i++) {
                sources.add(vertices[rng.nextInt(vertices.length)]);
            }
            queries = new ArrayList<>(queryCount);
            for (int i = 0; i < queryCount; i++) {
                Integer s = sources.get(rng.nextInt(sources.size()));
                Integer t = vertices[rng.nextInt(vertices.length)];
                queries.add(Pair.of(s, t));
            }
        }

        private void makeConnected()
        {
            Object[] vertices = graph.vertexSet().toArray();
            for (int i = 0; i < vertices.length - 1; i++) {
                if (!graph.containsEdge((Integer) vertices[i], (Integer) vertices[i + 1])) {
                    graph.addEdge((Integer) vertices[i], (Integer) vertices[i + 1]);
                }
            }
        }
    }
}
