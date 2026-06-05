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
package org.jgrapht.demo;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.alg.tour.*;
import org.jgrapht.graph.*;

import java.util.*;
import java.util.function.*;

/**
 * Demonstrates the weighted and endpoint-aware Hamiltonian path solvers:
 * <ul>
 * <li>endpoint-constrained existence search ({@link BacktrackingHamiltonianPath});</li>
 * <li>minimum-weight Hamiltonian path / path-TSP and its navigation variant
 * ({@link HeldKarpShortestHamiltonianPath});</li>
 * <li>maximum-weight Hamiltonian path ({@link HeldKarpLongestHamiltonianPath});</li>
 * <li>longest (non-spanning) simple path ({@link HeldKarpLongestPath});</li>
 * <li>enumeration of every Hamiltonian path ({@link HamiltonianPathEnumerator}).</li>
 * </ul>
 *
 * <p>
 * The running example is a small weighted road network of six towns; each demo prints the relevant
 * path and its weight. All of these problems are NP-hard in general, so the solvers are exact
 * exponential-time algorithms intended for small graphs.
 *
 * @author seilat
 */
public final class WeightedHamiltonianPathDemo
{
    private WeightedHamiltonianPathDemo()
    {
    }

    public static void main(String[] args)
    {
        Graph<String, DefaultWeightedEdge> roads = roadNetwork();
        System.out.println("Road network (undirected, edge weights = travel minutes):");
        for (DefaultWeightedEdge e : roads.edgeSet()) {
            System.out.printf(
                "  %s -- %s : %.0f%n", roads.getEdgeSource(e), roads.getEdgeTarget(e),
                roads.getEdgeWeight(e));
        }

        endpointConstrainedDemo(roads);
        minimumWeightDemo(roads);
        navigationDemo(roads);
        maximumWeightDemo(roads);
        longestSimplePathDemo();
        enumerationDemo();
    }

    private static void endpointConstrainedDemo(Graph<String, DefaultWeightedEdge> roads)
    {
        System.out.println("\n== Endpoint-constrained search (backtracking) ==");
        BacktrackingHamiltonianPath<String, DefaultWeightedEdge> solver =
            new BacktrackingHamiltonianPath<>();
        System.out.print("A tour visiting every town that starts at 'Avon' and ends at 'Foxton': ");
        printResult(solver.getPathBetween(roads, "Avon", "Foxton"));
        System.out.print("A tour that simply starts at 'Cole': ");
        printResult(solver.getPathFrom(roads, "Cole"));
    }

    private static void minimumWeightDemo(Graph<String, DefaultWeightedEdge> roads)
    {
        System.out.println("\n== Minimum-weight Hamiltonian path (path-TSP, Held-Karp) ==");
        HeldKarpShortestHamiltonianPath<String, DefaultWeightedEdge> solver =
            new HeldKarpShortestHamiltonianPath<>();
        System.out.print("Cheapest route visiting every town (endpoints free): ");
        printResult(solver.getPath(roads));
        System.out.print("Cheapest route from 'Avon' to 'Foxton': ");
        printResult(solver.getPathBetween(roads, "Avon", "Foxton"));
    }

    private static void navigationDemo(Graph<String, DefaultWeightedEdge> roads)
    {
        System.out.println("\n== Navigation: enter near a start, leave near a target ==");
        // You are off-network. The approach leg is the time to drive onto each town, the departure
        // leg the time to leave it towards your destination. The solver picks the entry/exit towns
        // that minimise approach + tour + departure jointly.
        Map<String, Double> approach = Map.of(
            "Avon", 2.0, "Bly", 5.0, "Cole", 8.0, "Deeping", 9.0, "Esk", 11.0, "Foxton", 13.0);
        Map<String, Double> departure = Map.of(
            "Avon", 12.0, "Bly", 9.0, "Cole", 7.0, "Deeping", 4.0, "Esk", 2.0, "Foxton", 1.0);
        ToDoubleFunction<String> approachCost = approach::get;
        ToDoubleFunction<String> departureCost = departure::get;

        HamiltonianPathSearchResult<String, DefaultWeightedEdge> r =
            new HeldKarpShortestHamiltonianPath<String, DefaultWeightedEdge>()
                .getShortestPathNearEndpoints(roads, approachCost, departureCost);
        GraphPath<String, DefaultWeightedEdge> tour = r.getPath().orElseThrow();
        double total = tour.getWeight() + approachCost.applyAsDouble(tour.getStartVertex())
            + departureCost.applyAsDouble(tour.getEndVertex());
        System.out.println("Optimal entry/exit tour: " + tour.getVertexList());
        System.out.printf(
            "  tour weight %.0f + approach %.0f + departure %.0f = total journey %.0f%n",
            tour.getWeight(), approachCost.applyAsDouble(tour.getStartVertex()),
            departureCost.applyAsDouble(tour.getEndVertex()), total);
    }

    private static void maximumWeightDemo(Graph<String, DefaultWeightedEdge> roads)
    {
        System.out.println("\n== Maximum-weight (longest) Hamiltonian path ==");
        System.out.print("Most scenic route visiting every town (maximises total minutes): ");
        printResult(
            new HeldKarpLongestHamiltonianPath<String, DefaultWeightedEdge>().getPath(roads));
    }

    private static void longestSimplePathDemo()
    {
        System.out.println("\n== Longest simple path (need not visit every vertex) ==");
        // A high-value triangle plus an outlier reachable only by a costly edge: the longest simple
        // path stays in the triangle rather than paying to include the outlier.
        Graph<String, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (String v : List.of("p", "q", "r", "outlier")) {
            g.addVertex(v);
        }
        g.setEdgeWeight(g.addEdge("p", "q"), 10);
        g.setEdgeWeight(g.addEdge("q", "r"), 10);
        g.setEdgeWeight(g.addEdge("p", "r"), 10);
        g.setEdgeWeight(g.addEdge("outlier", "p"), -100);
        GraphPath<String, DefaultWeightedEdge> path =
            new HeldKarpLongestPath<String, DefaultWeightedEdge>().getPath(g);
        System.out.println("Longest simple path: " + path.getVertexList());
        System.out.println("  weight: " + path.getWeight() + " (the outlier is skipped)");
    }

    private static void enumerationDemo()
    {
        System.out.println("\n== Enumerating all Hamiltonian paths ==");
        // 4-cycle 0-1-2-3-0: undirected Hamiltonian paths are exactly the cycle minus one edge.
        Graph<Integer, DefaultEdge> cycle = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < 4; i++) {
            cycle.addVertex(i);
        }
        for (int i = 0; i < 4; i++) {
            cycle.addEdge(i, (i + 1) % 4);
        }
        HamiltonianPathEnumerator<Integer, DefaultEdge> enumerator =
            new HamiltonianPathEnumerator<>(cycle);
        System.out.println("4-cycle has " + enumerator.count() + " Hamiltonian paths:");
        for (GraphPath<Integer, DefaultEdge> path : enumerator) {
            System.out.println("  " + path.getVertexList());
        }
    }

    private static Graph<String, DefaultWeightedEdge> roadNetwork()
    {
        Graph<String, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (String town : List.of("Avon", "Bly", "Cole", "Deeping", "Esk", "Foxton")) {
            g.addVertex(town);
        }
        addRoad(g, "Avon", "Bly", 4);
        addRoad(g, "Avon", "Cole", 6);
        addRoad(g, "Bly", "Cole", 3);
        addRoad(g, "Bly", "Deeping", 7);
        addRoad(g, "Cole", "Deeping", 5);
        addRoad(g, "Cole", "Esk", 4);
        addRoad(g, "Deeping", "Esk", 2);
        addRoad(g, "Deeping", "Foxton", 6);
        addRoad(g, "Esk", "Foxton", 3);
        return g;
    }

    private static void addRoad(
        Graph<String, DefaultWeightedEdge> g, String a, String b, double minutes)
    {
        g.setEdgeWeight(g.addEdge(a, b), minutes);
    }

    private static <V, E> void printResult(HamiltonianPathSearchResult<V, E> result)
    {
        if (result.getPath().isEmpty()) {
            System.out.println("none (status=" + result.getStatus() + ").");
            return;
        }
        GraphPath<V, E> path = result.getPath().orElseThrow();
        System.out.println(path.getVertexList() + "  (weight " + path.getWeight() + ")");
    }
}
