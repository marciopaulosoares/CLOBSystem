package com.mb.crypto.loadtest;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the load testing framework.
 * Coordinates test execution, metrics collection, and reporting.
 */
public class LoadTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(LoadTestRunner.class);

    private final LoadProfile loadProfile;
    private final MetricsCollector metricsCollector;
    private final ReportGenerator reportGenerator;
    private final OrderGenerator orderGenerator;

    public LoadTestRunner(LoadProfile loadProfile) {
        this.loadProfile = loadProfile;
        this.metricsCollector = new MetricsCollector(100_000);
        this.reportGenerator = new ReportGenerator();
        this.orderGenerator = new OrderGenerator(loadProfile);
    }

    /**
     * Run the load test
     */
    public void run() {
        logger.info("Starting CLOB load test with configuration: {}", loadProfile);

        ExecutorService executorService = Executors.newFixedThreadPool(loadProfile.getThreadCount());
        AtomicBoolean testRunning = new AtomicBoolean(true);

        try {
            // Preload the order book with initial liquidity
            preloadOrderBook();

            // Submit load test tasks
            List<Future<?>> futures = new ArrayList<>();
            long testDuration = loadProfile.getTestDurationSeconds() * 1000; // ms
            long testStartTime = System.currentTimeMillis();

            for (int i = 0; i < loadProfile.getThreadCount(); i++) {
                futures.add(executorService.submit(() ->
                        runLoadTestTask(testRunning, testStartTime, testDuration)
                ));
            }

            // Wait for all tasks to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    logger.error("Task failed: ", e.getCause());
                    ReportGenerator.logError("Task execution failed", e.getCause());
                    metricsCollector.recordFailedOperation();
                }
            }

            metricsCollector.stop();

            // Generate and print report
            reportGenerator.generateReport(loadProfile, metricsCollector);

            logger.info("Load test completed successfully");

        } catch (InterruptedException e) {
            logger.error("Load test interrupted: ", e);
            Thread.currentThread().interrupt();
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("ExecutorService did not terminate in time, forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted waiting for executor shutdown: ", e);
                executorService.shutdownNow();
            }
        }
    }

    /**
     * Preload the order book with initial liquidity
     */
    private void preloadOrderBook() {
        logger.info("Preloading order book with {} orders...", loadProfile.getInitialLiquidityDepth());
        // TODO: Implement order book preloading
        // This will need to create initial orders in ClobSystem
    }

    /**
     * Main test task executed by each thread
     */
    private void runLoadTestTask(AtomicBoolean testRunning, long testStartTime, long testDuration) {
        long ordersPerThreadPerSecond = loadProfile.getOrdersPerSecond() / loadProfile.getThreadCount();
        long timeBetweenOrdersMs = 1000 / Math.max(1, ordersPerThreadPerSecond);

        while (testRunning.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - testStartTime > testDuration) {
                testRunning.set(false);
                break;
            }

            try {
                // Generate and process order
                long operationStartTime = System.nanoTime();
                OrderGenerator.OrderData orderData = orderGenerator.generateOrder();
                executeOrder(orderData);
                long latencyNanos = System.nanoTime() - operationStartTime;

                metricsCollector.recordOrderSubmitted();
                metricsCollector.recordLatency(latencyNanos);

                // Throttle to match target throughput
                Thread.sleep(timeBetweenOrdersMs);

            } catch (InterruptedException e) {
                logger.debug("Load test task interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error executing order: ", e);
                ReportGenerator.logError("Order execution failed: " + e.getMessage(), e);
                metricsCollector.recordFailedOperation();
            }
        }

        logger.debug("Load test task completed");
    }

    /**
     * Execute an order operation against ClobSystem
     * TODO: Replace with actual ClobSystem calls
     */
    private void executeOrder(OrderGenerator.OrderData order) {
        // This is a placeholder - will integrate with real ClobSystem
        // Example:
        // if (order.operationType == OrderGenerator.OperationType.NEW_ORDER) {
        //     ClobSystem.placeOrder(order.accountId, order.side, order.price, order.quantity);
        // }

        // For now, simulate execution
        simulateExecution(order);
    }

    /**
     * Simulate execution for demo purposes
     */
    private void simulateExecution(OrderGenerator.OrderData order) {
        // Simulate different execution outcomes
        int outcome = ThreadLocalRandom.current().nextInt(100);

        if (outcome < 60) {
            metricsCollector.recordOrderFullyExecuted();
        } else if (outcome < 85) {
            metricsCollector.recordOrderPartiallyExecuted();
        } else {
            metricsCollector.recordOrderCanceled();
        }
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        try {
            // Load environment configuration from .env file
            Dotenv dotenv = Dotenv.load();

            // Read configuration from environment variables with defaults
            int threadCount = Integer.parseInt(dotenv.get("THREAD_COUNT", "10"));
            int testDurationSeconds = Integer.parseInt(dotenv.get("TEST_DURATION_SECONDS", "30"));
            int ordersPerSecond = Integer.parseInt(dotenv.get("ORDERS_PER_SECOND", "1000"));
            int numberOfAccounts = Integer.parseInt(dotenv.get("NUMBER_OF_ACCOUNTS", "50"));
            double basePrice = Double.parseDouble(dotenv.get("BASE_PRICE", "100.0"));
            double priceSpread = Double.parseDouble(dotenv.get("PRICE_SPREAD", "0.5"));
            double priceVolatility = Double.parseDouble(dotenv.get("PRICE_VOLATILITY", "2.0"));
            int initialLiquidityDepth = Integer.parseInt(dotenv.get("INITIAL_LIQUIDITY_DEPTH", "500"));
            int newOrderPercentage = Integer.parseInt(dotenv.get("NEW_ORDER_PERCENTAGE", "70"));
            int cancelPercentage = Integer.parseInt(dotenv.get("CANCEL_PERCENTAGE", "20"));
            int queryPercentage = Integer.parseInt(dotenv.get("QUERY_PERCENTAGE", "10"));

            // Create a load profile with configuration from .env
            LoadProfile profile = new LoadProfile.Builder()
                    .threadCount(threadCount)
                    .testDurationSeconds(testDurationSeconds)
                    .ordersPerSecond(ordersPerSecond)
                    .numberOfAccounts(numberOfAccounts)
                    .basePrice(basePrice)
                    .priceSpread(priceSpread)
                    .priceVolatility(priceVolatility)
                    .initialLiquidityDepth(initialLiquidityDepth)
                    .newOrderPercentage(newOrderPercentage)
                    .cancelPercentage(cancelPercentage)
                    .queryPercentage(queryPercentage)
                    .build();

            LoadTestRunner runner = new LoadTestRunner(profile);
            runner.run();

        } catch (Exception e) {
            logger.error("Fatal error in load test runner: ", e);
            ReportGenerator.logError("Fatal error", e);
            System.exit(1);
        }
    }
}
