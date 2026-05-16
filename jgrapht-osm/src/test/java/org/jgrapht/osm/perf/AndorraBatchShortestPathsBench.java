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
package org.jgrapht.osm.perf;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.BatchShortestPathAlgorithm.*;
import org.jgrapht.alg.shortestpath.*;
import org.jgrapht.alg.util.*;
import org.jgrapht.graph.*;
import org.jgrapht.osm.*;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Andorra-OSM benchmark for {@link SequentialBatchShortestPaths}.
 *
 * <p>
 * Sweeps {@code distinctSources} to characterize the crossover at which source grouping
 * stops winning. At {@code distinctSources = queryCount} every query has a unique source
 * and grouping degenerates to one Dijkstra per query (equivalent to naive); at
 * {@code distinctSources = 1} all queries share a source and grouping issues exactly one
 * SSSP.
 *
 * <p>
 * Modes compared:
 * <ul>
 *   <li>{@code testNaiveLoop} — fresh {@code DijkstraShortestPath} per query, single-pair
 *       call.</li>
 *   <li>{@code testSequentialBatchGrouped} — adapter with source grouping enabled.</li>
 * </ul>
 *
 * <p>
 * The {@code distinctSources} sweep uses powers of four ({@code 1, 4, 16, 64}) so the
 * expected speedup falls along a clean curve. {@code queryCount} is fixed at 64 because
 * Andorra Dijkstras cost ~5-20 ms each on a laptop and a 256-query naive cell would
 * already burn ~3 s per measurement iteration.
 *
 * @author Shai Eilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0, jvmArgs = {
    "--add-opens=org.jgrapht.osm/org.jgrapht.osm.perf=ALL-UNNAMED",
    "--add-opens=org.jgrapht.osm/org.jgrapht.osm.perf.jmh_generated=ALL-UNNAMED",
    "--add-exports=org.jgrapht.osm/org.jgrapht.osm.perf=ALL-UNNAMED",
    "--add-exports=org.jgrapht.osm/org.jgrapht.osm.perf.jmh_generated=ALL-UNNAMED"
})
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AndorraBatchShortestPathsBench
{

    private static final long SEED = 19L;

    @Benchmark
    public Map<Pair<Integer, Integer>, Double> testNaiveLoop(AndorraBatchState s)
    {
        Map<Pair<Integer, Integer>, Double> out = new HashMap<>(s.queries.size());
        for (Pair<Integer, Integer> q : s.queries) {
            GraphPath<Integer, DefaultWeightedEdge> p =
                new DijkstraShortestPath<>(s.graph).getPath(q.getFirst(), q.getSecond());
            out.put(q, p == null ? Double.POSITIVE_INFINITY : p.getWeight());
        }
        return out;
    }

    @Benchmark
    public Map<Pair<Integer, Integer>, Double> testSequentialBatchGrouped(AndorraBatchState s)
    {
        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            new SequentialBatchShortestPaths<>(
                s.graph, DijkstraShortestPath::new, true)
                .getBatchPaths(s.queries);
        return result.asWeightMap();
    }

    @State(Scope.Benchmark)
    public static class AndorraBatchState
    {
        @Param({ "64" })
        int queryCount;
        @Param({ "1", "4", "16", "64" })
        int distinctSources;

        Graph<Integer, DefaultWeightedEdge> graph;
        List<Pair<Integer, Integer>> queries;

        @Setup(Level.Trial)
        public void load()
        {
            graph = AndorraGraphLoader.load().graph;
        }

        @Setup(Level.Iteration)
        public void buildQueries()
        {
            Random rng = new Random(SEED);
            Integer[] vertices = graph.vertexSet().toArray(new Integer[0]);
            Arrays.sort(vertices);
            List<Integer> sources = new ArrayList<>(distinctSources);
            for (int i = 0; i < distinctSources; i++) {
                sources.add(vertices[rng.nextInt(vertices.length)]);
            }
            queries = new ArrayList<>(queryCount);
            for (int i = 0; i < queryCount; i++) {
                Integer src = sources.get(rng.nextInt(sources.size()));
                Integer dst = vertices[rng.nextInt(vertices.length)];
                queries.add(Pair.of(src, dst));
            }
        }
    }
}
