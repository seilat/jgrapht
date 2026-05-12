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
package org.jgrapht.perf.shortestpath.osm;

import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;
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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Andorra-OSM benchmark for {@link AllDirectedPaths} non-simple mode (i.e. enumerating
 * length-bounded walks). The full Andorra SCC is too large for non-simple-mode walk
 * enumeration; we instead carve out a BFS-radius ball around a random anchor (~80–250
 * vertices) and enumerate length-bounded walks inside that subgraph.
 *
 * <p>
 * Running this bench on master and on {@code alldirectedpaths-skip-unused-visited}
 * (PR #1341) or {@code alldirectedpaths-source-sandwich-prune} yields the before/after
 * speedup for the {@code ArrayDeque}/unused-visited fix (#1341) and the optional
 * {@code useSandwichPrune} reachability prune (C3, dev-list hold).
 *
 * @author Shai Eilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0, jvmArgs = {
    "--add-opens=org.jgrapht.core/org.jgrapht.perf.shortestpath.osm=ALL-UNNAMED",
    "--add-opens=org.jgrapht.core/org.jgrapht.perf.shortestpath.osm.jmh_generated=ALL-UNNAMED",
    "--add-exports=org.jgrapht.core/org.jgrapht.perf.shortestpath.osm=ALL-UNNAMED",
    "--add-exports=org.jgrapht.core/org.jgrapht.perf.shortestpath.osm.jmh_generated=ALL-UNNAMED"
})
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AndorraAllDirectedPathsNonSimpleBench
{
    @Benchmark
    public List<GraphPath<Integer, DefaultWeightedEdge>> allDirectedPathsNonSimple(
        AndorraAdpState s)
    {
        AllDirectedPaths<Integer, DefaultWeightedEdge> adp =
            new AllDirectedPaths<>(s.subgraph);
        return adp.getAllPaths(
            Collections.singleton(s.source), Collections.singleton(s.sink), false, s.maxPathLen);
    }

    @State(Scope.Benchmark)
    public static class AndorraAdpState
    {
        @Param({ "6" })
        int bfsRadius;
        @Param({ "6" })
        int maxPathLen;

        AndorraGraphLoader.AndorraData data;
        AsSubgraph<Integer, DefaultWeightedEdge> subgraph;
        Integer source;
        Integer sink;

        @Setup(Level.Trial)
        public void load()
        {
            data = AndorraGraphLoader.load();
            int n = data.graph.vertexSet().size();
            Random rnd = new Random(13L);

            // best-effort search: pick the first anchor whose BFS ball is non-trivial
            // and yields any reachable target at least 2 hops away. Sparse rural roads
            // dominate Andorra, so tight size brackets fail too often.
            int bestSize = -1;
            Integer bestAnchor = null;
            Set<Integer> bestBall = null;
            Integer bestSink = null;
            int bestHops = -1;
            for (int attempt = 0; attempt < 400; attempt++) {
                Integer anchor = rnd.nextInt(n);
                Set<Integer> ball = bfsBall(anchor, bfsRadius);
                if (ball.size() < 8) {
                    continue;
                }
                Integer cand = null;
                int candHops = -1;
                for (Integer v : ball) {
                    if (!v.equals(anchor) && data.graph.outDegreeOf(v) > 0) {
                        int h = bfsHops(anchor, v, bfsRadius);
                        if (h > candHops) {
                            candHops = h;
                            cand = v;
                        }
                    }
                }
                if (cand == null || candHops < 2) {
                    continue;
                }
                // prefer a "richer" subgraph (more vertices) but cap so non-simple
                // enumeration stays under a few seconds per query.
                if (ball.size() <= 1500 && ball.size() > bestSize) {
                    bestSize = ball.size();
                    bestAnchor = anchor;
                    bestBall = ball;
                    bestSink = cand;
                    bestHops = candHops;
                    if (ball.size() >= 120 && candHops >= 3) {
                        // good enough, stop early
                        break;
                    }
                }
            }
            if (bestBall == null) {
                throw new IllegalStateException(
                    "could not carve any Andorra subgraph after 400 anchor attempts");
            }
            subgraph = new AsSubgraph<>(data.graph, bestBall);
            source = bestAnchor;
            sink = bestSink;
            // log via a sentinel field; JMH suppresses System.out during measurement
            // but the trial setup phase prints to surefire output.
            System.out.printf(
                "[AndorraAdpState] ball=%d, src=%d sink=%d hops=%d radius=%d maxPathLen=%d%n",
                bestBall.size(), bestAnchor, bestSink, bestHops, bfsRadius, maxPathLen);
        }

        private Set<Integer> bfsBall(Integer start, int radius)
        {
            Set<Integer> visited = new HashSet<>();
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[] { start, 0 });
            visited.add(start);
            while (!q.isEmpty()) {
                int[] head = q.poll();
                if (head[1] == radius) {
                    continue;
                }
                for (Integer nbr : Graphs.successorListOf(data.graph, head[0])) {
                    if (visited.add(nbr)) {
                        q.add(new int[] { nbr, head[1] + 1 });
                    }
                }
            }
            return visited;
        }

        private int bfsHops(Integer src, Integer dst, int radius)
        {
            ArrayDeque<int[]> q = new ArrayDeque<>();
            Set<Integer> visited = new HashSet<>();
            q.add(new int[] { src, 0 });
            visited.add(src);
            while (!q.isEmpty()) {
                int[] head = q.poll();
                if (head[0] == dst) {
                    return head[1];
                }
                if (head[1] == radius) {
                    continue;
                }
                for (Integer nbr : Graphs.successorListOf(data.graph, head[0])) {
                    if (visited.add(nbr)) {
                        q.add(new int[] { nbr, head[1] + 1 });
                    }
                }
            }
            return -1;
        }
    }
}
