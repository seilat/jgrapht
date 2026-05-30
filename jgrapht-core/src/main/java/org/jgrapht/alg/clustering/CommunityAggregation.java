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

import java.util.*;

/**
 * Shared community-aggregation primitives for the multilevel community-detection algorithms in
 * this package ({@link LouvainClustering} and Leiden). A graph level is represented as an
 * integer-indexed weighted adjacency (off-diagonal neighbour weights) plus a per-node self-loop
 * weight; the weighted degree of node {@code i} is {@code 2*selfLoop[i] + Σ adjacency[i].values()},
 * matching the conventions of {@link UndirectedModularityMeasurer}.
 *
 * <p>
 * All methods are static and side-effect-free on their inputs. {@link #aggregate} <em>compacts the
 * supplied community labelling internally</em>, so callers may pass an arbitrary (non-dense)
 * labelling — important for Leiden, whose refinement phase yields non-compact sub-community labels.
 *
 * @author seilat
 */
final class CommunityAggregation
{
    private CommunityAggregation()
    {
    }

    /**
     * An aggregated graph level: off-diagonal weighted adjacency and per-node self-loop weights.
     */
    static final class Level
    {
        final List<Map<Integer, Double>> adjacency;
        final double[] selfLoop;

        Level(List<Map<Integer, Double>> adjacency, double[] selfLoop)
        {
            this.adjacency = adjacency;
            this.selfLoop = selfLoop;
        }

        int size()
        {
            return adjacency.size();
        }
    }

    /**
     * Relabels arbitrary community ids to a dense range {@code [0, k)} preserving first-seen order.
     *
     * @param labels arbitrary integer labels
     * @return a new array with labels remapped to {@code [0, k)}
     */
    static int[] compact(int[] labels)
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
     *
     * @param compactedLabels labels already in dense {@code [0, k)} form
     * @return the number of communities {@code k}
     */
    static int numberOfCommunities(int[] compactedLabels)
    {
        int max = -1;
        for (int label : compactedLabels) {
            if (label > max) {
                max = label;
            }
        }
        return max + 1;
    }

    /**
     * Contracts each community of {@code level} into a single node. Intra-community edges (and
     * pre-existing self-loops) become the new node's self-loop; inter-community edges are summed
     * into the new adjacency. Degrees are conserved: the aggregated node degree equals the total
     * degree of its members.
     *
     * <p>
     * The {@code community} labelling is compacted internally, so it need not be dense; node
     * {@code i} of {@code level} joins aggregated node {@code compact(community)[i]}.
     *
     * @param level the current level
     * @param community a community label per node of {@code level} (any integers)
     * @return the aggregated level with one node per distinct community
     */
    static Level aggregate(Level level, int[] community)
    {
        int[] dense = compact(community);
        int k = numberOfCommunities(dense);
        List<Map<Integer, Double>> adjacency = new ArrayList<>(k);
        for (int c = 0; c < k; c++) {
            adjacency.add(new HashMap<>());
        }
        double[] selfLoop = new double[k];
        double[] internalAccumulator = new double[k];
        final int n = level.size();
        for (int u = 0; u < n; u++) {
            int cu = dense[u];
            selfLoop[cu] += level.selfLoop[u];
            for (Map.Entry<Integer, Double> en : level.adjacency.get(u).entrySet()) {
                int cv = dense[en.getKey()];
                if (cu == cv) {
                    // Each intra-community edge is seen once per direction; halved below.
                    internalAccumulator[cu] += en.getValue();
                } else {
                    adjacency.get(cu).merge(cv, en.getValue(), Double::sum);
                }
            }
        }
        for (int c = 0; c < k; c++) {
            selfLoop[c] += internalAccumulator[c] / 2d;
        }
        return new Level(adjacency, selfLoop);
    }

    /**
     * Computes the weighted degree of every node of {@code level}:
     * {@code degree[i] = 2*selfLoop[i] + Σ adjacency[i].values()}.
     *
     * @param level the level
     * @return per-node weighted degrees
     */
    static double[] degrees(Level level)
    {
        final int n = level.size();
        double[] degree = new double[n];
        for (int i = 0; i < n; i++) {
            double d = 2d * level.selfLoop[i];
            for (double w : level.adjacency.get(i).values()) {
                d += w;
            }
            degree[i] = d;
        }
        return degree;
    }
}
