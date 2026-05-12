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
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.AStarSpurEngine;
import org.jgrapht.alg.shortestpath.BoundedPrunedYenKShortestPath;
import org.jgrapht.alg.shortestpath.DijkstraSpurEngine;
import org.jgrapht.alg.shortestpath.YenKShortestPath;
import org.jgrapht.alg.util.Pair;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Roadmap-scale JMH benchmark for {@link BoundedPrunedYenKShortestPath} against
 * {@link YenKShortestPath} on the Andorra OSM road network.
 *
 * <p>
 * Graph: 36,618 vertices / 67,354 directed edges (largest SCC of the Geofabrik free
 * GPKG snapshot dated 2026-05-10). Weights = great-circle distance in metres.
 * Queries: 20 random (source, sink) pairs, fixed seed.
 *
 * <p>
 * Three engines are benchmarked: classical Yen, bounded-pruned Yen with a
 * {@link DijkstraSpurEngine}, and bounded-pruned Yen with an {@link AStarSpurEngine}.
 * All three return the same ordered sequence of path weights.
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
public class AndorraBoundedPrunedYenBench
{
    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> yen(AndorraYenState s)
    {
        return run(new YenKShortestPath<>(s.data.graph), s);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> boundedPrunedYenDijkstra(
        AndorraYenState s)
    {
        return run(
            new BoundedPrunedYenKShortestPath<>(s.data.graph, new DijkstraSpurEngine<>()), s);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> boundedPrunedYenAStar(
        AndorraYenState s)
    {
        return run(
            new BoundedPrunedYenKShortestPath<>(s.data.graph, new AStarSpurEngine<>()), s);
    }

    private static List<List<GraphPath<Integer, DefaultWeightedEdge>>> run(
        KShortestPathAlgorithm<Integer, DefaultWeightedEdge> alg, AndorraYenState s)
    {
        List<List<GraphPath<Integer, DefaultWeightedEdge>>> out = new ArrayList<>(s.queries.size());
        for (Pair<Integer, Integer> q : s.queries) {
            out.add(alg.getPaths(q.getFirst(), q.getSecond(), s.k));
        }
        return out;
    }

    @State(Scope.Benchmark)
    public static class AndorraYenState
    {
        // k=1 is just the underlying shortest-path engine; k=5 exercises the
        // bounded-prune candidate heap. Higher k values keep classical Yen running
        // for >30 s per query on Andorra, so we cap here.
        @Param({ "1", "5" })
        int k;

        AndorraGraphLoader.AndorraData data;
        List<Pair<Integer, Integer>> queries;

        @Setup(Level.Trial)
        public void load()
        {
            data = AndorraGraphLoader.load();
            int n = data.graph.vertexSet().size();
            Random rnd = new Random(7L);
            // 3 queries: classical Yen at k=5 averages ~5–10 s per query on
            // Andorra, so 3 queries per @Benchmark invocation keeps each JMH
            // iteration around 30 s (and lets BoundedPrunedYen still show its
            // amortised advantage).
            queries = new ArrayList<>(3);
            while (queries.size() < 3) {
                int src = rnd.nextInt(n);
                int dst = rnd.nextInt(n);
                if (src != dst) {
                    queries.add(Pair.of(src, dst));
                }
            }
        }
    }
}
