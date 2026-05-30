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
package org.jgrapht.alg.isomorphism;

import org.jgrapht.*;
import org.jgrapht.alg.isomorphism.WeisfeilerLehmanGraphHash.*;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.*;

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

    @Test
    public void parallelEdgesCountedWithMultiplicity()
    {
        // a double edge between 0 and 1 must change the hash vs a single edge: parallel edges are
        // counted with multiplicity (unlike ColorRefinementAlgorithm, which deduplicates).
        Graph<Integer, DefaultEdge> doubled = new Multigraph<>(DefaultEdge.class);
        Graph<Integer, DefaultEdge> single = new Multigraph<>(DefaultEdge.class);
        for (int v = 0; v <= 2; v++) {
            doubled.addVertex(v);
            single.addVertex(v);
        }
        doubled.addEdge(0, 1);
        doubled.addEdge(0, 1); // parallel
        doubled.addEdge(1, 2);
        single.addEdge(0, 1);
        single.addEdge(1, 2);

        assertNotEquals(
            new WeisfeilerLehmanGraphHash<>(doubled).getHash(),
            new WeisfeilerLehmanGraphHash<>(single).getHash());
    }

    @Test
    public void undirectedSelfLoopHandled()
    {
        // a self-loop on vertex 0 is honoured and deterministic, and changes the hash vs no loop
        Graph<Integer, DefaultEdge> withLoop = new Pseudograph<>(DefaultEdge.class);
        Graph<Integer, DefaultEdge> noLoop = new Pseudograph<>(DefaultEdge.class);
        for (int v = 0; v <= 2; v++) {
            withLoop.addVertex(v);
            noLoop.addVertex(v);
        }
        withLoop.addEdge(0, 1);
        withLoop.addEdge(1, 2);
        withLoop.addEdge(0, 0); // self-loop
        noLoop.addEdge(0, 1);
        noLoop.addEdge(1, 2);

        String h = new WeisfeilerLehmanGraphHash<>(withLoop).getHash();
        assertNotNull(h);
        assertEquals(h, new WeisfeilerLehmanGraphHash<>(withLoop).getHash()); // deterministic
        assertNotEquals(h, new WeisfeilerLehmanGraphHash<>(noLoop).getHash());
    }

    @Test
    public void directedSelfLoopHandled()
    {
        Graph<Integer, DefaultEdge> withLoop = new DirectedPseudograph<>(DefaultEdge.class);
        Graph<Integer, DefaultEdge> noLoop = new DirectedPseudograph<>(DefaultEdge.class);
        for (int v = 0; v <= 2; v++) {
            withLoop.addVertex(v);
            noLoop.addVertex(v);
        }
        withLoop.addEdge(0, 1);
        withLoop.addEdge(1, 2);
        withLoop.addEdge(0, 0);
        noLoop.addEdge(0, 1);
        noLoop.addEdge(1, 2);

        String h = new WeisfeilerLehmanGraphHash<>(withLoop).getHash();
        assertNotNull(h);
        assertEquals(h, new WeisfeilerLehmanGraphHash<>(withLoop).getHash());
        assertNotEquals(h, new WeisfeilerLehmanGraphHash<>(noLoop).getHash());
    }

    @Test
    public void edgeWeightsAreIgnored()
    {
        // v1 is unweighted: two structurally identical graphs differing only in weights hash equal
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> light =
            new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> heavy =
            new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v = 0; v <= 3; v++) {
            light.addVertex(v);
            heavy.addVertex(v);
        }
        int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 } };
        for (int[] e : edges) {
            light.setEdgeWeight(light.addEdge(e[0], e[1]), 1.0);
            heavy.setEdgeWeight(heavy.addEdge(e[0], e[1]), 99.0);
        }

        assertEquals(
            new WeisfeilerLehmanGraphHash<>(light).getHash(),
            new WeisfeilerLehmanGraphHash<>(heavy).getHash());
    }

    @Test
    public void isolatedVerticesHandled()
    {
        // mix of connected and isolated vertices must not crash and stays deterministic
        Graph<Integer, DefaultEdge> g = undirected(new int[][] { { 0, 1 } }, 2, 3, 4);
        String h = new WeisfeilerLehmanGraphHash<>(g).getHash();
        assertNotNull(h);
        assertEquals(h, new WeisfeilerLehmanGraphHash<>(g).getHash());
        assertEquals(5, new WeisfeilerLehmanGraphHash<>(g).getVertexHashes().size());
    }

    @Test
    public void hashIsStableBeyondConvergence()
    {
        // colour refinement reaches a fixed point; once stable, more iterations add no information,
        // so the hash must be identical for any large iteration count (and never loop forever).
        Graph<Integer, DefaultEdge> g =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 } });
        String converged = new WeisfeilerLehmanGraphHash<>(g, 1000).getHash();
        assertEquals(converged, new WeisfeilerLehmanGraphHash<>(g, 50).getHash());
        assertEquals(converged, new WeisfeilerLehmanGraphHash<>(g, Integer.MAX_VALUE).getHash());
    }

    @Test
    public void directedIsomorphicGraphsHashEqual()
    {
        Graph<Integer, DefaultEdge> g1 = new SimpleDirectedGraph<>(DefaultEdge.class);
        Graph<Integer, DefaultEdge> g2 = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (int v = 0; v <= 3; v++) {
            g1.addVertex(v);
            g2.addVertex(v + 10);
        }
        // directed path 0->1->2->3 vs relabelled 10->11->12->13 added in scrambled order
        g1.addEdge(0, 1);
        g1.addEdge(1, 2);
        g1.addEdge(2, 3);
        g2.addEdge(12, 13);
        g2.addEdge(10, 11);
        g2.addEdge(11, 12);

        assertEquals(
            new WeisfeilerLehmanGraphHash<>(g1).getHash(),
            new WeisfeilerLehmanGraphHash<>(g2).getHash());
    }

    @Test
    public void vertexHashesAreUnmodifiableAndFullLength()
    {
        Graph<Integer, DefaultEdge> g =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 2, 0 } });
        Map<Integer, String> colors = new WeisfeilerLehmanGraphHash<>(g).getVertexHashes();

        assertThrows(UnsupportedOperationException.class, () -> colors.put(99, "x"));
        // full SHA-256 hex (256 bits = 64 chars), not truncated, to avoid label collisions
        for (String c : colors.values()) {
            assertEquals(64, c.length());
        }
    }

    @Test
    public void vertexLabelFunctionFoldsIntoHash()
    {
        Graph<Integer, DefaultEdge> path =
            undirected(new int[][] { { 0, 1 }, { 1, 2 } });
        Function<Integer, String> uniform = v -> "x";
        Function<Integer, String> distinct = v -> "v" + v;

        String hUniform = new WeisfeilerLehmanGraphHash<>(
            path, 3, uniform, null, ParallelEdgeRule.COUNT).getHash();
        String hDistinct = new WeisfeilerLehmanGraphHash<>(
            path, 3, distinct, null, ParallelEdgeRule.COUNT).getHash();
        assertNotEquals(hUniform, hDistinct);

        // distinct vertex labels break the path's end-symmetry: ends get different colours
        Map<Integer, String> uniformColors = new WeisfeilerLehmanGraphHash<>(
            path, 3, uniform, null, ParallelEdgeRule.COUNT).getVertexHashes();
        assertEquals(uniformColors.get(0), uniformColors.get(2));
        Map<Integer, String> distinctColors = new WeisfeilerLehmanGraphHash<>(
            path, 3, distinct, null, ParallelEdgeRule.COUNT).getVertexHashes();
        assertNotEquals(distinctColors.get(0), distinctColors.get(2));
    }

    @Test
    public void edgeLabelFunctionFoldsIntoHash()
    {
        Graph<Integer, DefaultEdge> path =
            undirected(new int[][] { { 0, 1 }, { 1, 2 } });
        DefaultEdge e01 = path.getEdge(0, 1);
        Function<DefaultEdge, String> twoColours = e -> e.equals(e01) ? "red" : "blue";
        Function<DefaultEdge, String> uniform = e -> "x";

        assertNotEquals(
            new WeisfeilerLehmanGraphHash<>(path, 3, null, twoColours, ParallelEdgeRule.COUNT)
                .getHash(),
            new WeisfeilerLehmanGraphHash<>(path, 3, null, uniform, ParallelEdgeRule.COUNT)
                .getHash());

        // differently-labelled incident edges break the end-symmetry of the path
        Map<Integer, String> colors = new WeisfeilerLehmanGraphHash<>(
            path, 3, null, twoColours, ParallelEdgeRule.COUNT).getVertexHashes();
        assertNotEquals(colors.get(0), colors.get(2));
    }

    @Test
    public void parallelEdgeRuleChangesAggregation()
    {
        // path 0-1-2-3 with the 0-1 edge doubled: the graph still refines to distinct colours,
        // but the neighbour multisets (COUNT) differ from the neighbour sets (IGNORE) on the way,
        // so the per-round histograms — and thus the graph hash — differ between the two rules.
        Graph<Integer, DefaultEdge> multi = new Multigraph<>(DefaultEdge.class);
        for (int v = 0; v <= 3; v++) {
            multi.addVertex(v);
        }
        multi.addEdge(0, 1);
        multi.addEdge(0, 1);
        multi.addEdge(1, 2);
        multi.addEdge(2, 3);

        String counted = new WeisfeilerLehmanGraphHash<>(
            multi, 3, null, null, ParallelEdgeRule.COUNT).getHash();
        String ignored = new WeisfeilerLehmanGraphHash<>(
            multi, 3, null, null, ParallelEdgeRule.IGNORE).getHash();
        assertNotEquals(counted, ignored);
        // deterministic under IGNORE
        assertEquals(ignored, new WeisfeilerLehmanGraphHash<>(
            multi, 3, null, null, ParallelEdgeRule.IGNORE).getHash());
    }

    @Test
    public void vertexHashSequencesStructure()
    {
        Graph<Integer, DefaultEdge> path =
            undirected(new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 } });
        WeisfeilerLehmanGraphHash<Integer, DefaultEdge> wl =
            new WeisfeilerLehmanGraphHash<>(path, 5);

        Map<Integer, List<String>> seqs = wl.getVertexHashSequences();
        int len = seqs.get(0).size();
        assertTrue(len >= 1);
        for (List<String> s : seqs.values()) {
            assertEquals(len, s.size()); // all vertices share the round count
        }

        // the last entry of each sequence equals the final vertex hash
        Map<Integer, String> finals = new WeisfeilerLehmanGraphHash<>(path, 5).getVertexHashes();
        for (Integer v : path.vertexSet()) {
            assertEquals(finals.get(v), seqs.get(v).get(len - 1));
        }

        // symmetric ends of the path share their entire colour sequence
        assertEquals(seqs.get(0), seqs.get(4));

        // both the map and the inner lists are unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> seqs.put(99, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> seqs.get(0).add("x"));
    }

    @Test
    public void nullParallelEdgeRuleRejected()
    {
        Graph<Integer, DefaultEdge> g = undirected(new int[][] { { 0, 1 } });
        assertThrows(
            NullPointerException.class,
            () -> new WeisfeilerLehmanGraphHash<>(g, 3, null, null, null));
    }
}
