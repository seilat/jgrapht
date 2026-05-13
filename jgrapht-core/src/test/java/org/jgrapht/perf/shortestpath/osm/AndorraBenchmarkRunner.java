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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Manual JMH runner for the {@code osm/Andorra*Bench} harnesses. The class is named
 * {@code *Runner} rather than {@code *Test} so the maven-surefire default include
 * pattern does not pick it up; the bench is only executed when explicitly requested
 * via {@code -Dtest=AndorraBenchmarkRunner#runYen} (or {@code #runM2M}, {@code #runADP}).
 *
 * <p>
 * Each method writes its JMH summary to {@code target/jmh-andorra/<bench>.txt} so the
 * results survive surefire's stdout buffering.
 *
 * @author Shai Eilat
 */
class AndorraBenchmarkRunner
{
    private static final Path OUT_DIR = Path.of("target", "jmh-andorra");

    @Test
    void runYen() throws Exception
    {
        runBenchAverageTime(AndorraBoundedPrunedYenBench.class.getSimpleName(), "yen");
    }

    @Test
    void runM2M() throws Exception
    {
        // SingleShotTime mode + the pre-PR-#1340 cost: a single call already
        // averages ~210 s on Andorra, so we force 0 warmup + 1 measurement
        // iteration and let the bench class' @BenchmarkMode govern the rest.
        runBenchSingleShot(
            AndorraDijkstraManyToManyGetPathsBench.class.getSimpleName(), "m2m");
    }

    @Test
    void runADP() throws Exception
    {
        runBenchAverageTime(
            AndorraAllDirectedPathsNonSimpleBench.class.getSimpleName(), "adp");
    }

    private static void runBenchAverageTime(String benchSimpleName, String outBase)
        throws RunnerException, java.io.IOException
    {
        runJmh(benchSimpleName, outBase, /*warmupIters=*/ 3, /*measureIters=*/ 5);
    }

    private static void runBenchSingleShot(String benchSimpleName, String outBase)
        throws RunnerException, java.io.IOException
    {
        runJmh(benchSimpleName, outBase, /*warmupIters=*/ 0, /*measureIters=*/ 1);
    }

    private static void runJmh(
        String benchSimpleName, String outBase, int warmupIters, int measureIters)
        throws RunnerException, java.io.IOException
    {
        Files.createDirectories(OUT_DIR);
        Path out = OUT_DIR.resolve(outBase + ".txt");
        OptionsBuilder builder = new OptionsBuilder();
        builder
            .include(".*" + benchSimpleName + ".*")
            // run in the same JVM as surefire to inherit its module-path / argLine.
            // less hermetic than a fork, but the alternative (fork=1) loses the
            // jgrapht-core module on the classpath of the JMH-forked JVM.
            .forks(0)
            .warmupIterations(warmupIters)
            .measurementIterations(measureIters)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            .timeUnit(TimeUnit.MILLISECONDS)
            .resultFormat(ResultFormatType.TEXT)
            .result(out.toString());
        if (warmupIters > 0) {
            builder.warmupTime(TimeValue.seconds(5));
        }
        if (measureIters > 0) {
            builder.measurementTime(TimeValue.seconds(10));
        }
        new Runner(builder.build()).run();
    }
}
