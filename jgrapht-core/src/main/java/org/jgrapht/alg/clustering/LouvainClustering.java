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
package org.jgrapht.alg.clustering;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;

import java.util.*;

/**
 * The Louvain method for community detection.
 *
 * <p>
 * Greedily optimizes the
 * <a href="https://en.wikipedia.org/wiki/Modularity_(networks)">modularity</a> of a vertex
 * partition. The algorithm is described in detail in the following
 * <a href="https://doi.org/10.1088/1742-5468/2008/10/P10008">paper</a>:
 * <ul>
 * <li>Blondel, V. D., Guillaume, J. L., Lambiotte, R., and Lefebvre, E. (2008). Fast unfolding of
 * communities in large networks. Journal of Statistical Mechanics: Theory and Experiment, 2008(10),
 * P10008.</li>
 * </ul>
 *
 * <p>
 * The method proceeds in alternating phases. In the <em>local-moving</em> phase every vertex starts
 * in its own community and is repeatedly moved to the neighbouring community that yields the
 * largest positive gain in modularity, until no move improves the objective. In the
 * <em>aggregation</em> phase the discovered communities are contracted into super-vertices (with
 * intra-community edges becoming self-loops) and the two phases repeat on the smaller graph. The
 * process stops once a local-moving phase merges no communities. The final partition is projected
 * back onto the original vertices.
 *
 * <p>
 * The algorithm runs on undirected graphs and supports edge weights; parallel edges are collapsed
 * by summing their weights and self-loops are honoured using the same conventions as
 * {@link UndirectedModularityMeasurer}. Edge weights must be non-negative (modularity is undefined
 * for negative weights); a negative weight triggers an {@link IllegalArgumentException} when the
 * clustering is computed. Its empirical running time is close to linear in the number of edges,
 * although no worst-case guarantee is provided.
 *
 * <p>
 * The local-moving phase visits vertices in a random order, so two runs on the same graph may
 * return different (but typically similar quality) partitions. Supply a seeded {@link Random} via
 * the constructor for deterministic behaviour.
 *
 * @author seilat
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 */
public class LouvainClustering<V, E> implements ClusteringAlgorithm<V>
{
    /**
     * Default minimum modularity gain that justifies moving a vertex to another community. Guards
     * against floating-point oscillation in the local-moving phase.
     */
    public static final double DEFAULT_TOLERANCE = 1e-7;

    private final Graph<V, E> graph;
    private final Random rng;
    private final double tolerance;

    private Clustering<V> result;
    private double modularity;

    /**
     * Create a new clustering algorithm with a fresh random number generator.
     *
     * @param graph the graph (needs to be undirected)
     */
    public LouvainClustering(Graph<V, E> graph)
    {
        this(graph, new Random(), DEFAULT_TOLERANCE);
    }

    /**
     * Create a new clustering algorithm with a user-supplied random number generator. Provide a
     * seeded generator for reproducible results.
     *
     * @param graph the graph (needs to be undirected)
     * @param rng random number generator
     */
    public LouvainClustering(Graph<V, E> graph, Random rng)
    {
        this(graph, rng, DEFAULT_TOLERANCE);
    }

    /**
     * Create a new clustering algorithm.
     *
     * @param graph the graph (needs to be undirected)
     * @param rng random number generator
     * @param tolerance minimum modularity gain that justifies moving a vertex; must be non-negative
     */
    public LouvainClustering(Graph<V, E> graph, Random rng, double tolerance)
    {
        this.graph = GraphTests.requireUndirected(graph);
        this.rng = Objects.requireNonNull(rng, "Random number generator cannot be null");
        if (tolerance < 0d) {
            throw new IllegalArgumentException("Tolerance cannot be negative");
        }
        this.tolerance = tolerance;
    }

    @Override
    public Clustering<V> getClustering()
    {
        if (result == null) {
            compute();
        }
        return result;
    }

    /**
     * Returns the modularity of the computed clustering. The clustering is computed on first
     * access. The modularity of a graph with no positive total edge weight (no edges, or all edge
     * weights {@code 0}) is defined here to be {@code 0}.
     *
     * @return the modularity of the clustering in the range $[-0.5, 1)$
     */
    public double getModularity()
    {
        getClustering();
        return modularity;
    }

    private void compute()
    {
        List<V> indexToVertex = new ArrayList<>(graph.vertexSet());
        final int n = indexToVertex.size();
        if (n == 0) {
            result = new ClusteringImpl<>(Collections.emptyList());
            modularity = 0d;
            return;
        }
        Map<V, Integer> vertexToIndex = new HashMap<>(n);
        for (int i = 0; i < n; i++) {
            vertexToIndex.put(indexToVertex.get(i), i);
        }

        // Build the level-0 weighted adjacency, collapsing parallel edges and tracking self-loops.
        List<Map<Integer, Double>> adjacency = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adjacency.add(new HashMap<>());
        }
        double[] selfLoop = new double[n];
        boolean weighted = graph.getType().isWeighted();
        for (E e : graph.edgeSet()) {
            int u = vertexToIndex.get(graph.getEdgeSource(e));
            int v = vertexToIndex.get(graph.getEdgeTarget(e));
            double w = weighted ? graph.getEdgeWeight(e) : 1d;
            if (w < 0d) {
                throw new IllegalArgumentException("Edge weights must be non-negative");
            }
            if (u == v) {
                selfLoop[u] += w;
            } else {
                adjacency.get(u).merge(v, w, Double::sum);
                adjacency.get(v).merge(u, w, Double::sum);
            }
        }

        // 2m: sum of all weighted degrees, invariant across aggregation levels.
        double totalWeight = 0d;
        for (int i = 0; i < n; i++) {
            totalWeight += 2d * selfLoop[i];
            for (double w : adjacency.get(i).values()) {
                totalWeight += w;
            }
        }

        // Map every original vertex to its node in the current (aggregated) level.
        int[] vertexToNode = new int[n];
        for (int i = 0; i < n; i++) {
            vertexToNode[i] = i;
        }

        if (totalWeight > 0d) {
            while (true) {
                int[] community = localMoving(adjacency, selfLoop, totalWeight);
                int communities = countCommunities(community);
                for (int v = 0; v < n; v++) {
                    vertexToNode[v] = community[vertexToNode[v]];
                }
                if (communities == adjacency.size()) {
                    // The local-moving phase merged nothing: converged.
                    break;
                }
                List<Map<Integer, Double>> aggregatedAdjacency = new ArrayList<>(communities);
                for (int c = 0; c < communities; c++) {
                    aggregatedAdjacency.add(new HashMap<>());
                }
                double[] aggregatedSelfLoop =
                    aggregate(adjacency, selfLoop, community, communities, aggregatedAdjacency);
                adjacency = aggregatedAdjacency;
                selfLoop = aggregatedSelfLoop;
            }
        }

        result = buildClustering(indexToVertex, vertexToNode);
        // Guard on totalWeight, not edgeSet emptiness: a graph whose edges all have weight 0 has
        // 2m == 0, which would make the measurer divide by zero and return NaN.
        modularity = totalWeight > 0d
            ? new UndirectedModularityMeasurer<>(graph).modularity(result.getClusters())
            : 0d;
    }

    /**
     * Local-moving phase. Each node starts in its own community and is repeatedly relocated to the
     * neighbouring community that maximises the modularity gain until no move improves the
     * objective. Returns a compacted community label (in {@code [0, k)}) for every node.
     */
    private int[] localMoving(
        List<Map<Integer, Double>> adjacency, double[] selfLoop, double totalWeight)
    {
        final int n = adjacency.size();
        double[] degree = new double[n];
        double[] sigmaTot = new double[n];
        int[] community = new int[n];
        for (int i = 0; i < n; i++) {
            double d = 2d * selfLoop[i];
            for (double w : adjacency.get(i).values()) {
                d += w;
            }
            degree[i] = d;
            sigmaTot[i] = d;
            community[i] = i;
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        boolean improvement = true;
        while (improvement) {
            improvement = false;
            Collections.shuffle(Arrays.asList(order), rng);
            for (int oi = 0; oi < n; oi++) {
                int i = order[oi];
                double ki = degree[i];

                // Weight from i to each neighbouring community.
                Map<Integer, Double> weightToCommunity = new HashMap<>();
                for (Map.Entry<Integer, Double> en : adjacency.get(i).entrySet()) {
                    weightToCommunity.merge(community[en.getKey()], en.getValue(), Double::sum);
                }

                int currentCommunity = community[i];
                // Tentatively remove i from its community.
                sigmaTot[currentCommunity] -= ki;

                int bestCommunity = currentCommunity;
                double bestGain = weightToCommunity.getOrDefault(currentCommunity, 0d)
                    - sigmaTot[currentCommunity] * ki / totalWeight;
                for (Map.Entry<Integer, Double> en : weightToCommunity.entrySet()) {
                    int c = en.getKey();
                    if (c == currentCommunity) {
                        continue;
                    }
                    double gain = en.getValue() - sigmaTot[c] * ki / totalWeight;
                    if (gain > bestGain + tolerance) {
                        bestGain = gain;
                        bestCommunity = c;
                    }
                }

                sigmaTot[bestCommunity] += ki;
                if (bestCommunity != currentCommunity) {
                    community[i] = bestCommunity;
                    improvement = true;
                }
            }
        }
        return compact(community);
    }

    /**
     * Contracts each community into a single node. Intra-community edges (and pre-existing
     * self-loops) become the new node's self-loop; inter-community edges are summed into the new
     * adjacency. Returns the self-loop weights of the aggregated nodes and fills
     * {@code aggregatedAdjacency}. Degrees are conserved: the aggregated node degree equals the
     * total degree of its members.
     */
    private double[] aggregate(
        List<Map<Integer, Double>> adjacency, double[] selfLoop, int[] community, int communities,
        List<Map<Integer, Double>> aggregatedAdjacency)
    {
        double[] aggregatedSelfLoop = new double[communities];
        double[] internalAccumulator = new double[communities];
        final int n = adjacency.size();
        for (int u = 0; u < n; u++) {
            int cu = community[u];
            aggregatedSelfLoop[cu] += selfLoop[u];
            for (Map.Entry<Integer, Double> en : adjacency.get(u).entrySet()) {
                int cv = community[en.getKey()];
                if (cu == cv) {
                    // Each intra-community edge is seen once per direction; halved below.
                    internalAccumulator[cu] += en.getValue();
                } else {
                    aggregatedAdjacency.get(cu).merge(cv, en.getValue(), Double::sum);
                }
            }
        }
        for (int c = 0; c < communities; c++) {
            aggregatedSelfLoop[c] += internalAccumulator[c] / 2d;
        }
        return aggregatedSelfLoop;
    }

    /**
     * Builds the list-of-sets clustering from the original-vertex-to-final-community map, ordered
     * by community id.
     */
    private Clustering<V> buildClustering(List<V> indexToVertex, int[] vertexToNode)
    {
        int[] compacted = compact(vertexToNode);
        int k = countCommunities(compacted);
        List<Set<V>> clusters = new ArrayList<>(k);
        for (int c = 0; c < k; c++) {
            clusters.add(new LinkedHashSet<>());
        }
        for (int v = 0; v < compacted.length; v++) {
            clusters.get(compacted[v]).add(indexToVertex.get(v));
        }
        return new ClusteringImpl<>(clusters);
    }

    /**
     * Relabels arbitrary community ids to a dense range {@code [0, k)} preserving first-seen order.
     */
    private static int[] compact(int[] labels)
    {
        Map<Integer, Integer> remap = new HashMap<>();
        int[] out = new int[labels.length];
        int next = 0;
        for (int i = 0; i < labels.length; i++) {
            Integer mapped = remap.get(labels[i]);
            if (mapped == null) {
                mapped = next++;
                remap.put(labels[i], mapped);
            }
            out[i] = mapped;
        }
        return out;
    }

    /**
     * Number of distinct labels in a compacted label array (its maximum plus one, or zero if
     * empty).
     */
    private static int countCommunities(int[] compactedLabels)
    {
        int max = -1;
        for (int label : compactedLabels) {
            if (label > max) {
                max = label;
            }
        }
        return max + 1;
    }
}
