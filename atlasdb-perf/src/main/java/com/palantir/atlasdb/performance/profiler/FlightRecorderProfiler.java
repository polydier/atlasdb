package com.palantir.atlasdb.performance.profiler;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.ExternalProfiler;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;

public class FlightRecorderProfiler implements ExternalProfiler {

    @Override
    public Collection<String> addJVMInvokeOptions(BenchmarkParams params) {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> addJVMOptions(BenchmarkParams params) {
        String filename = createOutputFilename(params);
        long duration = getDurationSeconds(params.getWarmup()) + getDurationSeconds(params.getMeasurement());
        return Arrays.asList(
                "-XX:+UnlockCommercialFeatures",
                "-XX:+FlightRecorder",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+DebugNonSafepoints",
                String.format("-XX:StartFlightRecording=settings=profile,duration=%ds,filename=%s", duration, filename));
    }

    private String createOutputFilename(BenchmarkParams params) {
        StringBuilder parameters = new StringBuilder();
        for (String param : params.getParamsKeys()) {
            if (parameters.length() != 0) {
                parameters.append('-');
            }
            parameters.append(param)
                    .append('-')
                    .append(params.getParam(param));
        }

        return params.getBenchmark() + "_" + parameters + ".jfr";
    }

    private long getDurationSeconds(IterationParams warmup) {
        return warmup.getCount() * warmup.getTime().convertTo(TimeUnit.SECONDS);
    }

    @Override
    public void beforeTrial(BenchmarkParams benchmarkParams) {
    }

    @Override
    public Collection<? extends Result> afterTrial(BenchmarkResult br, long pid, File stdOut, File stdErr) {
        return Collections.emptyList();
    }

    @Override
    public boolean allowPrintOut() {
        return true;
    }

    @Override
    public boolean allowPrintErr() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Generates Java Flight Recorder profile output for each benchmark";
    }

}
