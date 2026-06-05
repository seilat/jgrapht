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
package org.jgrapht.alg.tour;

import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertHamiltonianPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HamiltonianPathEnumerator}: closed-form counts on structured graphs, exhaustive
 * set-equality against a permutation brute force on random graphs, orientation de-duplication, and
 * iterator semantics.
 */
public class HamiltonianPathEnumeratorTest
{

    private static final long SEED = 0xEEDC0FFEE1234567L;
    private static final int GRAPHS_PER_CONFIG = 15;

    // ---- closed-form counts --------------------------------------------------------------------

    @Test
    public void singleVertexHasOnePath()
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        g.addVertex(0);
        assertEquals(1L, new HamiltonianPathEnumerator<>(g).count());
    }

    @Test
    public void undirectedCompleteHasNFactorialOverTwo()
    {
        for (int n = 2; n <= 6; n++) {
            Graph<Integer, DefaultEdge> g = completeUndirected(n);
            assertEquals(factorial(n) / 2, new HamiltonianPathEnumerator<>(g).count(),
                () -> "K_undirected count");
        }
    }

    @Test
    public void directedCompleteHasNFactorial()
    {
        for (int n = 2; n <= 5; n++) {
            Graph<Integer, DefaultEdge> g = completeDirected(n);
            assertEquals(factorial(n), new HamiltonianPathEnumerator<>(g).count(),
                () -> "complete digraph count");
        }
    }

    @Test
    public void pathGraphHasExactlyOne()
    {
        assertEquals(1L, new HamiltonianPathEnumerator<>(undirectedPath(6)).count());
    }

    @Test
    public void undirectedCycleHasNPaths()
    {
        for (int n = 3; n <= 6; n++) {
            Graph<Integer, DefaultEdge> g = undirectedPath(n);
            g.addEdge(n - 1, 0);
            assertEquals((long) n, new HamiltonianPathEnumerator<>(g).count(), "C_n count");
        }
    }

    @Test
    public void disconnectedHasNone()
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        g.addEdge(0, 1);
        g.addEdge(2, 3);
        assertEquals(0L, new HamiltonianPathEnumerator<>(g).count());
        assertFalse(new HamiltonianPathEnumerator<>(g).iterator().hasNext());
    }

    @Test
    public void starWithMoreThanTwoLeavesHasNone()
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i <= 3; i++) {
            g.addVertex(i);
        }
        g.addEdge(0, 1);
        g.addEdge(0, 2);
        g.addEdge(0, 3);
        assertEquals(0L, new HamiltonianPathEnumerator<>(g).count());
    }

    // ---- structure and de-duplication ----------------------------------------------------------

    @Test
    public void everyEmittedPathIsValidAndDistinct()
    {
        Graph<Integer, DefaultEdge> g = completeUndirected(5);
        Set<List<Integer>> canonical = new HashSet<>();
        for (GraphPath<Integer, DefaultEdge> path : new HamiltonianPathEnumerator<>(g)) {
            assertHamiltonianPath(g, path);
            assertTrue(canonical.add(canonical(path.getVertexList(), false)),
                "no undirected path (or its reverse) should be emitted twice");
        }
        assertEquals(factorial(5) / 2, (long) canonical.size());
    }

    // ---- iterator semantics --------------------------------------------------------------------

    @Test
    public void iteratorThrowsAtEnd()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(3);
        Iterator<GraphPath<Integer, DefaultEdge>> it = new HamiltonianPathEnumerator<>(g).iterator();
        assertTrue(it.hasNext());
        assertTrue(it.hasNext()); // idempotent
        it.next();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    public void independentIteratorsDoNotInterfere()
    {
        Graph<Integer, DefaultEdge> g = completeUndirected(4);
        HamiltonianPathEnumerator<Integer, DefaultEdge> enumerator = new HamiltonianPathEnumerator<>(g);
        Iterator<GraphPath<Integer, DefaultEdge>> a = enumerator.iterator();
        Iterator<GraphPath<Integer, DefaultEdge>> b = enumerator.iterator();
        a.next(); // advance one only
        long viaB = 0;
        while (b.hasNext()) {
            b.next();
            viaB++;
        }
        assertEquals(factorial(4) / 2, viaB, "second iterator sees the full set");
    }

    @Test
    public void constructorRejectsEmptyAndNull()
    {
        assertThrows(NullPointerException.class, () -> new HamiltonianPathEnumerator<>(null));
        Graph<Integer, DefaultEdge> empty = new SimpleGraph<>(DefaultEdge.class);
        assertThrows(IllegalArgumentException.class, () -> new HamiltonianPathEnumerator<>(empty));
    }

    // ---- random cross-validation against brute-force permutation enumeration --------------------

    @Test
    public void undirectedRandomGraphsMatchBruteForce()
    {
        Random random = new Random(SEED);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.4, 0.7 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runOne(randomUndirected(n, p, random), false);
                }
            }
        }
    }

    @Test
    public void directedRandomGraphsMatchBruteForce()
    {
        Random random = new Random(SEED ^ 0x5A5A5A5AL);
        for (int n = 2; n <= 6; n++) {
            for (double p : new double[] { 0.4, 0.7 }) {
                for (int t = 0; t < GRAPHS_PER_CONFIG; t++) {
                    runOne(randomDirected(n, p, random), true);
                }
            }
        }
    }

    private void runOne(Graph<Integer, DefaultEdge> graph, boolean directed)
    {
        Set<List<Integer>> enumerated = new HashSet<>();
        List<List<Integer>> rawSequences = new ArrayList<>();
        for (GraphPath<Integer, DefaultEdge> path : new HamiltonianPathEnumerator<>(graph)) {
            assertHamiltonianPath(graph, path);
            rawSequences.add(path.getVertexList());
            enumerated.add(canonical(path.getVertexList(), directed));
        }
        assertEquals(rawSequences.size(), enumerated.size(),
            () -> "enumerator emitted a duplicate path on " + graph);

        Set<List<Integer>> brute = bruteForce(graph, directed);
        assertEquals(brute, enumerated, () -> "enumeration disagrees with brute force on " + graph);
    }

    private Set<List<Integer>> bruteForce(Graph<Integer, DefaultEdge> graph, boolean directed)
    {
        List<Integer> vertices = new ArrayList<>(graph.vertexSet());
        Set<List<Integer>> result = new HashSet<>();
        permute(graph, vertices, 0, directed, result);
        return result;
    }

    private void permute(
        Graph<Integer, DefaultEdge> graph, List<Integer> v, int from, boolean directed,
        Set<List<Integer>> out)
    {
        if (from == v.size()) {
            for (int i = 1; i < v.size(); i++) {
                if (!graph.containsEdge(v.get(i - 1), v.get(i))) {
                    return;
                }
            }
            out.add(canonical(v, directed));
            return;
        }
        for (int i = from; i < v.size(); i++) {
            Collections.swap(v, from, i);
            permute(graph, v, from + 1, directed, out);
            Collections.swap(v, from, i);
        }
    }

    /**
     * Canonical key for a path: the sequence itself for directed graphs; for undirected graphs the
     * lexicographically smaller of the sequence and its reverse, so a path and its reverse map to
     * the same key.
     */
    private List<Integer> canonical(List<Integer> sequence, boolean directed)
    {
        if (directed) {
            return new ArrayList<>(sequence);
        }
        List<Integer> reversed = new ArrayList<>(sequence);
        Collections.reverse(reversed);
        return compare(sequence, reversed) <= 0 ? new ArrayList<>(sequence) : reversed;
    }

    private int compare(List<Integer> a, List<Integer> b)
    {
        for (int i = 0; i < a.size(); i++) {
            int c = Integer.compare(a.get(i), b.get(i));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    // ---- graph builders ------------------------------------------------------------------------

    private long factorial(int k)
    {
        long f = 1L;
        for (int i = 2; i <= k; i++) {
            f *= i;
        }
        return f;
    }

    private Graph<Integer, DefaultEdge> undirectedPath(int n)
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 1; i < n; i++) {
            g.addEdge(i - 1, i);
        }
        return g;
    }

    private Graph<Integer, DefaultEdge> completeUndirected(int n)
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(i, j);
            }
        }
        return g;
    }

    private Graph<Integer, DefaultEdge> completeDirected(int n)
    {
        Graph<Integer, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    g.addEdge(i, j);
                }
            }
        }
        return g;
    }

    private Graph<Integer, DefaultEdge> randomUndirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (random.nextDouble() < p) {
                    g.addEdge(i, j);
                }
            }
        }
        return g;
    }

    private Graph<Integer, DefaultEdge> randomDirected(int n, double p, Random random)
    {
        Graph<Integer, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && random.nextDouble() < p) {
                    g.addEdge(i, j);
                }
            }
        }
        return g;
    }
}
