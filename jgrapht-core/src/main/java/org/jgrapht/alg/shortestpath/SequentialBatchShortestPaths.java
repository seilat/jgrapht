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
package org.jgrapht.alg.shortestpath;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.*;
import org.jgrapht.alg.util.*;

import java.util.*;
import java.util.function.*;

/**
 * Sequential adapter that turns a single-pair {@link ShortestPathAlgorithm} into a
 * {@link BatchShortestPathAlgorithm} by processing each query independently.
 *
 * <p>
 * This is the baseline implementation in the {@code BatchShortestPathAlgorithm} family.
 * It runs the wrapped algorithm once per distinct query pair, on the calling thread, in
 * the iteration order of the input collection. When queries share a source vertex, the
 * adapter can amortize work by issuing a single all-targets search via
 * {@link ShortestPathAlgorithm#getPaths(Object)}; this behavior is controlled by the
 * {@code groupBySource} option (enabled by default).
 *
 * <p>
 * The adapter does not require the wrapped algorithm to be thread-safe; queries are
 * dispatched sequentially. Parallel and CH-backed variants live in sibling classes.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author Shai Eilat
 */
public class SequentialBatchShortestPaths<V, E>
    implements BatchShortestPathAlgorithm<V, E>
{

    private final Graph<V, E> graph;
    private final Function<Graph<V, E>, ShortestPathAlgorithm<V, E>> algorithmFactory;
    private final boolean groupBySource;

    /**
     * Constructs a sequential batch adapter that, for every batch, creates a fresh
     * single-pair algorithm via {@code algorithmFactory}. Source grouping is enabled.
     *
     * @param graph the graph
     * @param algorithmFactory factory producing a single-pair shortest-path algorithm
     *        bound to the graph
     */
    public SequentialBatchShortestPaths(
        Graph<V, E> graph,
        Function<Graph<V, E>, ShortestPathAlgorithm<V, E>> algorithmFactory)
    {
        this(graph, algorithmFactory, true);
    }

    /**
     * Constructs a sequential batch adapter with explicit grouping control.
     *
     * @param graph the graph
     * @param algorithmFactory factory producing a single-pair shortest-path algorithm
     *        bound to the graph
     * @param groupBySource when {@code true}, queries sharing a source vertex are served
     *        from a single {@link ShortestPathAlgorithm#getPaths(Object)} call; when
     *        {@code false}, every query issues a fresh {@code getPath(source, target)}
     *        call
     */
    public SequentialBatchShortestPaths(
        Graph<V, E> graph,
        Function<Graph<V, E>, ShortestPathAlgorithm<V, E>> algorithmFactory,
        boolean groupBySource)
    {
        this.graph = Objects.requireNonNull(graph, "graph should not be null");
        this.algorithmFactory =
            Objects.requireNonNull(algorithmFactory, "algorithmFactory should not be null");
        this.groupBySource = groupBySource;
    }

    /**
     * Convenience constructor that wraps an algorithm-instance supplier as a factory
     * ignoring the graph argument. Intended for already-constructed algorithm instances
     * bound to the same graph.
     *
     * @param graph the graph
     * @param algorithm the single-pair shortest-path algorithm bound to {@code graph}
     */
    public SequentialBatchShortestPaths(
        Graph<V, E> graph, ShortestPathAlgorithm<V, E> algorithm)
    {
        this(graph, g -> algorithm, true);
        Objects.requireNonNull(algorithm, "algorithm should not be null");
    }

    @Override
    public BatchShortestPaths<V, E> getBatchPaths(Collection<Pair<V, V>> queries)
    {
        Objects.requireNonNull(queries, "queries should not be null");

        Map<Pair<V, V>, GraphPath<V, E>> paths = new LinkedHashMap<>();
        Map<Pair<V, V>, Double> weights = new LinkedHashMap<>();

        if (groupBySource) {
            computeGroupedBySource(queries, paths, weights);
        } else {
            computePairByPair(queries, paths, weights);
        }

        return new MapBackedBatchShortestPaths<>(queries, paths, weights);
    }

    private void computePairByPair(
        Collection<Pair<V, V>> queries,
        Map<Pair<V, V>, GraphPath<V, E>> paths,
        Map<Pair<V, V>, Double> weights)
    {
        ShortestPathAlgorithm<V, E> algorithm = algorithmFactory.apply(graph);
        for (Pair<V, V> q : queries) {
            if (paths.containsKey(q)) {
                continue;
            }
            assertVertices(q);
            GraphPath<V, E> p = algorithm.getPath(q.getFirst(), q.getSecond());
            paths.put(q, p);
            weights.put(q, p == null ? Double.POSITIVE_INFINITY : p.getWeight());
        }
    }

    private void computeGroupedBySource(
        Collection<Pair<V, V>> queries,
        Map<Pair<V, V>, GraphPath<V, E>> paths,
        Map<Pair<V, V>, Double> weights)
    {
        Map<V, List<Pair<V, V>>> bySource = new LinkedHashMap<>();
        for (Pair<V, V> q : queries) {
            if (paths.containsKey(q)) {
                continue;
            }
            assertVertices(q);
            bySource.computeIfAbsent(q.getFirst(), s -> new ArrayList<>()).add(q);
        }

        for (Map.Entry<V, List<Pair<V, V>>> e : bySource.entrySet()) {
            List<Pair<V, V>> sourceQueries = e.getValue();
            ShortestPathAlgorithm<V, E> algorithm = algorithmFactory.apply(graph);
            if (sourceQueries.size() == 1) {
                Pair<V, V> q = sourceQueries.get(0);
                GraphPath<V, E> p = algorithm.getPath(q.getFirst(), q.getSecond());
                paths.put(q, p);
                weights.put(q, p == null ? Double.POSITIVE_INFINITY : p.getWeight());
            } else {
                SingleSourcePaths<V, E> ssp = algorithm.getPaths(e.getKey());
                for (Pair<V, V> q : sourceQueries) {
                    GraphPath<V, E> p = ssp.getPath(q.getSecond());
                    paths.put(q, p);
                    weights.put(q, p == null ? Double.POSITIVE_INFINITY : p.getWeight());
                }
            }
        }
    }

    private void assertVertices(Pair<V, V> q)
    {
        if (!graph.containsVertex(q.getFirst())) {
            throw new IllegalArgumentException(
                "source vertex not in graph: " + q.getFirst());
        }
        if (!graph.containsVertex(q.getSecond())) {
            throw new IllegalArgumentException(
                "target vertex not in graph: " + q.getSecond());
        }
    }

    private static final class MapBackedBatchShortestPaths<V, E>
        extends BatchShortestPathAlgorithm.BaseBatchShortestPathsImpl<V, E>
    {
        private final Map<Pair<V, V>, GraphPath<V, E>> paths;
        private final Map<Pair<V, V>, Double> weights;

        MapBackedBatchShortestPaths(
            Collection<Pair<V, V>> queries,
            Map<Pair<V, V>, GraphPath<V, E>> paths,
            Map<Pair<V, V>, Double> weights)
        {
            super(queries);
            this.paths = paths;
            this.weights = weights;
        }

        @Override
        public GraphPath<V, E> getPath(V source, V target)
        {
            assertQueried(source, target);
            return paths.get(Pair.of(source, target));
        }

        @Override
        public double getWeight(V source, V target)
        {
            assertQueried(source, target);
            return weights.get(Pair.of(source, target));
        }

        @Override
        public Map<Pair<V, V>, GraphPath<V, E>> asPathMap()
        {
            return Collections.unmodifiableMap(paths);
        }

        @Override
        public Map<Pair<V, V>, Double> asWeightMap()
        {
            return Collections.unmodifiableMap(weights);
        }
    }
}
