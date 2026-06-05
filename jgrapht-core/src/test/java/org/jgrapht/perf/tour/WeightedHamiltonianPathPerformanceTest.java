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
package org.jgrapht.perf.tour;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.alg.tour.*;
import org.jgrapht.graph.*;
import org.jgrapht.perf.tour.HamiltonianPathPerformanceTest.GraphFamily;
import org.junit.jupiter.api.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * JMH benchmark for the weighted and enumeration Hamiltonian-path solvers:
 * {@link HeldKarpShortestHamiltonianPath} (minimum-weight path-TSP),
 * {@link HeldKarpLongestHamiltonianPath} (maximum-weight), {@link HeldKarpLongestPath} (longest
 * simple path), the endpoint-constrained {@link BacktrackingHamiltonianPath#getPathBetween}, and
 * the lazy {@link HamiltonianPathEnumerator}.
 *
 * <p>
 * The Held-Karp solvers run the full {@code O(n^2 2^n)} subset DP regardless of graph structure, so
 * the benchmarked sizes are kept at {@code n in {8, 12, 16}} (well under the default vertex ceiling
 * of 18) to bound time and memory. The enumeration cell is bounded independently of graph density
 * by stopping after at most {@link #ENUMERATION_CAP} paths, so even a complete graph (which has
 * astronomically many Hamiltonian paths) completes quickly. As with
 * {@link HamiltonianPathPerformanceTest}, run {@link #testSmoke()} first to confirm the wiring,
 * then {@link #testBaseline()} for the bounded cell sweep.
 *
 * @author seilat
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class WeightedHamiltonianPathPerformanceTest
{

    /** Upper bound on paths drawn from the enumerator per measurement, keeping the cell bounded. */
    static final int ENUMERATION_CAP = 50_000;

    @Param({ "8", "12", "16" })
    public int n;

    @Param({ "COMPLETE", "SPARSE" })
    public GraphFamily family;

    Graph<Integer, DefaultEdge> graph;

    @Setup(Level.Trial)
    public void buildGraph()
    {
        graph = GraphBuilders.build(family, n);
    }

    @Benchmark
    public HamiltonianPathSearchResult<Integer, DefaultEdge> shortestHeldKarp()
    {
        return new HeldKarpShortestHamiltonianPath<Integer, DefaultEdge>().getPath(graph);
    }

    @Benchmark
    public HamiltonianPathSearchResult<Integer, DefaultEdge> longestHeldKarp()
    {
        return new HeldKarpLongestHamiltonianPath<Integer, DefaultEdge>().getPath(graph);
    }

    @Benchmark
    public GraphPath<Integer, DefaultEdge> longestSimplePath()
    {
        return new HeldKarpLongestPath<Integer, DefaultEdge>().getPath(graph);
    }

    @Benchmark
    public HamiltonianPathSearchResult<Integer, DefaultEdge> backtrackingBetween()
    {
        return new BacktrackingHamiltonianPath<Integer, DefaultEdge>()
            .getPathBetween(graph, 0, n - 1);
    }

    @Benchmark
    public long enumerateCapped()
    {
        long count = 0L;
        Iterator<GraphPath<Integer, DefaultEdge>> it =
            new HamiltonianPathEnumerator<>(graph).iterator();
        while (it.hasNext() && count < ENUMERATION_CAP) {
            it.next();
            count++;
        }
        return count;
    }

    /**
     * Smoke driver: one family, one size, a single warmup and measurement iteration. Run this
     * before {@link #testBaseline()} to confirm the wiring builds and fires. Runtime is a few
     * seconds.
     */
    @Test
    public void testSmoke()
        throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(".*" + WeightedHamiltonianPathPerformanceTest.class.getSimpleName() + ".*")
            .param("n", "8")
            .param("family", "COMPLETE")
            .mode(Mode.AverageTime).timeUnit(TimeUnit.MILLISECONDS)
            .warmupIterations(1).warmupTime(TimeValue.seconds(1))
            .measurementIterations(1).measurementTime(TimeValue.seconds(1))
            .forks(1).shouldFailOnError(true).shouldDoGC(true)
            .jvmArgsAppend(
                "--add-exports", "org.jgrapht.core/org.jgrapht.perf.tour.jmh_generated=ALL-UNNAMED",
                "--add-opens", "org.jgrapht.core/org.jgrapht.perf.tour=ALL-UNNAMED")
            .build();
        new Runner(opt).run();
    }

    /**
     * Baseline driver: covers both graph families at {@code n in {8, 12, 16}}. Each cell is bounded
     * (the Held-Karp DP by {@code n <= 16}, the enumeration by {@link #ENUMERATION_CAP}); total
     * wall time on commodity hardware is in the low minutes.
     */
    @Test
    public void testBaseline()
        throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(".*" + WeightedHamiltonianPathPerformanceTest.class.getSimpleName() + ".*")
            .mode(Mode.AverageTime).timeUnit(TimeUnit.MILLISECONDS)
            .warmupIterations(1).warmupTime(TimeValue.seconds(1))
            .measurementIterations(2).measurementTime(TimeValue.seconds(1))
            .forks(1).shouldFailOnError(true).shouldDoGC(true)
            .jvmArgsAppend(
                "--add-exports", "org.jgrapht.core/org.jgrapht.perf.tour.jmh_generated=ALL-UNNAMED",
                "--add-opens", "org.jgrapht.core/org.jgrapht.perf.tour=ALL-UNNAMED")
            .build();
        new Runner(opt).run();
    }
}
