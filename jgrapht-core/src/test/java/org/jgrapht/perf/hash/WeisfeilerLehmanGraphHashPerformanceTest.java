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
package org.jgrapht.perf.hash;

import org.jgrapht.*;
import org.jgrapht.alg.hash.*;
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
 * JMH benchmark for {@link WeisfeilerLehmanGraphHash} measuring how the cost of
 * {@link WeisfeilerLehmanGraphHash#getHash() getHash} scales with the number of refinement
 * iterations on sparse and dense random graphs.
 *
 * <p>
 * Cells are kept deliberately small and ordered smallest-first (fewest iterations, sparsest graph)
 * so the suite self-bounds within the fast-test budget; raise {@code vertices}, {@code degree} or
 * {@code iterations} locally for a heavier sweep.
 *
 * @author seilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class WeisfeilerLehmanGraphHashPerformanceTest
{
    /**
     * In-process JMH launcher with short, self-bounded warmup/measurement so the benchmark stays
     * within the fast-test budget. Run with {@code -Dtest=WeisfeilerLehmanGraphHashPerformanceTest}.
     *
     * @throws RunnerException if the benchmark harness fails
     */
    @Test
    public void runBenchmark()
        throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(".*" + WeisfeilerLehmanGraphHashPerformanceTest.class.getSimpleName() + ".*")
            .forks(0).warmupIterations(2).warmupTime(TimeValue.seconds(1))
            .measurementIterations(3).measurementTime(TimeValue.seconds(1))
            .shouldFailOnError(true).build();
        new Runner(opt).run();
    }

    @Benchmark
    public String hash(HashState state)
    {
        return new WeisfeilerLehmanGraphHash<>(state.graph, state.iterations).getHash();
    }

    /**
     * Benchmark state: a random graph of fixed size and the requested average degree, regenerated
     * per iteration with a fixed seed for repeatability.
     */
    @State(Scope.Benchmark)
    public static class HashState
    {
        @Param({ "1", "3", "5" })
        int iterations;
        @Param({ "500" })
        int vertices;
        // average degree: 4 = sparse, 32 = dense
        @Param({ "4", "32" })
        int degree;

        final long seed = 42L;
        Graph<Integer, DefaultEdge> graph;

        @Setup(Level.Iteration)
        public void generate()
        {
            graph = GraphTypeBuilder
                .undirected().allowingMultipleEdges(false).allowingSelfLoops(false).weighted(false)
                .edgeSupplier(SupplierUtil.DEFAULT_EDGE_SUPPLIER)
                .vertexSupplier(SupplierUtil.createIntegerSupplier()).buildGraph();
            int edges = Math.max(0, (vertices * degree) / 2);
            new GnmRandomGraphGenerator<Integer, DefaultEdge>(vertices, edges, seed)
                .generateGraph(graph);
        }
    }
}
