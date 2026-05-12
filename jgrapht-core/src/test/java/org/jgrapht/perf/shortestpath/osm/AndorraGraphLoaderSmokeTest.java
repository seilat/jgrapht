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
package org.jgrapht.perf.shortestpath.osm;

import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for {@link AndorraGraphLoader}. Verifies the committed test
 * resource decodes to the expected vertex/edge counts and that Dijkstra can
 * route through the resulting graph.
 *
 * @author Shai Eilat
 */
class AndorraGraphLoaderSmokeTest
{
    @Test
    void loadsAndorraRoadGraphAndRoutesThroughIt()
    {
        AndorraGraphLoader.AndorraData data = AndorraGraphLoader.load();

        assertEquals(36618, data.graph.vertexSet().size(), "andorra largest SCC vertex count");
        assertEquals(67354, data.graph.edgeSet().size(), "andorra largest SCC edge count");
        assertEquals(36618, data.nodeLatLon.length, "node coordinate table size");

        // pick the two vertices furthest apart by ID — works because the SCC is strongly
        // connected, so a shortest path must exist.
        int source = 0;
        int sink = data.graph.vertexSet().size() - 1;
        DijkstraShortestPath<Integer, DefaultWeightedEdge> dijkstra =
            new DijkstraShortestPath<>(data.graph);
        var path = dijkstra.getPath(source, sink);
        assertNotNull(path, "expected a route from 0 to last node in the largest SCC");
        assertTrue(path.getWeight() > 0, "non-trivial route weight");

        AndorraGraphLoader.GreatCircleHeuristic heuristic =
            new AndorraGraphLoader.GreatCircleHeuristic(data.nodeLatLon);
        double estimate = heuristic.getCostEstimate(source, sink);
        assertTrue(
            estimate <= path.getWeight() + 1e-6,
            "great-circle heuristic must be admissible (h <= true distance)");
    }
}
