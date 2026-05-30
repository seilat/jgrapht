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
package org.jgrapht.alg.hash;

import org.jgrapht.*;

import java.nio.charset.*;
import java.security.*;
import java.util.*;

/**
 * The 1-dimensional Weisfeiler-Lehman (1-WL) graph hash.
 *
 * <p>
 * This algorithm computes a short, deterministic fingerprint of a graph that is invariant under
 * graph isomorphism: two isomorphic graphs always receive the same hash, regardless of the identity
 * or insertion order of their vertices and edges. The hash is computed by iteratively refining a
 * structural colouring of the vertices &mdash; the same colour-refinement (na&iuml;ve vertex
 * classification) procedure that underlies the 1-WL isomorphism test &mdash; and then aggregating
 * the histogram of vertex colours from every round into a single digest. The procedure is closely
 * related to {@link org.jgrapht.alg.color.ColorRefinementAlgorithm}, which computes the coarsest
 * stable colouring; here we instead retain the colour histogram of each round to build a
 * fingerprint.
 *
 * <p>
 * Equality of hashes is a <em>necessary but not sufficient</em> condition for isomorphism: a
 * mismatch proves the graphs are not isomorphic, but a match does not prove that they are (1-WL
 * cannot distinguish certain non-isomorphic graphs, e.g. two triangles versus a single hexagon when
 * both are regular). The hash is therefore well suited as a fast pre-filter or as a content key for
 * caching and deduplication, not as a decision procedure for isomorphism.
 *
 * <p>
 * In this first version vertices are initialised by their degree (in- and out-degree for directed
 * graphs) and edges are treated as unweighted and unlabelled. For directed graphs incoming and
 * outgoing neighbours are kept distinct, so reversing all edges generally changes the hash.
 *
 * <p>
 * The running time is $O(k \cdot (|V| + |E|) \log |V|)$ for {@code k} iterations, dominated by the
 * per-round sort of each vertex's neighbour multiset.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author seilat
 */
public class WeisfeilerLehmanGraphHash<V, E>
{
    /**
     * Default number of refinement iterations.
     */
    public static final int DEFAULT_ITERATIONS = 3;

    /**
     * Number of hexadecimal characters retained from each digest. 16 hex chars (64 bits) keeps
     * labels short while making accidental collisions negligible for practical graph sizes.
     */
    private static final int HASH_HEX_LENGTH = 16;

    private final Graph<V, E> graph;
    private final int iterations;
    private final boolean directed;
    private final MessageDigest digest;

    /**
     * Construct a new hasher with the {@link #DEFAULT_ITERATIONS default} number of iterations.
     *
     * @param graph the input graph
     */
    public WeisfeilerLehmanGraphHash(Graph<V, E> graph)
    {
        this(graph, DEFAULT_ITERATIONS);
    }

    /**
     * Construct a new hasher.
     *
     * @param graph the input graph
     * @param iterations the number of colour-refinement iterations (must be non-negative); zero
     *        yields a hash of the degree histogram alone
     * @throws IllegalArgumentException if {@code iterations} is negative
     */
    public WeisfeilerLehmanGraphHash(Graph<V, E> graph, int iterations)
    {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");
        if (iterations < 0) {
            throw new IllegalArgumentException("iterations must be non-negative");
        }
        this.iterations = iterations;
        this.directed = graph.getType().isDirected();
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest not available", e);
        }
    }

    /**
     * Compute the graph hash.
     *
     * <p>
     * The hash aggregates, in a canonical (sorted) order, the vertex-colour histogram of the
     * initial round and of every refinement round. It is invariant under isomorphism and under the
     * insertion order of vertices and edges.
     *
     * @return a hexadecimal hash string
     */
    public String getHash()
    {
        Map<V, String> labels = initialLabels();

        StringBuilder fingerprint = new StringBuilder();
        appendHistogram(fingerprint, 0, labels);
        for (int round = 1; round <= iterations; round++) {
            labels = refine(labels);
            appendHistogram(fingerprint, round, labels);
        }
        return hashString(fingerprint.toString());
    }

    /**
     * Compute the final per-vertex colour (subtree hash) after all refinement iterations.
     *
     * <p>
     * Each returned value is the Weisfeiler-Lehman colour of a vertex, i.e. a hash of the rooted
     * subtree of radius {@code iterations} around it. Two vertices share a colour exactly when 1-WL
     * cannot tell their neighbourhoods apart up to that radius.
     *
     * @return a map from each vertex to its final colour hash
     */
    public Map<V, String> getVertexHashes()
    {
        Map<V, String> labels = initialLabels();
        for (int round = 1; round <= iterations; round++) {
            labels = refine(labels);
        }
        return labels;
    }

    /**
     * Build the round-0 labels from vertex degrees.
     *
     * @return a map from each vertex to its initial colour hash
     */
    private Map<V, String> initialLabels()
    {
        Map<V, String> labels = HashMap.newHashMap(graph.vertexSet().size());
        for (V v : graph.vertexSet()) {
            String init = directed
                ? graph.inDegreeOf(v) + "," + graph.outDegreeOf(v) : Integer.toString(graph.degreeOf(v));
            labels.put(v, hashString(init));
        }
        return labels;
    }

    /**
     * Perform a single colour-refinement round: each vertex's new colour is a hash of its current
     * colour together with the sorted multiset of its neighbours' colours.
     *
     * @param labels the current colouring
     * @return the refined colouring
     */
    private Map<V, String> refine(Map<V, String> labels)
    {
        Map<V, String> next = HashMap.newHashMap(graph.vertexSet().size());
        List<String> neighbourLabels = new ArrayList<>();

        for (V v : graph.vertexSet()) {
            neighbourLabels.clear();
            if (directed) {
                // keep direction: tag outgoing vs incoming so edge reversal changes the colour
                for (E e : graph.outgoingEdgesOf(v)) {
                    neighbourLabels.add("o" + labels.get(Graphs.getOppositeVertex(graph, e, v)));
                }
                for (E e : graph.incomingEdgesOf(v)) {
                    neighbourLabels.add("i" + labels.get(Graphs.getOppositeVertex(graph, e, v)));
                }
            } else {
                for (E e : graph.edgesOf(v)) {
                    neighbourLabels.add(labels.get(Graphs.getOppositeVertex(graph, e, v)));
                }
            }
            Collections.sort(neighbourLabels);

            StringBuilder sb = new StringBuilder(labels.get(v));
            for (String nl : neighbourLabels) {
                sb.append('|').append(nl);
            }
            next.put(v, hashString(sb.toString()));
        }
        return next;
    }

    /**
     * Append the canonical, sorted colour histogram of a round to the fingerprint builder.
     *
     * @param fingerprint the fingerprint under construction
     * @param round the round index (tagged into the fingerprint so rounds cannot alias)
     * @param labels the colouring of this round
     */
    private void appendHistogram(StringBuilder fingerprint, int round, Map<V, String> labels)
    {
        Map<String, Integer> counts = new TreeMap<>();
        for (String label : labels.values()) {
            counts.merge(label, 1, Integer::sum);
        }
        fingerprint.append('R').append(round).append('{');
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            fingerprint.append(entry.getKey()).append(':').append(entry.getValue()).append(',');
        }
        fingerprint.append('}');
    }

    /**
     * Hash a string to a fixed-length hexadecimal digest.
     *
     * @param s the input string
     * @return the truncated SHA-256 digest as a hexadecimal string
     */
    private String hashString(String s)
    {
        byte[] d = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        char[] hex = new char[HASH_HEX_LENGTH];
        for (int i = 0; i < HASH_HEX_LENGTH / 2; i++) {
            hex[2 * i] = Character.forDigit((d[i] >> 4) & 0xF, 16);
            hex[2 * i + 1] = Character.forDigit(d[i] & 0xF, 16);
        }
        return new String(hex);
    }
}
