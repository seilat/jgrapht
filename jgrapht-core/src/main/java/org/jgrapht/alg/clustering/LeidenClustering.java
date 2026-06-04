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
 * The Leiden method for community detection.
 *
 * <p>
 * An improvement of the {@link LouvainClustering Louvain method} that guarantees
 * <em>well-connected communities</em>. The algorithm is described in:
 * <ul>
 * <li>Traag, V. A., Waltman, L., and van Eck, N. J. (2019). From Louvain to Leiden: guaranteeing
 * well-connected communities. Scientific Reports, 9, 5233.
 * <a href="https://doi.org/10.1038/s41598-019-41695-z">doi:10.1038/s41598-019-41695-z</a>.</li>
 * </ul>
 *
 * <p>
 * Louvain can return communities that are internally disconnected: a vertex that once linked two
 * halves of a community can later move away, leaving the remainder split, and Louvain never
 * revisits it. Leiden inserts a <em>refinement</em> phase between local-moving and aggregation. In
 * the local-moving phase vertices are greedily relocated to maximise modularity (as in Louvain),
 * producing a partition $P$. In the refinement phase each community of $P$ is independently
 * re-partitioned into sub-communities that grow only along edges, so every sub-community is
 * connected; aggregation then contracts the <em>refined</em> sub-communities. Because each
 * aggregated super-vertex is connected, the communities reported at the top level are connected
 * too. As a final guarantee the reported partition is split into connected components, which can
 * only increase modularity (splitting a disconnected community reduces the null-model penalty
 * without losing any internal edge).
 *
 * <p>
 * Like {@link LouvainClustering} this runs on undirected graphs, supports non-negative edge
 * weights (a negative weight triggers an {@link IllegalArgumentException} when the clustering is
 * computed), collapses parallel edges and honours self-loops using the conventions of
 * {@link UndirectedModularityMeasurer} (reused for {@link #getModularity()}). The local-moving and
 * refinement phases visit vertices in a random order; supply a seeded {@link Random} for
 * deterministic output.
 *
 * <p>
 * <b>Implementation note:</b> this is the refinement-phase variant. For simplicity each
 * aggregation level restarts local-moving from singletons rather than seeding from the previous
 * partition, and the refinement gate is connectivity (merge only along edges with positive
 * modularity gain) rather than the stronger resolution-parametrised "well-connected" test of the
 * original paper. The well-connected-community guarantee on the reported partition is enforced
 * unconditionally by the final connected-components split.
 *
 * @author seilat
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 */
public class LeidenClustering<V, E>
    implements ClusteringAlgorithm<V>
{
    /**
     * Default minimum modularity gain that justifies moving a vertex. Guards against
     * floating-point oscillation.
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
    public LeidenClustering(Graph<V, E> graph)
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
    public LeidenClustering(Graph<V, E> graph, Random rng)
    {
        this(graph, rng, DEFAULT_TOLERANCE);
    }

    /**
     * Create a new clustering algorithm.
     *
     * @param graph the graph (needs to be undirected)
     * @param rng random number generator
     * @param tolerance minimum modularity gain that justifies moving a vertex; must be
     *        non-negative
     */
    public LeidenClustering(Graph<V, E> graph, Random rng, double tolerance)
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
     * access. The modularity of a graph with no positive total edge weight is defined to be
     * {@code 0}.
     *
     * @return the modularity of the clustering
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

        List<Map<Integer, Double>> baseAdjacency = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            baseAdjacency.add(new HashMap<>());
        }
        double[] baseSelfLoop = new double[n];
        boolean weighted = graph.getType().isWeighted();
        for (E e : graph.edgeSet()) {
            int u = vertexToIndex.get(graph.getEdgeSource(e));
            int v = vertexToIndex.get(graph.getEdgeTarget(e));
            double w = weighted ? graph.getEdgeWeight(e) : 1d;
            if (w < 0d) {
                throw new IllegalArgumentException("Edge weights must be non-negative");
            }
            if (u == v) {
                baseSelfLoop[u] += w;
            } else {
                baseAdjacency.get(u).merge(v, w, Double::sum);
                baseAdjacency.get(v).merge(u, w, Double::sum);
            }
        }

        double totalWeight = 0d;
        for (int i = 0; i < n; i++) {
            totalWeight += 2d * baseSelfLoop[i];
            for (double w : baseAdjacency.get(i).values()) {
                totalWeight += w;
            }
        }

        // community of every original vertex in the current (coarsest merged) partition P;
        // defaults to singletons so a structureless graph returns each vertex on its own.
        int[] vertexToCommunity = new int[n];
        for (int i = 0; i < n; i++) {
            vertexToCommunity[i] = i;
        }

        if (totalWeight > 0d) {
            CommunityAggregation.Level level =
                new CommunityAggregation.Level(baseAdjacency, baseSelfLoop);
            int[] vertexToNode = new int[n];
            for (int i = 0; i < n; i++) {
                vertexToNode[i] = i;
            }
            while (true) {
                int[] partition = localMoving(level, totalWeight);
                if (CommunityAggregation.numberOfCommunities(partition) == level.size()) {
                    // Local moving merged nothing: the previously recorded partition stands.
                    break;
                }
                // Record this (coarser) partition projected to original vertices.
                for (int v = 0; v < n; v++) {
                    vertexToCommunity[v] = partition[vertexToNode[v]];
                }
                int[] refined = refine(level, partition, totalWeight);
                CommunityAggregation.Level next = CommunityAggregation.aggregate(level, refined);
                if (next.size() >= level.size()) {
                    // Refinement could not contract the graph further; stop to guarantee
                    // termination. The recorded partition is the result.
                    break;
                }
                for (int v = 0; v < n; v++) {
                    vertexToNode[v] = refined[vertexToNode[v]];
                }
                level = next;
            }
        }

        // Guarantee well-connected communities: split each community into the connected components
        // of the subgraph it induces in the original graph. This never decreases modularity.
        int[] connected = splitIntoConnectedComponents(vertexToCommunity, baseAdjacency);

        result = buildClustering(indexToVertex, connected);
        modularity = totalWeight > 0d
            ? new UndirectedModularityMeasurer<>(graph).modularity(result.getClusters())
            : 0d;
    }

    /**
     * Local-moving phase (identical objective to {@link LouvainClustering}). Each node starts in
     * its own community and is greedily relocated to the neighbouring community of maximum
     * modularity gain until no move improves the objective. Returns a compacted community label per
     * node.
     */
    private int[] localMoving(CommunityAggregation.Level level, double totalWeight)
    {
        final List<Map<Integer, Double>> adjacency = level.adjacency;
        final int n = level.size();
        double[] degree = CommunityAggregation.degrees(level);
        double[] sigmaTot = degree.clone();
        int[] community = new int[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            community[i] = i;
            order[i] = i;
        }

        boolean improvement = true;
        while (improvement) {
            improvement = false;
            Collections.shuffle(Arrays.asList(order), rng);
            for (int oi = 0; oi < n; oi++) {
                int i = order[oi];
                double ki = degree[i];
                Map<Integer, Double> weightToCommunity = new HashMap<>();
                for (Map.Entry<Integer, Double> en : adjacency.get(i).entrySet()) {
                    weightToCommunity.merge(community[en.getKey()], en.getValue(), Double::sum);
                }
                int currentCommunity = community[i];
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
        return CommunityAggregation.compact(community);
    }

    /**
     * Refinement phase. Within each community of {@code partition}, starts every node in its own
     * refined sub-community and merges singleton nodes into a neighbouring sub-community of the
     * same community when the modularity gain is positive. Merges happen only along edges, so each
     * refined sub-community is connected and is contained in a single {@code partition} community.
     * Returns a compacted refined label per node.
     */
    private int[] refine(CommunityAggregation.Level level, int[] partition, double totalWeight)
    {
        final List<Map<Integer, Double>> adjacency = level.adjacency;
        final int n = level.size();
        double[] degree = CommunityAggregation.degrees(level);
        double[] sigmaTot = degree.clone();
        int[] refined = new int[n];
        int[] communitySize = new int[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            refined[i] = i;
            communitySize[i] = 1;
            order[i] = i;
        }
        Collections.shuffle(Arrays.asList(order), rng);

        for (int oi = 0; oi < n; oi++) {
            int i = order[oi];
            // Only merge nodes that are still singletons in the refinement (Leiden convention).
            if (communitySize[refined[i]] != 1) {
                continue;
            }
            double ki = degree[i];
            // Weight from i to each refined sub-community within the SAME partition community.
            Map<Integer, Double> weightToRefined = new HashMap<>();
            for (Map.Entry<Integer, Double> en : adjacency.get(i).entrySet()) {
                int j = en.getKey();
                if (partition[j] == partition[i]) {
                    weightToRefined.merge(refined[j], en.getValue(), Double::sum);
                }
            }
            int currentRefined = refined[i];
            sigmaTot[currentRefined] -= ki;
            // Baseline (staying as an isolated singleton) gain is 0; only a positive-gain neighbour
            // along an edge wins, which keeps every sub-community connected.
            int bestRefined = currentRefined;
            double bestGain = 0d;
            for (Map.Entry<Integer, Double> en : weightToRefined.entrySet()) {
                int c = en.getKey();
                if (c == currentRefined) {
                    continue;
                }
                double gain = en.getValue() - sigmaTot[c] * ki / totalWeight;
                if (gain > bestGain + tolerance) {
                    bestGain = gain;
                    bestRefined = c;
                }
            }
            sigmaTot[bestRefined] += ki;
            if (bestRefined != currentRefined) {
                refined[i] = bestRefined;
                communitySize[bestRefined]++;
                communitySize[currentRefined]--;
            }
        }
        return CommunityAggregation.compact(refined);
    }

    /**
     * Splits each community of {@code community} (indexed by original vertex) into the connected
     * components of the subgraph it induces in the original graph (described by {@code adjacency}).
     * Guarantees that every returned community is connected.
     */
    private int[] splitIntoConnectedComponents(
        int[] community, List<Map<Integer, Double>> adjacency)
    {
        final int n = community.length;
        int[] componentOf = new int[n];
        Arrays.fill(componentOf, -1);
        int nextComponent = 0;
        int[] queue = new int[n];
        for (int s = 0; s < n; s++) {
            if (componentOf[s] != -1) {
                continue;
            }
            int label = nextComponent++;
            int head = 0;
            int tail = 0;
            queue[tail++] = s;
            componentOf[s] = label;
            while (head < tail) {
                int u = queue[head++];
                for (int v : adjacency.get(u).keySet()) {
                    if (componentOf[v] == -1 && community[v] == community[u]) {
                        componentOf[v] = label;
                        queue[tail++] = v;
                    }
                }
            }
        }
        return componentOf;
    }

    private Clustering<V> buildClustering(List<V> indexToVertex, int[] community)
    {
        int[] compacted = CommunityAggregation.compact(community);
        int k = CommunityAggregation.numberOfCommunities(compacted);
        List<Set<V>> clusters = new ArrayList<>(k);
        for (int c = 0; c < k; c++) {
            clusters.add(new LinkedHashSet<>());
        }
        for (int v = 0; v < compacted.length; v++) {
            clusters.get(compacted[v]).add(indexToVertex.get(v));
        }
        return new ClusteringImpl<>(clusters);
    }
}
