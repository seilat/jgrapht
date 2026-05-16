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
package org.jgrapht.alg.interfaces;

import org.jgrapht.*;
import org.jgrapht.alg.util.*;

import java.util.*;

/**
 * An algorithm which computes shortest paths for a batch of independent
 * {@code (source, target)} queries.
 *
 * <p>
 * Unlike {@link ManyToManyShortestPathsAlgorithm}, which computes the full cross-product
 * of paths between two vertex sets, this interface targets workloads where only a
 * specific set of source-target pairs is of interest. Implementations are free to
 * exploit the batch structure (for example by running queries in parallel or by
 * grouping queries that share an endpoint), but must produce results equivalent to
 * running each query independently.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author Shai Eilat
 */
public interface BatchShortestPathAlgorithm<V, E>
{

    /**
     * Compute shortest paths for the given collection of {@code (source, target)} queries.
     *
     * <p>
     * Duplicate queries in the input are permitted; the result returns the same path for
     * each occurrence. The order in which queries are processed is implementation-defined.
     *
     * @param queries the collection of {@code (source, target)} query pairs
     * @return the computed batch shortest paths
     */
    BatchShortestPaths<V, E> getBatchPaths(Collection<Pair<V, V>> queries);

    /**
     * The result of a batch shortest paths computation.
     *
     * @param <V> the graph vertex type
     * @param <E> the graph edge type
     */
    interface BatchShortestPaths<V, E>
    {

        /**
         * Returns the queries for which this batch result was computed. The returned
         * collection contains the same pairs (including duplicates) that were passed to
         * {@link BatchShortestPathAlgorithm#getBatchPaths(Collection)}.
         *
         * @return the queries this result covers
         */
        Collection<Pair<V, V>> getQueries();

        /**
         * Return the shortest path from {@code source} to {@code target}.
         *
         * <p>
         * The pair {@code (source, target)} must have been part of the original query
         * collection.
         *
         * @param source the source vertex
         * @param target the target vertex
         * @return the shortest path or {@code null} if no such path exists
         * @throws IllegalArgumentException if {@code (source, target)} was not part of the
         *         original query collection
         */
        GraphPath<V, E> getPath(V source, V target);

        /**
         * Return the weight of the shortest path from {@code source} to {@code target}, or
         * {@link Double#POSITIVE_INFINITY} if no such path exists. The weight of the path
         * between a vertex and itself is always zero.
         *
         * <p>
         * The pair {@code (source, target)} must have been part of the original query
         * collection.
         *
         * @param source the source vertex
         * @param target the target vertex
         * @return the shortest path weight or {@link Double#POSITIVE_INFINITY} if no such
         *         path exists
         * @throws IllegalArgumentException if {@code (source, target)} was not part of the
         *         original query collection
         */
        double getWeight(V source, V target);

        /**
         * Return the batch result as a map keyed by query pair. The returned map preserves
         * the iteration order of the original query collection where possible. Duplicate
         * query pairs collapse to a single entry.
         *
         * @return a map from query pair to shortest path (or {@code null} when no path
         *         exists)
         */
        Map<Pair<V, V>, GraphPath<V, E>> asPathMap();

        /**
         * Return the batch result as a map keyed by query pair, with shortest path weights
         * as values. Entries for pairs with no path map to {@link Double#POSITIVE_INFINITY}.
         * Duplicate query pairs collapse to a single entry.
         *
         * @return a map from query pair to shortest path weight
         */
        Map<Pair<V, V>, Double> asWeightMap();
    }

    /**
     * Base class for batch shortest paths implementations. Stores the input query
     * collection and provides argument-validation helpers; subclasses contribute the
     * actual path and weight lookups.
     *
     * @param <V> the graph vertex type
     * @param <E> the graph edge type
     */
    abstract class BaseBatchShortestPathsImpl<V, E> implements BatchShortestPaths<V, E>
    {
        private final Collection<Pair<V, V>> queries;
        private final Set<Pair<V, V>> queryLookup;

        /**
         * Constructs an instance for the given query collection.
         *
         * @param queries the query collection (a defensive copy is taken)
         */
        protected BaseBatchShortestPathsImpl(Collection<Pair<V, V>> queries)
        {
            Objects.requireNonNull(queries, "queries should not be null");
            this.queries = List.copyOf(queries);
            this.queryLookup = new HashSet<>(this.queries);
        }

        @Override
        public Collection<Pair<V, V>> getQueries()
        {
            return queries;
        }

        /**
         * Validates that {@code (source, target)} is part of the original query
         * collection.
         *
         * @param source the source vertex
         * @param target the target vertex
         * @throws IllegalArgumentException if the pair was not queried for
         */
        protected void assertQueried(V source, V target)
        {
            Objects.requireNonNull(source, "source should not be null");
            Objects.requireNonNull(target, "target should not be null");
            if (!queryLookup.contains(Pair.of(source, target))) {
                throw new IllegalArgumentException(
                    "path between " + source + " and " + target + " was not queried");
            }
        }
    }
}
