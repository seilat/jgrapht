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

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.util.SupplierUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Builds the Andorra OSM road graph used by the {@code osm/Andorra*Bench} JMH harnesses.
 *
 * <p>
 * The source dataset is a Geofabrik "free" GPKG snapshot of OpenStreetMap (Andorra,
 * 2026-05-10) preprocessed into two gzipped CSV resources committed under
 * {@code src/test/resources/perf/osm/}:
 *
 * <ul>
 * <li>{@code andorra-edges.csv.gz} &mdash; {@code src,dst,weight_m} per directed edge
 * inside the largest strongly-connected component.</li>
 * <li>{@code andorra-edges.nodes.csv.gz} &mdash; {@code node_id,lat,lon} per vertex.</li>
 * </ul>
 *
 * <p>
 * Both files are written by {@code scripts/andorra_to_csv.py}; refer to that script for
 * the GPKG schema and the preprocessing rules (routable {@code fclass} filter,
 * coordinate snapping, oneway handling, parallel-edge dedupe).
 *
 * <p>
 * Edge weights are great-circle distances in metres (Haversine, R = 6_371_008.8 m).
 *
 * @author Shai Eilat
 */
public final class AndorraGraphLoader
{
    /** Edges CSV resource path (relative to test resources). */
    public static final String EDGES_RESOURCE = "/perf/osm/andorra-edges.csv.gz";
    /** Node coordinates CSV resource path (relative to test resources). */
    public static final String NODES_RESOURCE = "/perf/osm/andorra-edges.nodes.csv.gz";

    /** Earth radius used by both the preprocessor and the heuristic. */
    public static final double EARTH_RADIUS_M = 6_371_008.8;

    private AndorraGraphLoader()
    {
    }

    /**
     * Load the Andorra road graph plus the per-node coordinate table.
     *
     * @return loaded data bundle
     */
    public static AndorraData load()
    {
        double[][] coords = readNodes();
        // SimpleDirectedWeightedGraph so EppsteinKShortestPath can consume the result;
        // the preprocessor in scripts/andorra_to_csv.py already deduplicates parallel
        // edges so loading does not violate the simple-graph contract.
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph.setVertexSupplier(SupplierUtil.createIntegerSupplier());
        for (int i = 0; i < coords.length; i++) {
            graph.addVertex(i);
        }
        readEdges(graph);
        return new AndorraData(graph, coords);
    }

    private static double[][] readNodes()
    {
        try (BufferedReader r = openResource(NODES_RESOURCE)) {
            String header = r.readLine();
            Objects.requireNonNull(header, "nodes csv: empty");
            // Pass 1: count rows
            int count = 0;
            // We can size on the fly: read into an arraylist first, then copy.
            double[][] tmp = new double[1024][];
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                int firstComma = line.indexOf(',');
                int secondComma = line.indexOf(',', firstComma + 1);
                int nodeId = Integer.parseInt(line, 0, firstComma, 10);
                double lat = Double.parseDouble(line.substring(firstComma + 1, secondComma));
                double lon = Double.parseDouble(line.substring(secondComma + 1));
                if (nodeId >= tmp.length) {
                    int newLen = Math.max(tmp.length * 2, nodeId + 1);
                    double[][] grown = new double[newLen][];
                    System.arraycopy(tmp, 0, grown, 0, tmp.length);
                    tmp = grown;
                }
                tmp[nodeId] = new double[] { lat, lon };
                count = Math.max(count, nodeId + 1);
            }
            double[][] out = new double[count][];
            System.arraycopy(tmp, 0, out, 0, count);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void readEdges(Graph<Integer, DefaultWeightedEdge> graph)
    {
        try (BufferedReader r = openResource(EDGES_RESOURCE)) {
            String header = r.readLine();
            Objects.requireNonNull(header, "edges csv: empty");
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                int firstComma = line.indexOf(',');
                int secondComma = line.indexOf(',', firstComma + 1);
                int src = Integer.parseInt(line, 0, firstComma, 10);
                int dst = Integer.parseInt(line, firstComma + 1, secondComma, 10);
                double weight = Double.parseDouble(line.substring(secondComma + 1));
                DefaultWeightedEdge e = graph.addEdge(src, dst);
                if (e != null) {
                    graph.setEdgeWeight(e, weight);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedReader openResource(String resource) throws IOException
    {
        InputStream in = AndorraGraphLoader.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("missing test resource: " + resource);
        }
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
    }

    /**
     * Great-circle (Haversine) admissible heuristic over node ids whose coordinates are
     * recorded in {@link AndorraData#nodeLatLon}.
     */
    public static final class GreatCircleHeuristic
        implements AStarAdmissibleHeuristic<Integer>
    {
        private final double[][] coords;

        public GreatCircleHeuristic(double[][] coords)
        {
            this.coords = coords;
        }

        @Override
        public double getCostEstimate(Integer sourceVertex, Integer targetVertex)
        {
            double[] s = coords[sourceVertex];
            double[] t = coords[targetVertex];
            double p1 = Math.toRadians(s[0]);
            double p2 = Math.toRadians(t[0]);
            double dp = Math.toRadians(t[0] - s[0]);
            double dl = Math.toRadians(t[1] - s[1]);
            double sdp = Math.sin(dp / 2);
            double sdl = Math.sin(dl / 2);
            double a = sdp * sdp + Math.cos(p1) * Math.cos(p2) * sdl * sdl;
            return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
        }
    }

    /** Loaded graph + node coordinates. */
    public static final class AndorraData
    {
        public final Graph<Integer, DefaultWeightedEdge> graph;
        public final double[][] nodeLatLon;

        AndorraData(Graph<Integer, DefaultWeightedEdge> graph, double[][] nodeLatLon)
        {
            this.graph = graph;
            this.nodeLatLon = nodeLatLon;
        }
    }
}
