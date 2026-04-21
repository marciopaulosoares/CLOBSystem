package com.mb.crypto.loadtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates human-readable load test reports.
 * Outputs to console and saves to file.
 */
public class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generate and save a summary report
     */
    public void generateReport(LoadProfile profile, MetricsCollector metrics) {
        String report = buildReport(profile, metrics);

        // Print to console
        System.out.println(report);

        // Save to file
        saveToFile(report);
    }

    private String buildReport(LoadProfile profile, MetricsCollector metrics) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("CLOB LOAD TEST REPORT\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Generated: ").append(LocalDateTime.now().format(DATE_FORMAT)).append("\n\n");

        // Load Configuration
        sb.append("LOAD CONFIGURATION\n");
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("  Thread Count:              %d\n", profile.getThreadCount()));
        sb.append(String.format("  Test Duration:            %d seconds\n", profile.getTestDurationSeconds()));
        sb.append(String.format("  Target Throughput:        %d orders/sec\n", profile.getOrdersPerSecond()));
        sb.append(String.format("  Number of Accounts:       %d\n", profile.getNumberOfAccounts()));
        sb.append("\n");

        // Market Behavior
        sb.append("MARKET BEHAVIOR CONFIGURATION\n");
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("  Base Price:               $%.2f\n", profile.getBasePrice()));
        sb.append(String.format("  Price Spread:             $%.2f\n", profile.getPriceSpread()));
        sb.append(String.format("  Price Volatility:         %.2f%%\n", profile.getPriceVolatility()));
        sb.append(String.format("  Initial Liquidity Depth:  %d orders\n", profile.getInitialLiquidityDepth()));
        sb.append("\n");

        // Operation Distribution
        sb.append("OPERATION DISTRIBUTION\n");
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("  New Orders:               %d%%\n", profile.getNewOrderPercentage()));
        sb.append(String.format("  Cancellations:            %d%%\n", profile.getCancelPercentage()));
        sb.append(String.format("  Queries:                  %d%%\n", profile.getQueryPercentage()));
        sb.append("\n");

        // Test Results
        sb.append("TEST RESULTS\n");
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("  Actual Test Duration:     %d seconds\n", metrics.getTestDurationSeconds()));
        sb.append("\n");

        // Order Statistics
        sb.append("ORDER STATISTICS\n");
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("  Total Orders Submitted:   %,d\n", metrics.getTotalOrdersSubmitted()));
        sb.append(String.format("  Fully Executed:           %,d\n", metrics.getOrdersFullyExecuted()));
        sb.append(String.format("  Partially Executed:       %,d\n", metrics.getOrdersPartiallyExecuted()));
        sb.append(String.format("  Total Executed:           %,d\n", metrics.getTotalOrdersExecuted()));
        sb.append(String.format("  Canceled:                 %,d\n", metrics.getOrdersCanceled()));
        sb.append(String.format("  Failed Operations:        %,d\n", metrics.getFailedOperations()));
        sb.append("\n");

        // Performance Metrics
        sb.append("PERFORMANCE METRICS\n");
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("  Execution Rate:           %.2f%%\n", metrics.getExecutionRate()));
        sb.append(String.format("  Throughput:               %.2f orders/sec\n", metrics.getThroughput()));
        sb.append(String.format("  Average Latency:          %.4f ms\n", metrics.getAverageLatencyMs()));
        sb.append(String.format("  P95 Latency:              %.4f ms\n", metrics.getP95LatencyMs()));
        sb.append(String.format("  P99 Latency:              %.4f ms\n", metrics.getP99LatencyMs()));
        sb.append("\n");

        sb.append("=".repeat(80)).append("\n");

        return sb.toString();
    }

    private void saveToFile(String report) {
        String filePath = "logs/report.txt";
        try (FileWriter fw = new FileWriter(filePath);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.print(report);
            pw.flush();
            logger.info("Report saved to: {}", filePath);
        } catch (IOException e) {
            logger.error("Failed to write report to file: {}", filePath, e);
        }
    }

    /**
     * Save error log entry
     */
    public static void logError(String errorMessage, Throwable throwable) {
        String filePath = "logs/errors.log";
        try (FileWriter fw = new FileWriter(filePath, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("[" + LocalDateTime.now().format(DATE_FORMAT) + "] " + errorMessage);
            if (throwable != null) {
                throwable.printStackTrace(pw);
            }
            pw.println("---");
            pw.flush();
        } catch (IOException e) {
            logger.error("Failed to write to error log: {}", filePath, e);
        }
    }
}
