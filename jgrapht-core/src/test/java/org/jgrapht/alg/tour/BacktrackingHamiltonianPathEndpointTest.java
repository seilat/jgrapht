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
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertHamiltonianPath;
import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertProvenAbsent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deterministic tests for the endpoint-constrained search variants of
 * {@link BacktrackingHamiltonianPath}: {@link BacktrackingHamiltonianPath#getPathFrom},
 * {@link BacktrackingHamiltonianPath#getPathTo} and
 * {@link BacktrackingHamiltonianPath#getPathBetween}.
 */
public class BacktrackingHamiltonianPathEndpointTest
{

    private BacktrackingHamiltonianPath<Integer, DefaultEdge> solver()
    {
        return new BacktrackingHamiltonianPath<>();
    }

    /**
     * Asserts the result is a valid Hamiltonian path whose first vertex is {@code start} (when
     * non-null) and whose last vertex is {@code end} (when non-null).
     */
    private void assertEndpoints(
        Graph<Integer, DefaultEdge> graph,
        HamiltonianPathSearchResult<Integer, DefaultEdge> result, Integer start, Integer end)
    {
        assertHamiltonianPath(graph, result);
        GraphPath<Integer, DefaultEdge> path = result.getPath().orElseThrow();
        if (start != null) {
            assertEquals(start, path.getStartVertex(), "start vertex mismatch");
        }
        if (end != null) {
            assertEquals(end, path.getEndVertex(), "end vertex mismatch");
        }
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

    private Graph<Integer, DefaultEdge> undirectedCycle(int n)
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(n);
        g.addEdge(n - 1, 0);
        return g;
    }

    private Graph<Integer, DefaultEdge> undirectedComplete(int n)
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

    private Graph<Integer, DefaultEdge> directedChain(int n)
    {
        Graph<Integer, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) {
            g.addVertex(i);
        }
        for (int i = 1; i < n; i++) {
            g.addEdge(i - 1, i);
        }
        return g;
    }

    // ---- undirected path graph: endpoints must be the two degree-1 ends ------------------------

    @Test
    public void undirectedPathBetweenEnds()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        assertEndpoints(g, solver().getPathBetween(g, 0, 4), 0, 4);
        assertEndpoints(g, solver().getPathBetween(g, 4, 0), 4, 0);
    }

    @Test
    public void undirectedPathBetweenInternalVertexAbsent()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        assertProvenAbsent(solver().getPathBetween(g, 0, 2));
        assertProvenAbsent(solver().getPathBetween(g, 1, 3));
    }

    @Test
    public void undirectedPathFromAndTo()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        assertEndpoints(g, solver().getPathFrom(g, 0), 0, null);
        assertEndpoints(g, solver().getPathFrom(g, 4), 4, null);
        assertEndpoints(g, solver().getPathTo(g, 4), null, 4);
        // an internal vertex can be neither the first nor the last vertex of the path
        assertProvenAbsent(solver().getPathFrom(g, 2));
        assertProvenAbsent(solver().getPathTo(g, 2));
    }

    // ---- complete graph: a path exists between every pair --------------------------------------

    @Test
    public void completeGraphBetweenAnyPair()
    {
        Graph<Integer, DefaultEdge> g = undirectedComplete(4);
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                if (a != b) {
                    assertEndpoints(g, solver().getPathBetween(g, a, b), a, b);
                }
            }
        }
        assertEndpoints(g, solver().getPathFrom(g, 2), 2, null);
        assertEndpoints(g, solver().getPathTo(g, 1), null, 1);
    }

    @Test
    public void sameEndpointsOnMultiVertexGraphAbsent()
    {
        Graph<Integer, DefaultEdge> g = undirectedComplete(4);
        assertProvenAbsent(solver().getPathBetween(g, 0, 0));
        assertProvenAbsent(solver().getPathBetween(g, 3, 3));
    }

    // ---- undirected cycle: Hamiltonian-path endpoints must be adjacent in the cycle ------------

    @Test
    public void cycleBetweenAdjacentFoundNonAdjacentAbsent()
    {
        Graph<Integer, DefaultEdge> g = undirectedCycle(5);
        // adjacent endpoints: removing edge (a,b) yields a Hamiltonian path with ends a and b
        assertEndpoints(g, solver().getPathBetween(g, 0, 1), 0, 1);
        assertEndpoints(g, solver().getPathBetween(g, 0, 4), 0, 4);
        // non-adjacent endpoints: no Hamiltonian path can have them as its two ends
        assertProvenAbsent(solver().getPathBetween(g, 0, 2));
        assertProvenAbsent(solver().getPathBetween(g, 1, 3));
    }

    // ---- directed chain: direction makes from/to/between asymmetric ----------------------------

    @Test
    public void directedChainRespectsDirection()
    {
        Graph<Integer, DefaultEdge> g = directedChain(4);
        assertEndpoints(g, solver().getPathFrom(g, 0), 0, null);
        assertEndpoints(g, solver().getPathTo(g, 3), null, 3);
        assertEndpoints(g, solver().getPathBetween(g, 0, 3), 0, 3);

        // wrong-direction constraints have no path
        assertProvenAbsent(solver().getPathFrom(g, 1));
        assertProvenAbsent(solver().getPathTo(g, 0));
        assertProvenAbsent(solver().getPathBetween(g, 3, 0));
    }

    // ---- single vertex -------------------------------------------------------------------------

    @Test
    public void singleVertexEndpoints()
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        g.addVertex(7);
        assertEndpoints(g, solver().getPathFrom(g, 7), 7, 7);
        assertEndpoints(g, solver().getPathTo(g, 7), 7, 7);
        assertEndpoints(g, solver().getPathBetween(g, 7, 7), 7, 7);
    }

    // ---- argument validation -------------------------------------------------------------------

    @Test
    public void vertexNotInGraphThrows()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(3);
        assertThrows(
            IllegalArgumentException.class, () -> solver().getPathFrom(g, 99));
        assertThrows(
            IllegalArgumentException.class, () -> solver().getPathTo(g, 99));
        assertThrows(
            IllegalArgumentException.class, () -> solver().getPathBetween(g, 0, 99));
        assertThrows(
            IllegalArgumentException.class, () -> solver().getPathBetween(g, 99, 0));
    }

    @Test
    public void nullEndpointThrows()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(3);
        assertThrows(NullPointerException.class, () -> solver().getPathFrom(g, null));
        assertThrows(NullPointerException.class, () -> solver().getPathTo(g, null));
        assertThrows(NullPointerException.class, () -> solver().getPathBetween(g, null, 0));
        assertThrows(NullPointerException.class, () -> solver().getPathBetween(g, 0, null));
    }
}
