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
 * fingerprint. See B. Weisfeiler and A. Leman, "The reduction of a graph to canonical form and the
 * algebra which appears therein", 1968, and N. Shervashidze et al., "Weisfeiler-Lehman graph
 * kernels", JMLR 12, 2011.
 *
 * <p>
 * Equality of hashes is a <em>necessary but not sufficient</em> condition for isomorphism: a
 * mismatch proves the graphs are not isomorphic, but a match does not prove that they are (1-WL
 * cannot distinguish certain non-isomorphic graphs, e.g. a single 6-cycle versus two disjoint
 * triangles, both 2-regular). The hash is therefore well suited as a fast pre-filter or as a
 * content key for caching and deduplication, not as a decision procedure for isomorphism.
 *
 * <p>
 * <b>Scope and conventions (v1).</b>
 * <ul>
 * <li>Vertices are initialised by their degree (separately by in- and out-degree for directed
 * graphs). Edges are treated as <em>unweighted and unlabelled</em>: edge weights do not affect the
 * hash.</li>
 * <li><b>Parallel edges are counted with multiplicity.</b> Each edge contributes one entry to a
 * vertex's neighbour multiset, so two graphs differing only in the number of edges between the same
 * pair of vertices generally hash differently. This intentionally differs from
 * {@code ColorRefinementAlgorithm}, which deduplicates neighbours.</li>
 * <li><b>Self-loops</b> are honoured. An undirected self-loop contributes the vertex's own colour
 * once to its neighbour multiset (and is counted twice by {@code degreeOf} in the initial label,
 * per the {@link Graph} contract); a directed self-loop contributes both an outgoing and an
 * incoming entry.</li>
 * <li>For directed graphs incoming and outgoing neighbours are kept distinct, so reversing all
 * edges generally changes the hash. <b>Mixed graphs are rejected</b> (a mixed graph cannot be
 * unambiguously classified as directed or undirected here).</li>
 * </ul>
 *
 * <p>
 * Colour refinement is monotone and reaches a fixed point after at most $|V|$ rounds; once the
 * partition stops getting finer, further iterations add no information. This implementation
 * therefore aggregates the initial round and each refinement round up to {@code iterations}
 * <em>or until the colouring stabilises, whichever comes first</em>. Consequently the hash is
 * stable for any {@code iterations} value at or beyond convergence, and a large {@code iterations}
 * never causes more than $|V|$ rounds of work.
 *
 * <p>
 * The running time is $O(k \cdot (|V| + |E|))$ plus the per-round neighbour sorts, for {@code k}
 * effective iterations.
 *
 * <p>
 * Instances are effectively immutable: the only mutable state needed for hashing (a
 * {@code MessageDigest}) is held per-thread, so concurrent calls &mdash; on the same instance or on
 * separate instances &mdash; are safe.
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
     * Per-thread SHA-256 digest. {@link MessageDigest} is stateful and not thread-safe, so a
     * {@link ThreadLocal} gives each thread its own instance; {@link MessageDigest#digest(byte[])}
     * resets the digest after each call, making reuse within a thread safe.
     */
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest not available", e);
        }
    });

    private final Graph<V, E> graph;
    private final int iterations;
    private final boolean directed;

    /**
     * Construct a new hasher with the {@link #DEFAULT_ITERATIONS default} number of iterations.
     *
     * @param graph the input graph
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if {@code graph} is a mixed graph
     */
    public WeisfeilerLehmanGraphHash(Graph<V, E> graph)
    {
        this(graph, DEFAULT_ITERATIONS);
    }

    /**
     * Construct a new hasher.
     *
     * @param graph the input graph
     * @param iterations the maximum number of colour-refinement iterations (must be non-negative);
     *        zero yields a hash of the degree histogram alone. Refinement may stop earlier once the
     *        colouring stabilises.
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if {@code iterations} is negative or {@code graph} is a mixed
     *         graph
     */
    public WeisfeilerLehmanGraphHash(Graph<V, E> graph, int iterations)
    {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");
        if (iterations < 0) {
            throw new IllegalArgumentException("iterations must be non-negative");
        }
        GraphType type = graph.getType();
        if (type.isMixed()) {
            throw new IllegalArgumentException("Mixed graphs are not supported");
        }
        this.iterations = iterations;
        this.directed = type.isDirected();
    }

    /**
     * Compute the graph hash.
     *
     * <p>
     * The hash aggregates, in a canonical (sorted) order, the vertex-colour histogram of the
     * initial round and of every refinement round (up to {@code iterations} or convergence). It is
     * invariant under isomorphism and under the insertion order of vertices and edges.
     *
     * @return a hexadecimal hash string
     */
    public String getHash()
    {
        StringBuilder fingerprint = new StringBuilder();
        computeColoring(fingerprint);
        return hashString(fingerprint.toString());
    }

    /**
     * Compute the final per-vertex colour (subtree hash) after refinement.
     *
     * <p>
     * Each returned value is the Weisfeiler-Lehman colour of a vertex, i.e. a hash of the rooted
     * subtree around it up to the effective radius (the smaller of {@code iterations} and the
     * convergence depth). Two vertices share a colour exactly when 1-WL cannot tell their
     * neighbourhoods apart up to that radius.
     *
     * @return an unmodifiable map from each vertex to its final colour hash
     */
    public Map<V, String> getVertexHashes()
    {
        return Collections.unmodifiableMap(computeColoring(null));
    }

    /**
     * Run colour refinement, optionally accumulating the per-round histogram fingerprint.
     *
     * <p>
     * Stops after {@code iterations} rounds or as soon as a round fails to increase the number of
     * colour classes (a fixed point: the partition is then stable). The converged round is not
     * appended to the fingerprint, since it carries the same partition as the previous round.
     *
     * @param fingerprint a builder to append per-round histograms to, or {@code null} to skip
     * @return the final colouring (vertex to colour hash)
     */
    private Map<V, String> computeColoring(StringBuilder fingerprint)
    {
        Map<V, String> labels = initialLabels();
        int classes = distinctCount(labels);
        if (fingerprint != null) {
            appendHistogram(fingerprint, 0, labels);
        }
        for (int round = 1; round <= iterations; round++) {
            Map<V, String> next = refine(labels);
            int nextClasses = distinctCount(next);
            if (nextClasses == classes) {
                break; // converged: the partition did not get finer
            }
            labels = next;
            classes = nextClasses;
            if (fingerprint != null) {
                appendHistogram(fingerprint, round, labels);
            }
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
                ? graph.inDegreeOf(v) + "," + graph.outDegreeOf(v)
                : Integer.toString(graph.degreeOf(v));
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
     * Number of distinct colours in a colouring.
     *
     * @param labels the colouring
     * @return the number of colour classes
     */
    private int distinctCount(Map<V, String> labels)
    {
        return new HashSet<>(labels.values()).size();
    }

    /**
     * Append the canonical, sorted colour histogram of a round to the fingerprint builder. The
     * round index is tagged in so that rounds cannot alias; SHA-256 hex labels contain only the
     * characters {@code 0-9a-f}, never the delimiters used here, so the serialisation is
     * unambiguous.
     *
     * @param fingerprint the fingerprint under construction
     * @param round the round index
     * @param labels the colouring of this round
     */
    private void appendHistogram(StringBuilder fingerprint, int round, Map<V, String> labels)
    {
        Map<String, Integer> counts = new TreeMap<>();
        for (String label : labels.values()) {
            counts.merge(label, 1, Integer::sum);
        }
        fingerprint.append('R').append(round).append('{');
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                fingerprint.append(',');
            }
            fingerprint.append(entry.getKey()).append(':').append(entry.getValue());
            first = false;
        }
        fingerprint.append('}');
    }

    /**
     * Hash a string to a hexadecimal SHA-256 digest. The full 256-bit digest is used for internal
     * colour labels so that distinct neighbourhoods are not collapsed by truncation.
     *
     * @param s the input string
     * @return the SHA-256 digest as a 64-character hexadecimal string
     */
    private static String hashString(String s)
    {
        byte[] d = SHA_256.get().digest(s.getBytes(StandardCharsets.UTF_8));
        char[] hex = new char[d.length * 2];
        for (int i = 0; i < d.length; i++) {
            hex[2 * i] = Character.forDigit((d[i] >> 4) & 0xF, 16);
            hex[2 * i + 1] = Character.forDigit(d[i] & 0xF, 16);
        }
        return new String(hex);
    }
}
