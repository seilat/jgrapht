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
package org.jgrapht.alg.hash;

import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WeisfeilerLehmanGraphHash}.
 *
 * @author seilat
 */
public class WeisfeilerLehmanGraphHashTest
{
    private static Graph<Integer, DefaultEdge> undirected(int[][] edges, int... extraVertices)
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int v : extraVertices) {
            g.addVertex(v);
        }
        for (int[] e : edges) {
            g.addVertex(e[0]);
            g.addVertex(e[1]);
            g.addEdge(e[0], e[1]);
        }
        return g;
    }

    private static String hash(Graph<Integer, DefaultEdge> g)
    {
        return new WeisfeilerLehmanGraphHash<>(g).getHash();
    }

    @Test
    public void isomorphicGraphsHashEqual()
    {
        // path 0-1-2-3-4
        Graph<Integer, DefaultEdge> g1 =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 } });
        // same path under relabelling 0->10,1->11,... and with edges/vertices added in a
        // scrambled order
        Graph<Integer, DefaultEdge> g2 = new SimpleGraph<>(DefaultEdge.class);
        g2.addVertex(13);
        g2.addVertex(11);
        g2.addVertex(14);
        g2.addVertex(10);
        g2.addVertex(12);
        g2.addEdge(12, 13);
        g2.addEdge(10, 11);
        g2.addEdge(13, 14);
        g2.addEdge(11, 12);

        assertEquals(hash(g1), hash(g2));
    }

    @Test
    public void structurallyDifferentGraphsHashDiffer()
    {
        // path P5 (degrees 1,2,2,2,1) vs star S4 (degrees 4,1,1,1,1)
        Graph<Integer, DefaultEdge> path =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 } });
        Graph<Integer, DefaultEdge> star =
            undirected(new int[][] { { 0, 1 }, { 0, 2 }, { 0, 3 }, { 0, 4 } });

        assertNotEquals(hash(path), hash(star));
    }

    @Test
    public void insertionOrderInvariant()
    {
        int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 0 }, { 2, 3 }, { 3, 4 }, { 4, 2 } };
        Graph<Integer, DefaultEdge> forward = undirected(edges);

        int[][] reversedOrder = new int[edges.length][];
        for (int i = 0; i < edges.length; i++) {
            reversedOrder[i] = edges[edges.length - 1 - i];
        }
        Graph<Integer, DefaultEdge> backward = undirected(reversedOrder);

        assertEquals(hash(forward), hash(backward));
    }

    @Test
    public void hashIsDeterministicAcrossInstances()
    {
        Graph<Integer, DefaultEdge> g =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 } });
        assertEquals(hash(g), hash(g));
    }

    /**
     * 1-WL cannot distinguish two non-isomorphic regular graphs of the same degree: a single
     * 6-cycle and two disjoint triangles are both 2-regular on six vertices. Equal hashes here
     * document that hash equality is a necessary, not sufficient, condition for isomorphism.
     */
    @Test
    public void oneWlLimitationRegularGraphsCollide()
    {
        Graph<Integer, DefaultEdge> hexagon = undirected(
            new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 0 } });
        Graph<Integer, DefaultEdge> twoTriangles = undirected(
            new int[][] { { 0, 1 }, { 1, 2 }, { 2, 0 }, { 3, 4 }, { 4, 5 }, { 5, 3 } });

        assertEquals(hash(hexagon), hash(twoTriangles));
    }

    @Test
    public void iterationsZeroIsDegreeHistogramOnly()
    {
        // both graphs: four degree-1 vertices, two degree-2 vertices, four edges
        // A: P4 (0-1-2-3) plus a disjoint edge (4-5)
        Graph<Integer, DefaultEdge> a =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 4, 5 } });
        // B: two disjoint P3 paths (0-1-2) and (3-4-5)
        Graph<Integer, DefaultEdge> b =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 3, 4 }, { 4, 5 } });

        // identical degree multiset -> identical hash at iteration 0
        assertEquals(
            new WeisfeilerLehmanGraphHash<>(a, 0).getHash(),
            new WeisfeilerLehmanGraphHash<>(b, 0).getHash());
        // refinement separates them (neighbour-degree profiles differ)
        assertNotEquals(
            new WeisfeilerLehmanGraphHash<>(a, 2).getHash(),
            new WeisfeilerLehmanGraphHash<>(b, 2).getHash());
    }

    @Test
    public void directionMatters()
    {
        // out-star (centre 0 points to leaves) vs in-star (leaves point to centre 0).
        // These are non-isomorphic as digraphs — one centre is a source, the other a sink —
        // so distinguishing in- from out-neighbours must yield different hashes.
        Graph<Integer, DefaultEdge> outStar = new SimpleDirectedGraph<>(DefaultEdge.class);
        Graph<Integer, DefaultEdge> inStar = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (int v = 0; v <= 3; v++) {
            outStar.addVertex(v);
            inStar.addVertex(v);
        }
        for (int leaf = 1; leaf <= 3; leaf++) {
            outStar.addEdge(0, leaf);
            inStar.addEdge(leaf, 0);
        }

        assertNotEquals(
            new WeisfeilerLehmanGraphHash<>(outStar).getHash(),
            new WeisfeilerLehmanGraphHash<>(inStar).getHash());
    }

    @Test
    public void emptyGraphIsDeterministic()
    {
        Graph<Integer, DefaultEdge> e1 = new SimpleGraph<>(DefaultEdge.class);
        Graph<Integer, DefaultEdge> e2 = new SimpleGraph<>(DefaultEdge.class);
        String h = hash(e1);
        assertNotNull(h);
        assertEquals(h, hash(e2));
        assertTrue(new WeisfeilerLehmanGraphHash<>(e1).getVertexHashes().isEmpty());
    }

    @Test
    public void singleVertexGraph()
    {
        Graph<Integer, DefaultEdge> g = undirected(new int[0][], 42);
        assertNotNull(hash(g));
        Map<Integer, String> colors = new WeisfeilerLehmanGraphHash<>(g).getVertexHashes();
        assertEquals(1, colors.size());
    }

    @Test
    public void vertexHashesSizeAndSymmetry()
    {
        // star: centre 0, leaves 1..4 — all leaves are structurally identical
        Graph<Integer, DefaultEdge> star =
            undirected(new int[][] { { 0, 1 }, { 0, 2 }, { 0, 3 }, { 0, 4 } });
        Map<Integer, String> colors = new WeisfeilerLehmanGraphHash<>(star).getVertexHashes();

        assertEquals(5, colors.size());
        // the four leaves share a colour; the centre differs
        Set<String> leafColors = new HashSet<>();
        for (int leaf = 1; leaf <= 4; leaf++) {
            leafColors.add(colors.get(leaf));
        }
        assertEquals(1, leafColors.size());
        assertNotEquals(colors.get(0), colors.get(1));
    }

    @Test
    public void negativeIterationsRejected()
    {
        Graph<Integer, DefaultEdge> g = undirected(new int[][] { { 0, 1 } });
        assertThrows(
            IllegalArgumentException.class, () -> new WeisfeilerLehmanGraphHash<>(g, -1));
    }

    @Test
    public void nullGraphRejected()
    {
        assertThrows(
            NullPointerException.class, () -> new WeisfeilerLehmanGraphHash<Integer, DefaultEdge>(null));
    }
}
