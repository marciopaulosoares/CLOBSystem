package com.mb.crypto.loadtest;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe metrics collector for load test statistics.
 * Captures counters, latencies, and calculates percentiles.
 */
public class MetricsCollector {
    private final LongAdder totalOrdersSubmitted = new LongAdder();
    private final LongAdder ordersFullyExecuted = new LongAdder();
    private final LongAdder ordersPartiallyExecuted = new LongAdder();
    private final LongAdder ordersCanceled = new LongAdder();
    private final LongAdder failedOperations = new LongAdder();

    // Latency tracking (in nanoseconds)
    private final LongAdder latencySum = new LongAdder();
    private final LongAdder latencyCount = new LongAdder();
    private final AtomicLong[] latencies;
    private final AtomicLong latencyIndex = new AtomicLong(0);

    private final long startTime = System.nanoTime();
    private volatile long endTime;

    public MetricsCollector(int maxLatencySamples) {
        this.latencies = new AtomicLong[maxLatencySamples];
        for (int i = 0; i < maxLatencySamples; i++) {
            this.latencies[i] = new AtomicLong(0);
        }
    }

    // Recording methods
    public void recordOrderSubmitted() {
        totalOrdersSubmitted.increment();
    }

    public void recordOrderFullyExecuted() {
        ordersFullyExecuted.increment();
    }

    public void recordOrderPartiallyExecuted() {
        ordersPartiallyExecuted.increment();
    }

    public void recordOrderCanceled() {
        ordersCanceled.increment();
    }

    public void recordFailedOperation() {
        failedOperations.increment();
    }

    public void recordLatency(long latencyNanos) {
        latencyCount.increment();
        latencySum.add(latencyNanos);

        // Circular buffer for latency samples
        long idx = latencyIndex.getAndIncrement();
        int bufferIdx = (int) (idx % latencies.length);
        latencies[bufferIdx].set(latencyNanos);
    }

    public void stop() {
        this.endTime = System.nanoTime();
    }

    // Metric calculations
    public long getTotalOrdersSubmitted() {
        return totalOrdersSubmitted.sum();
    }

    public long getOrdersFullyExecuted() {
        return ordersFullyExecuted.sum();
    }

    public long getOrdersPartiallyExecuted() {
        return ordersPartiallyExecuted.sum();
    }

    public long getTotalOrdersExecuted() {
        return ordersFullyExecuted.sum() + ordersPartiallyExecuted.sum();
    }

    public long getOrdersCanceled() {
        return ordersCanceled.sum();
    }

    public long getFailedOperations() {
        return failedOperations.sum();
    }

    public double getExecutionRate() {
        long submitted = getTotalOrdersSubmitted();
        if (submitted == 0) return 0.0;
        return (getTotalOrdersExecuted() / (double) submitted) * 100.0;
    }

    public double getThroughput() {
        if (endTime == 0) {
            endTime = System.nanoTime();
        }
        long durationSeconds = (endTime - startTime) / 1_000_000_000L;
        if (durationSeconds == 0) durationSeconds = 1;
        return getTotalOrdersSubmitted() / (double) durationSeconds;
    }

    public double getAverageLatencyMs() {
        long count = latencyCount.sum();
        if (count == 0) return 0.0;
        return (latencySum.sum() / (double) count) / 1_000_000.0; // nanos to ms
    }

    public double getP95LatencyMs() {
        return getPercentileLatencyMs(95);
    }

    public double getP99LatencyMs() {
        return getPercentileLatencyMs(99);
    }

    private double getPercentileLatencyMs(int percentile) {
        long count = latencyCount.sum();
        if (count == 0) return 0.0;

        long[] samples = new long[(int) Math.min(count, latencies.length)];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = latencies[i].get();
        }

        Arrays.sort(samples);
        int index = (int) ((percentile / 100.0) * samples.length);
        if (index >= samples.length) {
            index = samples.length - 1;
        }

        return samples[index] / 1_000_000.0; // nanos to ms
    }

    public long getTestDurationSeconds() {
        if (endTime == 0) {
            endTime = System.nanoTime();
        }
        return (endTime - startTime) / 1_000_000_000L;
    }

    @Override
    public String toString() {
        return "MetricsCollector{" +
                "totalOrdersSubmitted=" + getTotalOrdersSubmitted() +
                ", ordersFullyExecuted=" + getOrdersFullyExecuted() +
                ", ordersPartiallyExecuted=" + getOrdersPartiallyExecuted() +
                ", ordersCanceled=" + getOrdersCanceled() +
                ", failedOperations=" + getFailedOperations() +
                ", executionRate=" + String.format("%.2f", getExecutionRate()) + "%" +
                ", throughput=" + String.format("%.2f", getThroughput()) + " orders/sec" +
                ", avgLatency=" + String.format("%.4f", getAverageLatencyMs()) + "ms" +
                ", p95Latency=" + String.format("%.4f", getP95LatencyMs()) + "ms" +
                ", p99Latency=" + String.format("%.4f", getP99LatencyMs()) + "ms" +
                '}';
    }
}
