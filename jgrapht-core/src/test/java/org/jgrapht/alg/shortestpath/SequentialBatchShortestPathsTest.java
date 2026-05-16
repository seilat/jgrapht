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
import org.jgrapht.alg.interfaces.BatchShortestPathAlgorithm.*;
import org.jgrapht.alg.util.*;
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;
import org.jgrapht.util.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SequentialBatchShortestPaths}.
 *
 * @author Shai Eilat
 */
public class SequentialBatchShortestPathsTest
{

    private static final long SEED = 19L;

    @Test
    public void testEmptyQueries()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(5);
        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            adapter(graph).getBatchPaths(List.of());

        assertTrue(result.getQueries().isEmpty());
        assertTrue(result.asPathMap().isEmpty());
        assertTrue(result.asWeightMap().isEmpty());
    }

    @Test
    public void testSingleQueryMatchesReference()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(5);
        Pair<Integer, Integer> q = Pair.of(0, 4);

        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            adapter(graph).getBatchPaths(List.of(q));

        GraphPath<Integer, DefaultWeightedEdge> reference =
            new DijkstraShortestPath<>(graph).getPath(0, 4);

        assertEquals(reference.getEdgeList(), result.getPath(0, 4).getEdgeList());
        assertEquals(reference.getWeight(), result.getWeight(0, 4), 0.0);
    }

    @Test
    public void testNoPathReturnsNullAndInfinity()
    {
        DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph.addVertex(0);
        graph.addVertex(1);

        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            adapter(graph).getBatchPaths(List.of(Pair.of(0, 1)));

        assertNull(result.getPath(0, 1));
        assertEquals(Double.POSITIVE_INFINITY, result.getWeight(0, 1), 0.0);
    }

    @Test
    public void testSourceEqualsTargetIsZeroWeight()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(3);
        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            adapter(graph).getBatchPaths(List.of(Pair.of(1, 1)));

        assertEquals(0.0, result.getWeight(1, 1), 0.0);
        assertNotNull(result.getPath(1, 1));
        assertEquals(List.of(), result.getPath(1, 1).getEdgeList());
    }

    @Test
    public void testDuplicateQueriesCollapse()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(5);
        Pair<Integer, Integer> q = Pair.of(0, 4);

        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            adapter(graph).getBatchPaths(List.of(q, q, q));

        assertEquals(3, result.getQueries().size());
        assertEquals(1, result.asPathMap().size());
        assertEquals(1, result.asWeightMap().size());
    }

    @Test
    public void testAssertQueriedThrowsForUnknownPair()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(5);
        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            adapter(graph).getBatchPaths(List.of(Pair.of(0, 4)));

        assertThrows(IllegalArgumentException.class, () -> result.getPath(0, 1));
        assertThrows(IllegalArgumentException.class, () -> result.getWeight(2, 3));
    }

    @Test
    public void testVertexNotInGraphRejected()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(3);
        SequentialBatchShortestPaths<Integer, DefaultWeightedEdge> alg = adapter(graph);

        assertThrows(
            IllegalArgumentException.class,
            () -> alg.getBatchPaths(List.of(Pair.of(99, 0))));
        assertThrows(
            IllegalArgumentException.class,
            () -> alg.getBatchPaths(List.of(Pair.of(0, 99))));
    }

    @Test
    public void testGroupedAndUngroupedAgree()
    {
        Graph<Integer, DefaultWeightedEdge> graph = randomGraph(40, 0.15);
        List<Pair<Integer, Integer>> queries = randomQueries(graph, 25);

        BatchShortestPaths<Integer, DefaultWeightedEdge> grouped =
            new SequentialBatchShortestPaths<>(graph, dijkstraFactory(), true)
                .getBatchPaths(queries);
        BatchShortestPaths<Integer, DefaultWeightedEdge> flat =
            new SequentialBatchShortestPaths<>(graph, dijkstraFactory(), false)
                .getBatchPaths(queries);

        for (Pair<Integer, Integer> q : queries) {
            assertEquals(
                flat.getWeight(q.getFirst(), q.getSecond()),
                grouped.getWeight(q.getFirst(), q.getSecond()),
                0.0,
                "weight mismatch for " + q);
        }
    }

    @Test
    public void testMatchesDijkstraOnRandomGraph()
    {
        Graph<Integer, DefaultWeightedEdge> graph = randomGraph(50, 0.2);
        List<Pair<Integer, Integer>> queries = randomQueries(graph, 30);

        DijkstraShortestPath<Integer, DefaultWeightedEdge> reference =
            new DijkstraShortestPath<>(graph);
        BatchShortestPaths<Integer, DefaultWeightedEdge> batch =
            adapter(graph).getBatchPaths(queries);

        for (Pair<Integer, Integer> q : queries) {
            double refWeight = reference.getPathWeight(q.getFirst(), q.getSecond());
            double batchWeight = batch.getWeight(q.getFirst(), q.getSecond());
            assertEquals(refWeight, batchWeight, 0.0, "weight mismatch for " + q);
        }
    }

    @Test
    public void testBidirectionalDijkstraAsBackendMatchesDijkstra()
    {
        Graph<Integer, DefaultWeightedEdge> graph = randomGraph(40, 0.2);
        List<Pair<Integer, Integer>> queries = randomQueries(graph, 20);

        Function<Graph<Integer, DefaultWeightedEdge>,
            org.jgrapht.alg.interfaces.ShortestPathAlgorithm<Integer, DefaultWeightedEdge>> bidirFactory =
                BidirectionalDijkstraShortestPath::new;

        BatchShortestPaths<Integer, DefaultWeightedEdge> bidir =
            new SequentialBatchShortestPaths<>(graph, bidirFactory).getBatchPaths(queries);
        BatchShortestPaths<Integer, DefaultWeightedEdge> dijkstra =
            adapter(graph).getBatchPaths(queries);

        for (Pair<Integer, Integer> q : queries) {
            assertEquals(
                dijkstra.getWeight(q.getFirst(), q.getSecond()),
                bidir.getWeight(q.getFirst(), q.getSecond()),
                0.0,
                "weight mismatch for " + q);
        }
    }

    @Test
    public void testAlgorithmInstanceConstructor()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(6);
        DijkstraShortestPath<Integer, DefaultWeightedEdge> instance =
            new DijkstraShortestPath<>(graph);

        BatchShortestPaths<Integer, DefaultWeightedEdge> result =
            new SequentialBatchShortestPaths<>(graph, instance)
                .getBatchPaths(List.of(Pair.of(0, 5)));

        assertEquals(5.0, result.getWeight(0, 5), 0.0);
    }

    @Test
    public void testNullArgumentsRejected()
    {
        Graph<Integer, DefaultWeightedEdge> graph = simpleChain(3);
        SequentialBatchShortestPaths<Integer, DefaultWeightedEdge> alg = adapter(graph);

        assertThrows(NullPointerException.class, () -> alg.getBatchPaths(null));
        assertThrows(
            NullPointerException.class,
            () -> new SequentialBatchShortestPaths<>(
                null, (Function<Graph<Integer, DefaultWeightedEdge>,
                    org.jgrapht.alg.interfaces.ShortestPathAlgorithm<
                        Integer, DefaultWeightedEdge>>) DijkstraShortestPath::new));
        assertThrows(
            NullPointerException.class,
            () -> new SequentialBatchShortestPaths<>(
                graph,
                (Function<Graph<Integer, DefaultWeightedEdge>,
                    org.jgrapht.alg.interfaces.ShortestPathAlgorithm<
                        Integer, DefaultWeightedEdge>>) null));
    }

    private static SequentialBatchShortestPaths<Integer, DefaultWeightedEdge> adapter(
        Graph<Integer, DefaultWeightedEdge> graph)
    {
        return new SequentialBatchShortestPaths<>(graph, dijkstraFactory());
    }

    private static
        Function<Graph<Integer, DefaultWeightedEdge>,
            org.jgrapht.alg.interfaces.ShortestPathAlgorithm<Integer, DefaultWeightedEdge>>
        dijkstraFactory()
    {
        return DijkstraShortestPath::new;
    }

    private static Graph<Integer, DefaultWeightedEdge> simpleChain(int n)
    {
        DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            graph.addVertex(i);
        }
        for (int i = 0; i < n - 1; i++) {
            DefaultWeightedEdge e = graph.addEdge(i, i + 1);
            graph.setEdgeWeight(e, 1.0);
        }
        return graph;
    }

    private static Graph<Integer, DefaultWeightedEdge> randomGraph(int n, double p)
    {
        DefaultDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph.setVertexSupplier(SupplierUtil.createIntegerSupplier());
        new GnpRandomGraphGenerator<Integer, DefaultWeightedEdge>(n, p, SEED)
            .generateGraph(graph);
        // Ensure some connectivity along an explicit chain.
        Integer[] vertices = graph.vertexSet().toArray(new Integer[0]);
        Arrays.sort(vertices);
        for (int i = 0; i < vertices.length - 1; i++) {
            if (!graph.containsEdge(vertices[i], vertices[i + 1])) {
                graph.addEdge(vertices[i], vertices[i + 1]);
            }
        }
        Random rng = new Random(SEED);
        for (DefaultWeightedEdge e : graph.edgeSet()) {
            graph.setEdgeWeight(e, 1.0 + rng.nextInt(100));
        }
        return graph;
    }

    private static List<Pair<Integer, Integer>> randomQueries(
        Graph<Integer, DefaultWeightedEdge> graph, int count)
    {
        Integer[] vertices = graph.vertexSet().toArray(new Integer[0]);
        Random rng = new Random(SEED + 1);
        List<Pair<Integer, Integer>> queries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Integer s = vertices[rng.nextInt(vertices.length)];
            Integer t = vertices[rng.nextInt(vertices.length)];
            queries.add(Pair.of(s, t));
        }
        return queries;
    }
}
