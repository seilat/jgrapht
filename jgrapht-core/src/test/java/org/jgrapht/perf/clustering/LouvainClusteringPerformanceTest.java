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
package org.jgrapht.perf.clustering;

import org.jgrapht.*;
import org.jgrapht.alg.clustering.*;
import org.jgrapht.alg.interfaces.ClusteringAlgorithm.*;
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;
import org.jgrapht.graph.builder.*;
import org.jgrapht.util.*;
import org.junit.jupiter.api.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * JMH benchmark comparing {@link LouvainClustering} against the other modularity-oriented
 * community-detection algorithms in the package ({@link LabelPropagationClustering} and
 * {@link GreedyModularityAlgorithm}) on planted-partition graphs of increasing size.
 *
 * <p>
 * Cells are kept deliberately small and ordered smallest-first so the suite self-bounds; raise the
 * {@code groups}/{@code groupSize} parameters locally for a heavier sweep.
 *
 * @author seilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class LouvainClusteringPerformanceTest
{
    /**
     * In-process JMH launcher with short, self-bounded warmup/measurement so the benchmark stays
     * within the fast-test budget. Run with {@code -Dtest=LouvainClusteringPerformanceTest}.
     *
     * @throws RunnerException if the benchmark harness fails
     */
    @Test
    public void runBenchmark()
        throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(".*" + LouvainClusteringPerformanceTest.class.getSimpleName() + ".*")
            .forks(0).warmupIterations(2).warmupTime(TimeValue.seconds(1))
            .measurementIterations(3).measurementTime(TimeValue.seconds(1))
            .shouldFailOnError(true).build();
        new Runner(opt).run();
    }

    @Benchmark
    public Clustering<Integer> louvain(ClusteringState state)
    {
        return new LouvainClustering<>(state.graph, new Random(state.seed)).getClustering();
    }

    @Benchmark
    public Clustering<Integer> leiden(ClusteringState state)
    {
        return new LeidenClustering<>(state.graph, new Random(state.seed)).getClustering();
    }

    @Benchmark
    public Clustering<Integer> labelPropagation(ClusteringState state)
    {
        return new LabelPropagationClustering<>(state.graph, new Random(state.seed)).getClustering();
    }

    @Benchmark
    public Clustering<Integer> greedyModularity(ClusteringState state)
    {
        return new GreedyModularityAlgorithm<>(state.graph).getClustering();
    }

    /**
     * Benchmark state: a freshly generated planted-partition graph per iteration.
     */
    @State(Scope.Benchmark)
    public static class ClusteringState
    {
        @Param({ "5", "20" })
        int groups;
        @Param({ "25" })
        int groupSize;
        @Param({ "0.4" })
        double intraProbability;
        @Param({ "0.02" })
        double interProbability;

        final long seed = 42L;
        Graph<Integer, DefaultEdge> graph;

        @Setup(Level.Iteration)
        public void generate()
        {
            graph = GraphTypeBuilder
                .undirected().allowingMultipleEdges(false).allowingSelfLoops(false).weighted(false)
                .edgeSupplier(SupplierUtil.DEFAULT_EDGE_SUPPLIER)
                .vertexSupplier(SupplierUtil.createIntegerSupplier()).buildGraph();
            new PlantedPartitionGraphGenerator<Integer, DefaultEdge>(
                groups, groupSize, intraProbability, interProbability, seed).generateGraph(graph);
        }
    }
}
