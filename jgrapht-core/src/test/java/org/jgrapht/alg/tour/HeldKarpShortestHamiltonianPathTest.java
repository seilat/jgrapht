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

import java.util.function.*;

import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertHamiltonianPath;
import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertProvenAbsent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deterministic tests for {@link HeldKarpShortestHamiltonianPath}: the minimum-weight (path-TSP)
 * objective, its endpoint-constrained variants, and the navigation-oriented
 * {@code getShortestPathWithBest*}. Expected optima are computed by hand for small weighted
 * graphs.
 */
public class HeldKarpShortestHamiltonianPathTest
{

    private static final double EPS = 1e-9;

    private HeldKarpShortestHamiltonianPath<Integer, DefaultWeightedEdge> solver()
    {
        return new HeldKarpShortestHamiltonianPath<>();
    }

    private double weightOf(HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> result)
    {
        return result.getPath().orElseThrow().getWeight();
    }

    /** Weighted undirected triangle: w(0,1)=1, w(1,2)=2, w(0,2)=5. */
    private Graph<Integer, DefaultWeightedEdge> weightedTriangle()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), 1);
        g.setEdgeWeight(g.addEdge(1, 2), 2);
        g.setEdgeWeight(g.addEdge(0, 2), 5);
        return g;
    }

    @Test
    public void freeEndpointsReturnsGlobalMinimum()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(3.0, weightOf(r), EPS); // 0-1-2
    }

    @Test
    public void betweenEndpointsMinimisesWeightForThosePins()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        assertEquals(3.0, weightOf(solver().getPathBetween(g, 0, 2)), EPS); // 0-1-2
        assertEquals(6.0, weightOf(solver().getPathBetween(g, 1, 2)), EPS); // 1-0-2
        assertEquals(7.0, weightOf(solver().getPathBetween(g, 0, 1)), EPS); // 0-2-1
    }

    @Test
    public void fromAndToConstrainTheCorrectEndpoint()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> from = solver().getPathFrom(g, 2);
        assertHamiltonianPath(g, from);
        assertEquals(2, from.getPath().orElseThrow().getStartVertex());
        assertEquals(3.0, weightOf(from), EPS); // 2-1-0

        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> to = solver().getPathTo(g, 0);
        assertHamiltonianPath(g, to);
        assertEquals(0, to.getPath().orElseThrow().getEndVertex());
        assertEquals(3.0, weightOf(to), EPS); // 2-1-0
    }

    @Test
    public void directedMinimumWeight()
    {
        Graph<Integer, DefaultWeightedEdge> g =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        // two directed Hamiltonian paths: 0->1->2 (w 1+1=2) and 2->1->0 (w 4+4=8)
        g.setEdgeWeight(g.addEdge(0, 1), 1);
        g.setEdgeWeight(g.addEdge(1, 2), 1);
        g.setEdgeWeight(g.addEdge(2, 1), 4);
        g.setEdgeWeight(g.addEdge(1, 0), 4);
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(2.0, weightOf(r), EPS);
        assertEquals(0, r.getPath().orElseThrow().getStartVertex());
        assertEquals(2, r.getPath().orElseThrow().getEndVertex());
    }

    @Test
    public void negativeWeightsHandled()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), -5);
        g.setEdgeWeight(g.addEdge(1, 2), 2);
        g.setEdgeWeight(g.addEdge(0, 2), 1);
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(-4.0, weightOf(r), EPS); // 1-0-2: -5 + 1
    }

    @Test
    public void multigraphUsesMinimumWeightParallelEdge()
    {
        Graph<Integer, DefaultWeightedEdge> g = new WeightedMultigraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 3; i++) {
            g.addVertex(i);
        }
        g.setEdgeWeight(g.addEdge(0, 1), 5);
        DefaultWeightedEdge cheap = g.addEdge(0, 1); // parallel edge
        g.setEdgeWeight(cheap, 1);
        g.setEdgeWeight(g.addEdge(1, 2), 3);
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(4.0, weightOf(r), EPS); // uses the weight-1 parallel edge: 1 + 3
    }

    @Test
    public void navigationMinimisesTotalJourneyNotTourWeight()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        // approach favours starting at 1; departure favours ending at 2
        ToDoubleFunction<Integer> approach = v -> v == 0 ? 10d : 0d;
        ToDoubleFunction<Integer> departure = v -> v == 2 ? 0d : 10d;
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r =
            solver().getShortestPathWithBestEndpoints(g, approach, departure);
        assertHamiltonianPath(g, r);
        GraphPath<Integer, DefaultWeightedEdge> path = r.getPath().orElseThrow();
        // optimum is the heavier tour 1-0-2 (tour weight 6) because it minimises the TOTAL
        // 0 (approach@1) + 6 (tour) + 0 (departure@2) = 6, beating 0-1-2's 10 + 3 + 0 = 13
        assertEquals(1, path.getStartVertex());
        assertEquals(2, path.getEndVertex());
        assertEquals(6.0, path.getWeight(), EPS);
    }

    @Test
    public void navigationSingleEndpointFamily()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        // free end, approach favours starting at 1: cheapest total is the path starting at 1
        ToDoubleFunction<Integer> approach = v -> v == 1 ? 0d : 10d;
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> fromStart =
            solver().getShortestPathWithBestStart(g, approach);
        assertHamiltonianPath(g, fromStart);
        assertEquals(1, fromStart.getPath().orElseThrow().getStartVertex());

        // free start, departure favours ending at 2
        ToDoubleFunction<Integer> departure = v -> v == 2 ? 0d : 10d;
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> toEnd =
            solver().getShortestPathWithBestEnd(g, departure);
        assertHamiltonianPath(g, toEnd);
        assertEquals(2, toEnd.getPath().orElseThrow().getEndVertex());
    }

    @Test
    public void navigationNullCostRejected()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        assertThrows(
            NullPointerException.class, () -> solver().getShortestPathWithBestStart(g, null));
        assertThrows(
            NullPointerException.class, () -> solver().getShortestPathWithBestEnd(g, null));
        assertThrows(
            IllegalArgumentException.class,
            () -> solver().getShortestPathWithBestEndpoints(g, v -> Double.NaN, v -> 0d));
    }

    @Test
    public void singleVertex()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        g.addVertex(42);
        HamiltonianPathSearchResult<Integer, DefaultWeightedEdge> r = solver().getPath(g);
        assertHamiltonianPath(g, r);
        assertEquals(0.0, weightOf(r), EPS);
    }

    @Test
    public void sameEndpointsOnMultiVertexGraphAbsent()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        assertProvenAbsent(solver().getPathBetween(g, 1, 1));
    }

    @Test
    public void noHamiltonianPathProvenAbsent()
    {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        g.addEdge(0, 1);
        g.addEdge(2, 3); // two disjoint edges
        assertProvenAbsent(solver().getPath(g));
    }

    @Test
    public void nonFiniteEdgeWeightsRejected()
    {
        for (double bad : new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.NaN })
        {
            Graph<Integer, DefaultWeightedEdge> g =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
            for (int i = 0; i < 3; i++) {
                g.addVertex(i);
            }
            g.setEdgeWeight(g.addEdge(0, 1), bad);
            g.setEdgeWeight(g.addEdge(1, 2), 1);
            assertThrows(
                IllegalArgumentException.class, () -> solver().getPath(g),
                () -> "weight " + bad + " must be rejected");
        }
    }

    @Test
    public void exceedingVertexCeilingThrows()
    {
        HeldKarpShortestHamiltonianPath<Integer, DefaultWeightedEdge> small =
            new HeldKarpShortestHamiltonianPath<>(3);
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        assertThrows(IllegalArgumentException.class, () -> small.getPath(g));
    }

    @Test
    public void invalidArgumentsThrow()
    {
        Graph<Integer, DefaultWeightedEdge> g = weightedTriangle();
        assertThrows(NullPointerException.class, () -> solver().getPath(null));
        assertThrows(NullPointerException.class, () -> solver().getPathFrom(g, null));
        assertThrows(IllegalArgumentException.class, () -> solver().getPathFrom(g, 99));
        assertThrows(IllegalArgumentException.class, () -> new HeldKarpShortestHamiltonianPath<>(0));
        assertThrows(
            IllegalArgumentException.class,
            () -> new HeldKarpShortestHamiltonianPath<>(
                HeldKarpShortestHamiltonianPath.HARD_MAX_VERTICES + 1));
        // empty graph
        Graph<Integer, DefaultWeightedEdge> empty =
            new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        assertThrows(IllegalArgumentException.class, () -> solver().getPath(empty));
    }
}
