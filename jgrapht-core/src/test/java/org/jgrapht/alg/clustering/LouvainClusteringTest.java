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
package org.jgrapht.alg.clustering;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.ClusteringAlgorithm.*;
import org.jgrapht.graph.*;
import org.jgrapht.graph.builder.*;
import org.jgrapht.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LouvainClustering}.
 *
 * @author seilat
 */
public class LouvainClusteringTest
{
    private static final long SEED = 0x1234ABCDL;

    @Test
    public void twoCliquesJoinedByOneEdge()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        g.addEdge(3, 4);

        Clustering<Integer> c = new LouvainClustering<>(g, new Random(SEED)).getClustering();

        assertEquals(2, c.getNumberClusters());
        assertEquals(
            Set.of(Set.of(0, 1, 2, 3), Set.of(4, 5, 6, 7)), new HashSet<>(c.getClusters()));
    }

    @Test
    public void threeCliquesInARing()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        addClique(g, 8, 9, 10, 11);
        g.addEdge(3, 4);
        g.addEdge(7, 8);
        g.addEdge(11, 0);

        Clustering<Integer> c = new LouvainClustering<>(g, new Random(SEED)).getClustering();

        assertEquals(3, c.getNumberClusters());
        assertEquals(
            Set.of(Set.of(0, 1, 2, 3), Set.of(4, 5, 6, 7), Set.of(8, 9, 10, 11)),
            new HashSet<>(c.getClusters()));
    }

    @Test
    public void completeGraphIsOneCommunity()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3, 4, 5);

        LouvainClustering<Integer, DefaultEdge> alg = new LouvainClustering<>(g, new Random(SEED));
        Clustering<Integer> c = alg.getClustering();

        assertEquals(1, c.getNumberClusters());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), c.getClusters().get(0));
        // The single-community partition of a complete graph has modularity 0; no split improves.
        assertEquals(0d, alg.getModularity(), 1e-9);
    }

    @Test
    public void weightedGraphFollowsHeavyEdges()
    {
        // Two triangles linked by a single light edge; heavy intra-triangle weights.
        Graph<Integer, DefaultEdge> g = weighted();
        for (int i = 0; i < 6; i++) {
            g.addVertex(i);
        }
        setEdge(g, 0, 1, 10);
        setEdge(g, 1, 2, 10);
        setEdge(g, 0, 2, 10);
        setEdge(g, 3, 4, 10);
        setEdge(g, 4, 5, 10);
        setEdge(g, 3, 5, 10);
        setEdge(g, 2, 3, 1); // light bridge

        Clustering<Integer> c = new LouvainClustering<>(g, new Random(SEED)).getClustering();

        assertEquals(2, c.getNumberClusters());
        assertEquals(Set.of(Set.of(0, 1, 2), Set.of(3, 4, 5)), new HashSet<>(c.getClusters()));
    }

    @Test
    public void seedYieldsDeterministicResult()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        addClique(g, 8, 9, 10, 11);
        g.addEdge(3, 4);
        g.addEdge(7, 8);

        List<Set<Integer>> a =
            new LouvainClustering<>(g, new Random(SEED)).getClustering().getClusters();
        List<Set<Integer>> b =
            new LouvainClustering<>(g, new Random(SEED)).getClustering().getClusters();

        assertEquals(a, b);
    }

    @Test
    public void singleVertex()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        g.addVertex(0);

        LouvainClustering<Integer, DefaultEdge> alg = new LouvainClustering<>(g, new Random(SEED));
        Clustering<Integer> c = alg.getClustering();

        assertEquals(1, c.getNumberClusters());
        assertEquals(Set.of(0), c.getClusters().get(0));
        assertEquals(0d, alg.getModularity(), 1e-9);
    }

    @Test
    public void emptyGraph()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        LouvainClustering<Integer, DefaultEdge> alg = new LouvainClustering<>(g, new Random(SEED));
        Clustering<Integer> c = alg.getClustering();
        assertEquals(0, c.getNumberClusters());
        assertEquals(0d, alg.getModularity(), 1e-9);
    }

    @Test
    public void isolatedVerticesEachOwnCluster()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        LouvainClustering<Integer, DefaultEdge> alg = new LouvainClustering<>(g, new Random(SEED));
        Clustering<Integer> c = alg.getClustering();
        assertEquals(4, c.getNumberClusters());
        assertEquals(0d, alg.getModularity(), 1e-9);
    }

    @Test
    public void selfLoopsAreAccepted()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        g.addEdge(3, 4);
        g.addEdge(0, 0); // self-loop
        g.addEdge(5, 5);

        Clustering<Integer> c = new LouvainClustering<>(g, new Random(SEED)).getClustering();
        assertEquals(2, c.getNumberClusters());
        assertClusteringIsPartition(g, c);
    }

    @Test
    public void clusteringIsAlwaysAValidPartition()
    {
        Random gen = new Random(99);
        for (int t = 0; t < 20; t++) {
            Graph<Integer, DefaultEdge> g = randomUndirected(25, 0.2, gen);
            Clustering<Integer> c = new LouvainClustering<>(g, new Random(SEED)).getClustering();
            assertClusteringIsPartition(g, c);
        }
    }

    @Test
    public void getModularityMatchesMeasurer()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        g.addEdge(3, 4);

        LouvainClustering<Integer, DefaultEdge> alg = new LouvainClustering<>(g, new Random(SEED));
        double reported = alg.getModularity();
        double recomputed =
            new UndirectedModularityMeasurer<>(g).modularity(alg.getClustering().getClusters());
        assertEquals(recomputed, reported, 1e-12);
    }

    @Test
    public void modularityBeatsAllSingletonsPartition()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        addClique(g, 8, 9, 10, 11);
        g.addEdge(3, 4);
        g.addEdge(7, 8);

        LouvainClustering<Integer, DefaultEdge> alg = new LouvainClustering<>(g, new Random(SEED));
        double louvain = alg.getModularity();

        UndirectedModularityMeasurer<Integer, DefaultEdge> measurer =
            new UndirectedModularityMeasurer<>(g);
        List<Set<Integer>> singletons = new ArrayList<>();
        for (Integer v : g.vertexSet()) {
            singletons.add(Set.of(v));
        }
        assertTrue(louvain > measurer.modularity(singletons), "Louvain should beat all-singletons");
        assertTrue(louvain > 0.3, "planted partition should have clearly positive modularity");
    }

    @Test
    public void directedGraphIsRejected()
    {
        Graph<Integer, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);
        g.addVertex(0);
        g.addVertex(1);
        g.addEdge(0, 1);
        assertThrows(IllegalArgumentException.class, () -> new LouvainClustering<>(g));
    }

    @Test
    public void nullRngIsRejected()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        g.addVertex(0);
        assertThrows(NullPointerException.class, () -> new LouvainClustering<>(g, null));
    }

    @Test
    public void negativeToleranceIsRejected()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        g.addVertex(0);
        assertThrows(
            IllegalArgumentException.class,
            () -> new LouvainClustering<>(g, new Random(SEED), -1d));
    }

    // ---- helpers ----

    private static Graph<Integer, DefaultEdge> unweighted()
    {
        return GraphTypeBuilder.undirected().allowingMultipleEdges(true).allowingSelfLoops(true)
            .weighted(false).edgeSupplier(SupplierUtil.DEFAULT_EDGE_SUPPLIER)
            .vertexSupplier(SupplierUtil.createIntegerSupplier()).buildGraph();
    }

    private static Graph<Integer, DefaultEdge> weighted()
    {
        return GraphTypeBuilder.undirected().allowingMultipleEdges(false).allowingSelfLoops(true)
            .weighted(true).edgeSupplier(SupplierUtil.DEFAULT_EDGE_SUPPLIER)
            .vertexSupplier(SupplierUtil.createIntegerSupplier()).buildGraph();
    }

    private static void addClique(Graph<Integer, DefaultEdge> g, int... vs)
    {
        for (int v : vs) {
            if (!g.containsVertex(v)) {
                g.addVertex(v);
            }
        }
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                g.addEdge(vs[i], vs[j]);
            }
        }
    }

    private static void setEdge(Graph<Integer, DefaultEdge> g, int u, int v, double w)
    {
        DefaultEdge e = g.addEdge(u, v);
        g.setEdgeWeight(e, w);
    }

    private static Graph<Integer, DefaultEdge> randomUndirected(int n, double p, Random rng)
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (rng.nextDouble() < p) {
                    g.addEdge(i, j);
                }
            }
        }
        return g;
    }

    private static void assertClusteringIsPartition(
        Graph<Integer, DefaultEdge> g, Clustering<Integer> c)
    {
        Set<Integer> seen = new HashSet<>();
        for (Set<Integer> cluster : c.getClusters()) {
            for (Integer v : cluster) {
                assertTrue(seen.add(v), "vertex " + v + " appears in more than one cluster");
            }
        }
        assertEquals(g.vertexSet(), seen, "clustering must cover every vertex exactly once");
    }
}
