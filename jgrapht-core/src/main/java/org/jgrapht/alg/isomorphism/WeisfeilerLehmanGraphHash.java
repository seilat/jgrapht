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
package org.jgrapht.alg.isomorphism;

import org.jgrapht.*;

import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.function.*;

/**
 * The 1-dimensional Weisfeiler-Lehman (1-WL) graph hash.
 *
 * <p>
 * This algorithm computes a short, deterministic fingerprint of a graph that is invariant under
 * graph isomorphism: two isomorphic graphs always receive the same hash, regardless of the identity
 * or insertion order of their vertices and edges. The hash is computed with the 1-WL
 * colour-refinement procedure &mdash; the same vertex-classification that underlies the 1-WL
 * isomorphism test and {@link ColorRefinementAlgorithm} &mdash; aggregating the histogram of vertex
 * colours from every round into a single digest. See B.&nbsp;Weisfeiler and A.&nbsp;Leman, "The
 * reduction of a graph to canonical form and the algebra which appears therein", 1968, and
 * N.&nbsp;Shervashidze et al., "Weisfeiler-Lehman graph kernels", JMLR 12, 2011.
 *
 * <p>
 * Equality of hashes is a <em>necessary but not sufficient</em> condition for isomorphism: a
 * mismatch proves the graphs are not isomorphic, but a match does not prove that they are (1-WL
 * cannot distinguish certain non-isomorphic graphs, e.g. a single 6-cycle versus two disjoint
 * triangles, both 2-regular). The hash is therefore well suited as a fast pre-filter or as a
 * content key for caching and deduplication, not as a decision procedure for isomorphism.
 *
 * <p>
 * <b>Initial colours and labels.</b> By default vertices are initialised by their degree (separately
 * by in- and out-degree for directed graphs) and edges are unlabelled. A custom
 * {@code vertexLabelFunction} and/or {@code edgeLabelFunction} may be supplied to fold
 * application-defined vertex and edge labels into the colouring; when given, the vertex-label
 * function <em>replaces</em> the degree initialisation. Edge weights are not used.
 *
 * <p>
 * <b>Graph-type handling.</b> For directed graphs incoming and outgoing neighbours are kept
 * distinct, so reversing all edges generally changes the hash; mixed graphs are rejected. Self-loops
 * are honoured. Parallel edges are handled per the chosen {@link ParallelEdgeRule}:
 * {@link ParallelEdgeRule#COUNT} (the default) counts each parallel edge with multiplicity, so a
 * multigraph hashes differently from its underlying simple graph; {@link ParallelEdgeRule#IGNORE}
 * collapses identical neighbour contributions, matching {@link ColorRefinementAlgorithm}.
 *
 * <p>
 * <b>Convergence.</b> Colour refinement is monotone and reaches a fixed point after at most $|V|$
 * rounds; once the partition stops getting finer, further iterations add no information. This
 * implementation aggregates the initial round and each refinement round up to {@code iterations}
 * <em>or until the colouring stabilises, whichever comes first</em>, so the hash is stable for any
 * {@code iterations} value at or beyond convergence and a large {@code iterations} never causes more
 * than $|V|$ rounds of work.
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
 * <p>
 * <b>Stability contract.</b> The digest is deterministic for a given graph and configuration, but
 * its exact string form is an implementation detail and is not guaranteed to be stable across
 * JGraphT versions. Callers may persist it as a cache key within one version; they should not treat
 * it as a cross-version serialisation format or a cryptographic commitment.
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
     * Strategy for treating parallel edges when aggregating a vertex's neighbour colours.
     */
    public enum ParallelEdgeRule
    {
        /**
         * Count each parallel edge separately: a vertex's neighbour contributions form a multiset.
         * More discriminative; a multigraph hashes differently from its underlying simple graph.
         */
        COUNT,
        /**
         * Collapse identical neighbour contributions into a set, ignoring parallel-edge
         * multiplicity. Matches the neighbour deduplication of {@link ColorRefinementAlgorithm}.
         */
        IGNORE
    }

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
    private final ParallelEdgeRule parallelEdgeRule;
    private final Function<V, String> vertexLabelFunction;
    private final Function<E, String> edgeLabelFunction;

    /**
     * Construct a new hasher with the {@link #DEFAULT_ITERATIONS default} number of iterations,
     * degree-based initial colours, unlabelled edges and {@link ParallelEdgeRule#COUNT}.
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
     * Construct a new hasher with degree-based initial colours, unlabelled edges and
     * {@link ParallelEdgeRule#COUNT}.
     *
     * @param graph the input graph
     * @param iterations the maximum number of colour-refinement iterations (must be non-negative)
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if {@code iterations} is negative or {@code graph} is a mixed
     *         graph
     */
    public WeisfeilerLehmanGraphHash(Graph<V, E> graph, int iterations)
    {
        this(graph, iterations, null, null, ParallelEdgeRule.COUNT);
    }

    /**
     * Construct a fully configured hasher.
     *
     * @param graph the input graph
     * @param iterations the maximum number of colour-refinement iterations (must be non-negative);
     *        zero yields a hash of the initial-colour histogram alone. Refinement may stop earlier
     *        once the colouring stabilises.
     * @param vertexLabelFunction maps each vertex to an initial label, or {@code null} to use the
     *        default degree-based initialisation. When supplied it replaces the degree colour.
     * @param edgeLabelFunction maps each edge to a label folded into the neighbour aggregation, or
     *        {@code null} to treat edges as unlabelled
     * @param parallelEdgeRule how to treat parallel edges (must not be {@code null})
     * @throws NullPointerException if {@code graph} or {@code parallelEdgeRule} is {@code null}
     * @throws IllegalArgumentException if {@code iterations} is negative or {@code graph} is a mixed
     *         graph
     */
    public WeisfeilerLehmanGraphHash(
        Graph<V, E> graph, int iterations, Function<V, String> vertexLabelFunction,
        Function<E, String> edgeLabelFunction, ParallelEdgeRule parallelEdgeRule)
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
        this.vertexLabelFunction = vertexLabelFunction;
        this.edgeLabelFunction = edgeLabelFunction;
        this.parallelEdgeRule =
            Objects.requireNonNull(parallelEdgeRule, "parallelEdgeRule cannot be null");
    }

    /**
     * Compute the graph hash.
     *
     * <p>
     * The hash aggregates, in a canonical (sorted) order, the vertex-colour histogram of the initial
     * round and of every refinement round (up to {@code iterations} or convergence). It is invariant
     * under isomorphism and under the insertion order of vertices and edges.
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
     * Compute the per-iteration colour of every vertex.
     *
     * <p>
     * For each vertex the returned list gives its colour after {@code 0, 1, 2, ...} refinement
     * rounds (index {@code 0} is the initial colour), up to {@code iterations} or convergence. This
     * is the rooted-subtree "subgraph hash" sequence: index {@code i} captures the vertex's
     * neighbourhood out to radius {@code i}. All vertices' lists have the same length.
     *
     * @return an unmodifiable map from each vertex to its (unmodifiable) per-round colour list
     */
    public Map<V, List<String>> getVertexHashSequences()
    {
        Map<V, List<String>> sequences = HashMap.newHashMap(graph.vertexSet().size());
        Map<V, String> labels = initialLabels();
        for (V v : graph.vertexSet()) {
            List<String> seq = new ArrayList<>();
            seq.add(labels.get(v));
            sequences.put(v, seq);
        }
        int classes = distinctCount(labels);
        for (int round = 1; round <= iterations; round++) {
            Map<V, String> next = refine(labels);
            int nextClasses = distinctCount(next);
            if (nextClasses == classes) {
                break;
            }
            labels = next;
            classes = nextClasses;
            for (V v : graph.vertexSet()) {
                sequences.get(v).add(labels.get(v));
            }
        }
        Map<V, List<String>> result = HashMap.newHashMap(sequences.size());
        for (Map.Entry<V, List<String>> e : sequences.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(result);
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
     * Build the round-0 labels from the vertex-label function or, by default, vertex degrees.
     *
     * @return a map from each vertex to its initial colour hash
     */
    private Map<V, String> initialLabels()
    {
        Map<V, String> labels = HashMap.newHashMap(graph.vertexSet().size());
        for (V v : graph.vertexSet()) {
            String init;
            if (vertexLabelFunction != null) {
                init = String.valueOf(vertexLabelFunction.apply(v));
            } else if (directed) {
                init = graph.inDegreeOf(v) + "," + graph.outDegreeOf(v);
            } else {
                init = Integer.toString(graph.degreeOf(v));
            }
            labels.put(v, hashString(init));
        }
        return labels;
    }

    /**
     * Perform a single colour-refinement round: each vertex's new colour is a hash of its current
     * colour together with the sorted multiset (or set, per {@link ParallelEdgeRule}) of its
     * neighbours' contributions. A neighbour contribution combines an optional direction tag, the
     * optional edge label and the neighbour's current colour.
     *
     * @param labels the current colouring
     * @return the refined colouring
     */
    private Map<V, String> refine(Map<V, String> labels)
    {
        Map<V, String> next = HashMap.newHashMap(graph.vertexSet().size());
        List<String> contributions = new ArrayList<>();

        for (V v : graph.vertexSet()) {
            contributions.clear();
            if (directed) {
                for (E e : graph.outgoingEdgesOf(v)) {
                    contributions.add("o" + contribution(e, v, labels));
                }
                for (E e : graph.incomingEdgesOf(v)) {
                    contributions.add("i" + contribution(e, v, labels));
                }
            } else {
                for (E e : graph.edgesOf(v)) {
                    contributions.add(contribution(e, v, labels));
                }
            }
            if (parallelEdgeRule == ParallelEdgeRule.IGNORE) {
                List<String> distinct = new ArrayList<>(new LinkedHashSet<>(contributions));
                contributions.clear();
                contributions.addAll(distinct);
            }
            Collections.sort(contributions);

            StringBuilder sb = new StringBuilder(labels.get(v));
            for (String c : contributions) {
                sb.append('|').append(c);
            }
            next.put(v, hashString(sb.toString()));
        }
        return next;
    }

    /**
     * Build a neighbour contribution: the optional (fixed-length) edge-label hash followed by the
     * opposite vertex's current colour. Both components are fixed-length hex within an instance, so
     * the concatenation is unambiguous.
     *
     * @param e the incident edge
     * @param v the vertex being coloured
     * @param labels the current colouring
     * @return the contribution string for this edge
     */
    private String contribution(E e, V v, Map<V, String> labels)
    {
        String edgePart =
            edgeLabelFunction == null ? "" : hashString(String.valueOf(edgeLabelFunction.apply(e)));
        return edgePart + labels.get(Graphs.getOppositeVertex(graph, e, v));
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
     * Append the canonical, sorted colour histogram of a round to the fingerprint builder. The round
     * index is tagged in so that rounds cannot alias; SHA-256 hex labels contain only the characters
     * {@code 0-9a-f}, never the delimiters used here, so the serialisation is unambiguous.
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
