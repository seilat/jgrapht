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

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests asserting that {@link YenAStarKShortestPath} produces the same ordered sequence
 * of path weights as {@link YenKShortestPath} on a wide variety of graphs.
 */
public class YenAStarKShortestPathTest
{
    private static final double EPS = 1e-9;

    @Test
    public void testNegativeKThrows()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graphs.addEdgeWithVertices(g, 1, 2, 1.0);
        assertThrows(
            IllegalArgumentException.class,
            () -> new YenAStarKShortestPath<>(g).getPaths(1, 2, -1));
    }

    @Test
    public void testKZeroReturnsEmpty()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(1);
        g.addVertex(2);
        List<GraphPath<Integer, DefaultWeightedEdge>> paths =
            new YenAStarKShortestPath<>(g).getPaths(1, 2, 0);
        assertEquals(0, paths.size());
    }

    @Test
    public void testMissingSourceThrows()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(2);
        assertThrows(
            IllegalArgumentException.class, () -> new YenAStarKShortestPath<>(g).getPaths(1, 2, 1));
    }

    @Test
    public void testMissingSinkThrows()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(1);
        assertThrows(
            IllegalArgumentException.class, () -> new YenAStarKShortestPath<>(g).getPaths(1, 2, 1));
    }

    @Test
    public void testUnreachableSink()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(1);
        g.addVertex(2);
        List<GraphPath<Integer, DefaultWeightedEdge>> paths =
            new YenAStarKShortestPath<>(g).getPaths(1, 2, 5);
        assertEquals(0, paths.size());
    }

    @Test
    public void testSimpleDiamondMatchesStandardYen()
    {
        // 1 -> 2 -> 4, 1 -> 3 -> 4, 1 -> 4 (longer direct)
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v : new int[] { 1, 2, 3, 4 }) {
            g.addVertex(v);
        }
        Graphs.addEdge(g, 1, 2, 1.0);
        Graphs.addEdge(g, 2, 4, 2.0);
        Graphs.addEdge(g, 1, 3, 2.0);
        Graphs.addEdge(g, 3, 4, 1.0);
        Graphs.addEdge(g, 1, 4, 10.0);
        assertSamePathWeights(g, 1, 4, 5);
    }

    @Test
    public void testTieEqualWeights()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v : new int[] { 1, 2, 3, 4 }) {
            g.addVertex(v);
        }
        Graphs.addEdge(g, 1, 2, 1.0);
        Graphs.addEdge(g, 2, 4, 1.0);
        Graphs.addEdge(g, 1, 3, 1.0);
        Graphs.addEdge(g, 3, 4, 1.0);
        assertSamePathWeights(g, 1, 4, 4);
    }

    @Test
    public void testCyclicGraph()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v : new int[] { 1, 2, 3 }) {
            g.addVertex(v);
        }
        Graphs.addEdge(g, 1, 2, 1.0);
        Graphs.addEdge(g, 2, 3, 1.0);
        Graphs.addEdge(g, 3, 1, 1.0);
        Graphs.addEdge(g, 1, 3, 5.0);
        assertSamePathWeights(g, 1, 3, 5);
    }

    @Test
    public void testZeroWeightEdges()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v : new int[] { 1, 2, 3, 4 }) {
            g.addVertex(v);
        }
        Graphs.addEdge(g, 1, 2, 0.0);
        Graphs.addEdge(g, 2, 4, 0.0);
        Graphs.addEdge(g, 1, 3, 0.0);
        Graphs.addEdge(g, 3, 4, 0.0);
        Graphs.addEdge(g, 1, 4, 1.0);
        assertSamePathWeights(g, 1, 4, 5);
    }

    @Test
    public void testSingleEdgeGraph()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(1);
        g.addVertex(2);
        Graphs.addEdge(g, 1, 2, 7.5);
        assertSamePathWeights(g, 1, 2, 5);
    }

    @Test
    public void testKLargerThanTotalPaths()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v : new int[] { 1, 2, 3, 4 }) {
            g.addVertex(v);
        }
        Graphs.addEdge(g, 1, 2, 1.0);
        Graphs.addEdge(g, 2, 4, 1.0);
        Graphs.addEdge(g, 1, 3, 2.0);
        Graphs.addEdge(g, 3, 4, 2.0);
        Graphs.addEdge(g, 1, 4, 10.0);
        assertSamePathWeights(g, 1, 4, 100);
    }

    @Test
    public void testPathChainReturnsSinglePath()
    {
        Graph<Integer, DefaultWeightedEdge> g = pathChain(20, 99L);
        List<GraphPath<Integer, DefaultWeightedEdge>> paths =
            new YenAStarKShortestPath<>(g).getPaths(0, 19, 5);
        assertEquals(1, paths.size());
    }

    @Test
    public void testLayeredDagSeveralK()
    {
        Graph<Integer, DefaultWeightedEdge> g = layeredDag(4, 4, 12345L);
        for (int k : new int[] { 1, 2, 5, 10 }) {
            assertSamePathWeights(g, 0, sinkOf(g), k);
        }
    }

    @Test
    public void testRandomDag()
    {
        Graph<Integer, DefaultWeightedEdge> g = randomDagWithChain(80, 160, 99L);
        assertSamePathWeights(g, 0, 79, 20);
    }

    @Test
    public void testGridGraph()
    {
        Graph<Integer, DefaultWeightedEdge> g = grid(8, 8, 7L);
        assertSamePathWeights(g, 0, 8 * 8 - 1, 10);
    }

    @Test
    public void testLargeKDenseDag()
    {
        Graph<Integer, DefaultWeightedEdge> g = layeredDag(4, 5, 314L);
        assertSamePathWeights(g, 0, sinkOf(g), 500);
    }

    /**
     * Fuzz test against {@link YenKShortestPath} on random DAGs of varied size and density.
     */
    @Test
    public void testFuzzRandomDagsAgainstStandardYen()
    {
        Random meta = new Random(20260515L);
        int totalCases = 30;
        for (int caseIdx = 0; caseIdx < totalCases; caseIdx++) {
            int n = 8 + meta.nextInt(60);
            int extraEdges = meta.nextInt(2 * n);
            int k = 1 + meta.nextInt(20);
            long seed = meta.nextLong();
            Graph<Integer, DefaultWeightedEdge> g = randomDagWithChain(n, extraEdges, seed);
            String label = String.format(
                "case=%d n=%d extras=%d k=%d seed=%d", caseIdx, n, extraEdges, k, seed);
            assertSameAsStandardYen(g, 0, n - 1, k, label);
        }
    }

    @Test
    public void testFuzzGridsAgainstStandardYen()
    {
        long[] seeds = { 11L, 22L, 33L };
        for (long seed : seeds) {
            for (int rows : new int[] { 3, 5, 8 }) {
                for (int cols : new int[] { 3, 5, 8 }) {
                    Graph<Integer, DefaultWeightedEdge> g = grid(rows, cols, seed);
                    String label = String.format("grid %dx%d seed=%d", rows, cols, seed);
                    assertSameAsStandardYen(g, 0, rows * cols - 1, 8, label);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private static <V, E> void assertSamePathWeights(Graph<V, E> g, V src, V sink, int k)
    {
        List<Double> std = weights(new YenKShortestPath<>(g).getPaths(src, sink, k));
        List<Double> yenA = weights(new YenAStarKShortestPath<>(g).getPaths(src, sink, k));
        assertEqualsWeights(std, yenA, "standard vs Yen+A*");
    }

    private static void assertSameAsStandardYen(
        Graph<Integer, DefaultWeightedEdge> g, Integer source, Integer sink, int k, String label)
    {
        List<Double> std = weights(new YenKShortestPath<>(g).getPaths(source, sink, k));
        List<Double> yenA = weights(new YenAStarKShortestPath<>(g).getPaths(source, sink, k));
        assertEqualsWeights(std, yenA, label);
    }

    private static void assertEqualsWeights(List<Double> std, List<Double> got, String label)
    {
        assertEquals(std.size(), got.size(), label + ": path count");
        for (int i = 0; i < std.size(); i++) {
            assertEquals(std.get(i), got.get(i), EPS, label + ": weight at index " + i);
        }
    }

    private static <V, E> List<Double> weights(List<GraphPath<V, E>> paths)
    {
        List<Double> w = new ArrayList<>(paths.size());
        for (GraphPath<V, E> p : paths) {
            w.add(p.getWeight());
        }
        return w;
    }

    private static int sinkOf(Graph<Integer, ?> g)
    {
        int max = 0;
        for (Integer v : g.vertexSet()) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    static Graph<Integer, DefaultWeightedEdge> pathChain(int n, long seed)
    {
        Random rnd = new Random(seed);
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i + 1 < n; i++) {
            Graphs.addEdge(g, i, i + 1, 1.0 + rnd.nextInt(5));
        }
        return g;
    }

    static Graph<Integer, DefaultWeightedEdge> layeredDag(int layers, int width, long seed)
    {
        Random rnd = new Random(seed);
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        int src = 0;
        g.addVertex(src);
        int next = 1;
        int[][] layerVerts = new int[layers][width];
        for (int l = 0; l < layers; l++) {
            for (int j = 0; j < width; j++) {
                layerVerts[l][j] = next;
                g.addVertex(next++);
            }
        }
        int sink = next;
        g.addVertex(sink);
        for (int j = 0; j < width; j++) {
            Graphs.addEdge(g, src, layerVerts[0][j], 1.0 + rnd.nextInt(5));
        }
        for (int l = 0; l + 1 < layers; l++) {
            for (int j = 0; j < width; j++) {
                for (int j2 = 0; j2 < width; j2++) {
                    if (rnd.nextDouble() < 0.5) {
                        Graphs.addEdge(
                            g, layerVerts[l][j], layerVerts[l + 1][j2], 1.0 + rnd.nextInt(5));
                    }
                }
            }
        }
        for (int j = 0; j < width; j++) {
            Graphs.addEdge(g, layerVerts[layers - 1][j], sink, 1.0 + rnd.nextInt(5));
        }
        return g;
    }

    /**
     * DAG with a guaranteed source-to-sink chain (0 -&gt; 1 -&gt; ... -&gt; n-1) plus
     * {@code extraEdges} random forward edges.
     */
    static Graph<Integer, DefaultWeightedEdge> randomDagWithChain(int n, int extraEdges, long seed)
    {
        Random rnd = new Random(seed);
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i + 1 < n; i++) {
            Graphs.addEdge(g, i, i + 1, 1.0 + rnd.nextInt(10));
        }
        int added = 0;
        int safety = 0;
        while (added < extraEdges && safety < extraEdges * 10) {
            safety++;
            int a = rnd.nextInt(n);
            int b = rnd.nextInt(n);
            if (a >= b) {
                continue;
            }
            if (g.containsEdge(a, b)) {
                continue;
            }
            Graphs.addEdge(g, a, b, 1.0 + rnd.nextInt(10));
            added++;
        }
        return g;
    }

    static Graph<Integer, DefaultWeightedEdge> grid(int rows, int cols, long seed)
    {
        Random rnd = new Random(seed);
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < rows * cols; i++) {
            g.addVertex(i);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int v = r * cols + c;
                if (c + 1 < cols) {
                    Graphs.addEdge(g, v, v + 1, 1.0 + rnd.nextInt(5));
                }
                if (r + 1 < rows) {
                    Graphs.addEdge(g, v, v + cols, 1.0 + rnd.nextInt(5));
                }
            }
        }
        return g;
    }
}
