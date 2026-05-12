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

import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths;
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths;
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

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Andorra-OSM benchmark for {@link DijkstraManyToManyShortestPaths#getPaths(Object)}.
 *
 * <p>
 * The classical implementation runs |S| independent Dijkstras when {@code getPaths(V)}
 * is invoked after {@code getManyToManyPaths(S, T)}. PR #1340 changes the behaviour to
 * reuse the single Dijkstra already executed during the many-to-many call, returning a
 * cheap view over the cached tree. Running this bench on master and on
 * {@code m2m-getpaths-single-dijkstra} (or any branch that backports PR #1340) yields
 * the before/after speedup.
 *
 * @author Shai Eilat
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AndorraDijkstraManyToManyGetPathsBench
{
    @Benchmark
    public SingleSourcePaths<Integer, DefaultWeightedEdge> getPathsForFirstSource(
        AndorraM2MState s)
    {
        DijkstraManyToManyShortestPaths<Integer, DefaultWeightedEdge> alg =
            new DijkstraManyToManyShortestPaths<>(s.data.graph);
        // exercise the many-to-many computation, then ask for the single-source view
        alg.getManyToManyPaths(s.sources, s.targets);
        Integer first = s.sources.iterator().next();
        return alg.getPaths(first);
    }

    @State(Scope.Benchmark)
    public static class AndorraM2MState
    {
        @Param({ "10", "50", "100" })
        int sourceCount;
        @Param({ "10", "50", "100" })
        int targetCount;

        AndorraGraphLoader.AndorraData data;
        Set<Integer> sources;
        Set<Integer> targets;

        @Setup(Level.Trial)
        public void load()
        {
            data = AndorraGraphLoader.load();
            int n = data.graph.vertexSet().size();
            Random rnd = new Random(11L);
            sources = new HashSet<>();
            while (sources.size() < sourceCount) {
                sources.add(rnd.nextInt(n));
            }
            targets = new HashSet<>();
            while (targets.size() < targetCount) {
                targets.add(rnd.nextInt(n));
            }
        }
    }
}
