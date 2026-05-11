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
import org.jgrapht.alg.shortestpath.*;
import org.jgrapht.graph.*;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * JMH benchmark for {@link AllDirectedPaths} preprocessing on workloads that target the
 * source-sandwich prune in {@code edgeMinDistancesBackwards}.
 *
 * <p>
 * Two cell families:
 * <ul>
 *   <li><b>Win case ({@code testWinCase}).</b> Forward chain of {@code chainLen} vertices
 *       {@code 0 → 1 → … → chainLen-1 = T} plus a {@code gardenSize}-vertex
 *       source-disconnected garden whose every vertex has an edge into {@code T}. Simple-paths
 *       mode, {@code maxPathLength = chainLen + 5}. The garden is reachable backwards from
 *       {@code T} but no garden vertex is reachable forwards from the source, so without the
 *       source-sandwich prune the backward sweep marks every garden vertex.</li>
 *   <li><b>Loss case ({@code testLossCase}).</b> Small dense strongly-connected digraph where
 *       {@code F = B = V}, so the new forward BFS is pure overhead and the sandwich condition
 *       never drops anything. Bounds the regression cost.</li>
 * </ul>
 *
 * @author Shai Eilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0, jvmArgs = "--illegal-access=permit")
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 8, time = 2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AllDirectedPathsSandwichPrunePerformance
{

    @Benchmark
    public List<GraphPath<Integer, DefaultEdge>> testWinCase(WinState state)
    {
        return state.algorithm.getAllPaths(state.source, state.target, true, state.maxPathLength);
    }

    @Benchmark
    public List<GraphPath<Integer, DefaultEdge>> testLossCase(LossState state)
    {
        return state.algorithm.getAllPaths(state.source, state.target, true, state.maxPathLength);
    }

    @State(Scope.Benchmark)
    public static class WinState
    {
        @Param({ "500", "1000", "2000" })
        int gardenSize;
        @Param({ "20" })
        int chainLen;

        DefaultDirectedGraph<Integer, DefaultEdge> graph;
        AllDirectedPaths<Integer, DefaultEdge> algorithm;
        Integer source;
        Integer target;
        int maxPathLength;

        @Setup(Level.Trial)
        public void buildGraph()
        {
            graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            for (int i = 0; i < chainLen + gardenSize; i++) {
                graph.addVertex(i);
            }
            for (int i = 0; i < chainLen - 1; i++) {
                graph.addEdge(i, i + 1);
            }
            int targetVertex = chainLen - 1;
            for (int g = 0; g < gardenSize; g++) {
                int gardenVertex = chainLen + g;
                graph.addEdge(gardenVertex, targetVertex);
            }
            algorithm = new AllDirectedPaths<>(graph);
            source = 0;
            target = targetVertex;
            maxPathLength = chainLen + 5;
        }
    }

    @State(Scope.Benchmark)
    public static class LossState
    {
        @Param({ "20" })
        int n;
        @Param({ "3" })
        int maxPathLength;

        DefaultDirectedGraph<Integer, DefaultEdge> graph;
        AllDirectedPaths<Integer, DefaultEdge> algorithm;
        Integer source;
        Integer target;

        @Setup(Level.Trial)
        public void buildGraph()
        {
            graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            for (int i = 0; i < n; i++) {
                graph.addVertex(i);
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        graph.addEdge(i, j);
                    }
                }
            }
            algorithm = new AllDirectedPaths<>(graph);
            source = 0;
            target = n - 1;
        }
    }
}
