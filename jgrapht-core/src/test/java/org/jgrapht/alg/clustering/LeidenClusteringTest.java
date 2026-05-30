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
import org.jgrapht.alg.connectivity.*;
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
 * Tests for {@link LeidenClustering}, including the headline well-connected-community guarantee
 * that distinguishes Leiden from {@link LouvainClustering}.
 *
 * @author seilat
 */
public class LeidenClusteringTest
{
    private static final long SEED = 0x1234ABCDL;

    @Test
    public void twoCliquesJoinedByOneEdge()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        g.addEdge(3, 4);

        Clustering<Integer> c = new LeidenClustering<>(g, new Random(SEED)).getClustering();
        assertEquals(2, c.getNumberClusters());
        assertEquals(
            Set.of(Set.of(0, 1, 2, 3), Set.of(4, 5, 6, 7)), new HashSet<>(c.getClusters()));
        assertAllCommunitiesConnected(g, c);
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

        Clustering<Integer> c = new LeidenClustering<>(g, new Random(SEED)).getClustering();
        assertEquals(3, c.getNumberClusters());
        assertAllCommunitiesConnected(g, c);
    }

    @Test
    public void completeGraphIsOneCommunity()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3, 4, 5);

        LeidenClustering<Integer, DefaultEdge> alg = new LeidenClustering<>(g, new Random(SEED));
        Clustering<Integer> c = alg.getClustering();
        assertEquals(1, c.getNumberClusters());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), c.getClusters().get(0));
        assertEquals(0d, alg.getModularity(), 1e-9);
    }

    @Test
    public void weightedGraphFollowsHeavyEdges()
    {
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
        setEdge(g, 2, 3, 1);

        Clustering<Integer> c = new LeidenClustering<>(g, new Random(SEED)).getClustering();
        assertEquals(2, c.getNumberClusters());
        assertEquals(Set.of(Set.of(0, 1, 2), Set.of(3, 4, 5)), new HashSet<>(c.getClusters()));
    }

    /**
     * The defining Leiden guarantee: every returned community is internally connected. Louvain
     * does not guarantee this. Asserted across structured and random graphs.
     */
    @Test
    public void everyCommunityIsConnected()
    {
        List<Graph<Integer, DefaultEdge>> graphs = new ArrayList<>();
        graphs.add(twoCliquesGraph());
        graphs.add(barbellWithSingleConnector());
        Random gen = new Random(7);
        for (int t = 0; t < 30; t++) {
            graphs.add(randomUndirected(30, 0.15, gen));
        }
        for (Graph<Integer, DefaultEdge> g : graphs) {
            Clustering<Integer> c = new LeidenClustering<>(g, new Random(SEED)).getClustering();
            assertClusteringIsPartition(g, c);
            assertAllCommunitiesConnected(g, c);
        }
    }

    @Test
    public void modularityIsPositiveOnPlantedPartition()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        addClique(g, 8, 9, 10, 11);
        g.addEdge(3, 4);
        g.addEdge(7, 8);

        LeidenClustering<Integer, DefaultEdge> alg = new LeidenClustering<>(g, new Random(SEED));
        assertTrue(alg.getModularity() > 0.3, "planted partition should have clearly positive Q");
        assertAllCommunitiesConnected(g, alg.getClustering());
    }

    @Test
    public void modularityNotWorseThanLouvain()
    {
        Random gen = new Random(123);
        for (int t = 0; t < 10; t++) {
            Graph<Integer, DefaultEdge> g = randomUndirected(40, 0.1, gen);
            double leiden = new LeidenClustering<>(g, new Random(SEED)).getModularity();
            double louvain = new LouvainClustering<>(g, new Random(SEED)).getModularity();
            // Leiden trades a small amount of modularity for guaranteed well-connected communities;
            // on arbitrary (near-random) graphs it stays competitive with Louvain rather than
            // strictly dominating, so allow a small absolute margin.
            assertTrue(
                leiden >= louvain - 0.02,
                () -> "Leiden " + leiden + " materially below Louvain " + louvain);
        }
    }

    @Test
    public void seedYieldsDeterministicResult()
    {
        Graph<Integer, DefaultEdge> g = barbellWithSingleConnector();
        List<Set<Integer>> a =
            new LeidenClustering<>(g, new Random(SEED)).getClustering().getClusters();
        List<Set<Integer>> b =
            new LeidenClustering<>(g, new Random(SEED)).getClustering().getClusters();
        assertEquals(a, b);
    }

    @Test
    public void singleVertex()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        g.addVertex(0);
        LeidenClustering<Integer, DefaultEdge> alg = new LeidenClustering<>(g, new Random(SEED));
        assertEquals(1, alg.getClustering().getNumberClusters());
        assertEquals(0d, alg.getModularity(), 1e-9);
    }

    @Test
    public void emptyGraph()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        LeidenClustering<Integer, DefaultEdge> alg = new LeidenClustering<>(g, new Random(SEED));
        assertEquals(0, alg.getClustering().getNumberClusters());
        assertEquals(0d, alg.getModularity(), 1e-9);
    }

    @Test
    public void isolatedVerticesEachOwnCluster()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        assertEquals(4, new LeidenClustering<>(g, new Random(SEED)).getClustering()
            .getNumberClusters());
    }

    @Test
    public void zeroWeightEdgesGiveZeroModularityNotNaN()
    {
        Graph<Integer, DefaultEdge> g = weighted();
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        setEdge(g, 0, 1, 0d);
        setEdge(g, 1, 2, 0d);
        setEdge(g, 2, 3, 0d);
        LeidenClustering<Integer, DefaultEdge> alg = new LeidenClustering<>(g, new Random(SEED));
        assertEquals(0d, alg.getModularity(), 0d);
        assertClusteringIsPartition(g, alg.getClustering());
    }

    @Test
    public void negativeEdgeWeightIsRejected()
    {
        Graph<Integer, DefaultEdge> g = weighted();
        g.addVertex(0);
        g.addVertex(1);
        setEdge(g, 0, 1, -1d);
        LeidenClustering<Integer, DefaultEdge> alg = new LeidenClustering<>(g, new Random(SEED));
        assertThrows(IllegalArgumentException.class, alg::getClustering);
    }

    @Test
    public void directedGraphIsRejected()
    {
        Graph<Integer, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);
        g.addVertex(0);
        g.addVertex(1);
        g.addEdge(0, 1);
        assertThrows(IllegalArgumentException.class, () -> new LeidenClustering<>(g));
    }

    @Test
    public void nullRngIsRejected()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        g.addVertex(0);
        assertThrows(NullPointerException.class, () -> new LeidenClustering<>(g, null));
    }

    @Test
    public void negativeToleranceIsRejected()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        g.addVertex(0);
        assertThrows(
            IllegalArgumentException.class,
            () -> new LeidenClustering<>(g, new Random(SEED), -1d));
    }

    // ---- helpers ----

    private static Graph<Integer, DefaultEdge> twoCliquesGraph()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        g.addEdge(3, 4);
        return g;
    }

    /**
     * Two 4-cliques whose only link is through a single connector vertex (8) attached to one vertex
     * of each clique. A modularity optimiser may pull {@code 8} into one clique, which historically
     * stresses connectivity of the other grouping.
     */
    private static Graph<Integer, DefaultEdge> barbellWithSingleConnector()
    {
        Graph<Integer, DefaultEdge> g = unweighted();
        addClique(g, 0, 1, 2, 3);
        addClique(g, 4, 5, 6, 7);
        g.addVertex(8);
        g.addEdge(0, 8);
        g.addEdge(4, 8);
        return g;
    }

    private static Graph<Integer, DefaultEdge> unweighted()
    {
        return GraphTypeBuilder
            .undirected().allowingMultipleEdges(true).allowingSelfLoops(true).weighted(false)
            .edgeSupplier(SupplierUtil.DEFAULT_EDGE_SUPPLIER)
            .vertexSupplier(SupplierUtil.createIntegerSupplier()).buildGraph();
    }

    private static Graph<Integer, DefaultEdge> weighted()
    {
        return GraphTypeBuilder
            .undirected().allowingMultipleEdges(false).allowingSelfLoops(true).weighted(true)
            .edgeSupplier(SupplierUtil.DEFAULT_EDGE_SUPPLIER)
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
                assertTrue(seen.add(v), "vertex " + v + " in more than one cluster");
            }
        }
        assertEquals(g.vertexSet(), seen, "clustering must cover every vertex exactly once");
    }

    private static void assertAllCommunitiesConnected(
        Graph<Integer, DefaultEdge> g, Clustering<Integer> c)
    {
        for (Set<Integer> cluster : c.getClusters()) {
            Graph<Integer, DefaultEdge> sub = new AsSubgraph<>(g, cluster);
            assertTrue(
                new ConnectivityInspector<>(sub).isConnected(),
                () -> "community is not connected: " + cluster);
        }
    }
}
