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

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.AStarSpurEngine;
import org.jgrapht.alg.shortestpath.BoundedPrunedYenKShortestPath;
import org.jgrapht.alg.shortestpath.DijkstraSpurEngine;
import org.jgrapht.alg.shortestpath.EppsteinKShortestPath;
import org.jgrapht.alg.shortestpath.YenKShortestPath;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.generate.GnpRandomGraphGenerator;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
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
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark comparing top-k shortest-path algorithms across two graph families:
 * the Andorra OSM road graph (sparse, geographically structured) and a small
 * {@code G(n,p)} dense random graph.
 *
 * <p>
 * Five algorithm variants are compared:
 * <ul>
 *   <li>{@code yen} &mdash; classical {@link YenKShortestPath}.</li>
 *   <li>{@code yenAStarNoBoundedPrune} &mdash;
 *       {@link BoundedPrunedYenKShortestPath} with the A* spur engine and
 *       {@code setBoundedPruning(false)}: isolates the A* heuristic's
 *       contribution from the bounded-prune layer.</li>
 *   <li>{@code boundedPrunedYenDijkstra} &mdash; bounded prune + Dijkstra spur:
 *       isolates the bounded-prune layer's contribution from the heuristic.</li>
 *   <li>{@code boundedPrunedYenAStar} &mdash; bounded prune + A* spur (both).</li>
 *   <li>{@code eppstein} &mdash; {@link EppsteinKShortestPath}, which returns
 *       k shortest walks (loops allowed) rather than k shortest simple paths.
 *       Included for reference; the result sequence is not directly comparable
 *       to the Yen-family variants when loops shorten the answer.</li>
 * </ul>
 *
 * <p>
 * Graph types: {@code andorra} (36,618 vertices / 67,354 directed edges, weights in
 * metres, loaded from the committed CSV resource); {@code gnp} ({@code G(n=500, p=0.3)}
 * with uniform random weights). The dense {@code gnp} graph has no geometric structure,
 * so the {@link AStarSpurEngine}'s reverse-distance heuristic still works but the
 * heuristic value collapses toward a small constant and A* degenerates toward Dijkstra.
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
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class KShortestPathBench
{
    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> yen(KShortestState s)
    {
        return run(new YenKShortestPath<>(s.graph), s);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> yenAStarNoBoundedPrune(
        KShortestState s)
    {
        BoundedPrunedYenKShortestPath<Integer, DefaultWeightedEdge> alg =
            new BoundedPrunedYenKShortestPath<>(s.graph, new AStarSpurEngine<>());
        alg.setBoundedPruning(false);
        return run(alg, s);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> boundedPrunedYenDijkstra(
        KShortestState s)
    {
        return run(
            new BoundedPrunedYenKShortestPath<>(s.graph, new DijkstraSpurEngine<>()), s);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> boundedPrunedYenAStar(
        KShortestState s)
    {
        return run(
            new BoundedPrunedYenKShortestPath<>(s.graph, new AStarSpurEngine<>()), s);
    }

    @Benchmark
    public List<List<GraphPath<Integer, DefaultWeightedEdge>>> eppstein(KShortestState s)
    {
        return run(new EppsteinKShortestPath<>(s.graph), s);
    }

    private static List<List<GraphPath<Integer, DefaultWeightedEdge>>> run(
        KShortestPathAlgorithm<Integer, DefaultWeightedEdge> alg, KShortestState s)
    {
        List<List<GraphPath<Integer, DefaultWeightedEdge>>> out =
            new ArrayList<>(s.queries.size());
        for (Pair<Integer, Integer> q : s.queries) {
            out.add(alg.getPaths(q.getFirst(), q.getSecond(), s.k));
        }
        return out;
    }

    @State(Scope.Benchmark)
    public static class KShortestState
    {
        /** Graph family. Switches the {@code @Setup} between Andorra and dense Gnp. */
        @Param({ "andorra", "gnp" })
        String graphType;

        /**
         * Number of shortest paths per query. {@code k = 1} measures the underlying spur
         * engine cost; {@code k = 5} exercises the candidate heap; {@code k = 25} is the
         * regime where the bounded-prune layer has room to amortise across spurs.
         */
        @Param({ "1", "5", "25" })
        int k;

        Graph<Integer, DefaultWeightedEdge> graph;
        List<Pair<Integer, Integer>> queries;

        @Setup(Level.Trial)
        public void load()
        {
            if ("andorra".equals(graphType)) {
                AndorraGraphLoader.AndorraData data = AndorraGraphLoader.load();
                graph = data.graph;
            } else if ("gnp".equals(graphType)) {
                graph = buildGnpGraph(/* n= */ 500, /* p= */ 0.3, /* seed= */ 42L);
            } else {
                throw new IllegalArgumentException("unknown graphType: " + graphType);
            }
            queries = sampleQueries(graph, /* count= */ 10, /* seed= */ 7L);
        }

        private static Graph<Integer, DefaultWeightedEdge> buildGnpGraph(
            int n, double p, long seed)
        {
            Random rnd = new Random(seed);
            // SimpleDirectedWeightedGraph so Eppstein can consume the same graph
            // instance as the Yen-family variants. GnpRandomGraphGenerator does not
            // emit parallel edges or self-loops, so the simple-graph contract holds.
            SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> g =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            g.setVertexSupplier(SupplierUtil.createIntegerSupplier());
            new GnpRandomGraphGenerator<Integer, DefaultWeightedEdge>(n, p, seed)
                .generateGraph(g);
            for (DefaultWeightedEdge e : g.edgeSet()) {
                // Weights in [1, 100] keep numerics tame and avoid Eppstein's
                // negative-weight rejection path.
                g.setEdgeWeight(e, 1.0 + 99.0 * rnd.nextDouble());
            }
            return g;
        }

        private static List<Pair<Integer, Integer>> sampleQueries(
            Graph<Integer, DefaultWeightedEdge> g, int count, long seed)
        {
            int n = g.vertexSet().size();
            Random rnd = new Random(seed);
            // Plain index sampling: Andorra uses 0..N-1 ids by construction; Gnp also
            // uses 0..N-1 from the IntegerSupplier.
            List<Pair<Integer, Integer>> out = new ArrayList<>(count);
            while (out.size() < count) {
                int src = rnd.nextInt(n);
                int dst = rnd.nextInt(n);
                if (src != dst) {
                    out.add(Pair.of(src, dst));
                }
            }
            return out;
        }
    }
}
