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
import org.jgrapht.alg.interfaces.HamiltonianPathSearchResult.Status;
import org.jgrapht.graph.*;
import org.junit.jupiter.api.*;

import java.util.function.*;

import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertHamiltonianPath;
import static org.jgrapht.alg.tour.HamiltonianPathValidator.assertProvenAbsent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic tests for the {@code BacktrackingHamiltonianPath.getPathWithBest*} family: endpoint
 * selection that minimises caller-supplied approach/departure costs among feasible endpoint pairs,
 * with ranked fallback past infeasible cheaper pairs.
 */
public class BacktrackingHamiltonianPathNearEndpointTest
{

    private BacktrackingHamiltonianPath<Integer, DefaultEdge> solver()
    {
        return new BacktrackingHamiltonianPath<>();
    }

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

    /**
     * In a path graph {@code 0-1-2-3-4} the only feasible Hamiltonian-path endpoints are
     * {@code {0,4}}. With the source nearest vertex 2 and the target nearest vertex 3, the cheapest
     * pair {@code (2,3)} is infeasible; the ranked fallback must skip it and return the cheapest
     * feasible ordered pair, {@code (0,4)} (cost 3) rather than {@code (4,0)} (cost 5).
     */
    @Test
    public void rankedFallbackSkipsInfeasibleCheaperPairs()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        ToDoubleFunction<Integer> approach = v -> Math.abs(v - 2); // source near vertex 2
        ToDoubleFunction<Integer> departure = v -> Math.abs(v - 3); // target near vertex 3
        assertEndpoints(g, solver().getPathWithBestEndpoints(g, approach, departure), 0, 4);
    }

    @Test
    public void freeStartChoosesCheapestFeasibleEnd()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        // start free, target nearest vertex 3 -> feasible ends are {0,4}; 4 is closer to 3
        ToDoubleFunction<Integer> departure = v -> Math.abs(v - 3);
        assertEndpoints(g, solver().getPathWithBestEnd(g, departure), null, 4);
    }

    @Test
    public void freeEndChoosesCheapestFeasibleStart()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        // end free, source nearest vertex 1 -> feasible starts are {0,4}; 0 is closer to 1
        ToDoubleFunction<Integer> approach = v -> Math.abs(v - 1);
        assertEndpoints(g, solver().getPathWithBestStart(g, approach), 0, null);
    }

    @Test
    public void directedRespectsDirection()
    {
        Graph<Integer, DefaultEdge> g = directedChain(4);
        // only feasible directed endpoints are start 0, end 3
        ToDoubleFunction<Integer> approach = v -> Math.abs(v - 1);
        ToDoubleFunction<Integer> departure = v -> Math.abs(v - 2);
        assertEndpoints(g, solver().getPathWithBestEndpoints(g, approach, departure), 0, 3);
    }

    @Test
    public void noHamiltonianPathIsProvenAbsentRegardlessOfCosts()
    {
        // two disjoint edges: no Hamiltonian path exists at all
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < 4; i++) {
            g.addVertex(i);
        }
        g.addEdge(0, 1);
        g.addEdge(2, 3);
        ToDoubleFunction<Integer> cost = v -> v;
        assertProvenAbsent(solver().getPathWithBestEndpoints(g, cost, cost));
    }

    @Test
    public void singleVertexReturnsSingleton()
    {
        Graph<Integer, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
        g.addVertex(7);
        assertEndpoints(g, solver().getPathWithBestEndpoints(g, v -> 0d, v -> 0d), 7, 7);
    }

    /**
     * With the attempt budget exhausted before a feasible pair is reached, the result must be
     * {@code ABORTED} rather than a false {@code PROVEN_ABSENT}. In {@code 0-1-2-3-4} the single
     * cheapest pair {@code (2,3)} is infeasible, so a budget of one attempt aborts.
     */
    @Test
    public void budgetExhaustedBeforeFeasiblePairAborts()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        ToDoubleFunction<Integer> approach = v -> Math.abs(v - 2);
        ToDoubleFunction<Integer> departure = v -> Math.abs(v - 3);
        HamiltonianPathSearchResult<Integer, DefaultEdge> result =
            solver().getPathWithBestEndpoints(g, approach, departure, 1);
        assertEquals(Status.ABORTED, result.getStatus());
    }

    @Test
    public void generousBudgetStillFindsFeasiblePair()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        ToDoubleFunction<Integer> approach = v -> Math.abs(v - 2);
        ToDoubleFunction<Integer> departure = v -> Math.abs(v - 3);
        assertEndpoints(g, solver().getPathWithBestEndpoints(g, approach, departure, 100), 0, 4);
    }

    @Test
    public void statesExpandedAggregatesAcrossInternalSearches()
    {
        // 0-1-2-3-4: the nearest pair (2,3) is infeasible, so getPathWithBestEndpoints runs the
        // existence guard plus several ranked sub-searches; getStatesExpanded must reflect the
        // total, hence be at least as large as a single unconstrained search.
        Graph<Integer, DefaultEdge> g = undirectedPath(5);
        ToDoubleFunction<Integer> approach = v -> Math.abs(v - 2);
        ToDoubleFunction<Integer> departure = v -> Math.abs(v - 3);

        BacktrackingHamiltonianPath<Integer, DefaultEdge> near = solver();
        near.getPathWithBestEndpoints(g, approach, departure);
        long aggregate = near.getStatesExpanded();

        BacktrackingHamiltonianPath<Integer, DefaultEdge> single = solver();
        single.getPath(g);
        long oneSearch = single.getStatesExpanded();

        assertTrue(aggregate >= oneSearch && aggregate > 0,
            () -> "aggregate=" + aggregate + " should be >= single search=" + oneSearch);
    }

    @Test
    public void nonFiniteCostFunctionRejected()
    {
        // a path graph has a Hamiltonian path, so the existence guard passes and candidate ranking
        // (which consumes the cost functions) runs and must reject the non-finite cost
        Graph<Integer, DefaultEdge> g = undirectedPath(4);
        for (double bad : new double[] { Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY })
        {
            ToDoubleFunction<Integer> badCost = v -> v == 0 ? bad : 1.0;
            assertThrows(
                IllegalArgumentException.class,
                () -> solver().getPathWithBestStart(g, badCost));
            assertThrows(
                IllegalArgumentException.class,
                () -> solver().getPathWithBestEnd(g, badCost));
            assertThrows(
                IllegalArgumentException.class,
                () -> solver().getPathWithBestEndpoints(g, badCost, v -> 1.0));
        }
    }

    @Test
    public void invalidArgumentsThrow()
    {
        Graph<Integer, DefaultEdge> g = undirectedPath(3);
        assertThrows(
            NullPointerException.class,
            () -> solver().getPathWithBestEndpoints(null, v -> 0d, v -> 0d));
        assertThrows(
            IllegalArgumentException.class,
            () -> solver().getPathWithBestEndpoints(g, v -> 0d, v -> 0d, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> solver().getPathWithBestEndpoints(g, v -> 0d, v -> 0d, -3));
        // explicit overloads reject a null cost function instead of treating it as a free endpoint
        assertThrows(
            NullPointerException.class, () -> solver().getPathWithBestStart(g, null));
        assertThrows(
            NullPointerException.class, () -> solver().getPathWithBestEnd(g, null));
        assertThrows(
            NullPointerException.class, () -> solver().getPathWithBestEndpoints(g, null, v -> 0d));
        assertThrows(
            NullPointerException.class, () -> solver().getPathWithBestEndpoints(g, v -> 0d, null));
    }
}
