package com.mb.crypto.loadtest;

import com.mb.crypto.clob.ClobSystem;
import com.mb.crypto.clob.domain.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LoadTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(LoadTestRunner.class);

    private static final Instrument INSTRUMENT = new Instrument(Asset.BTC, Asset.BRL);
    // Each account receives generous initial balances so lock operations never fail under load
    private static final BigDecimal INITIAL_BRL = new BigDecimal("100000000"); // 100M BRL
    private static final BigDecimal INITIAL_BTC = new BigDecimal("100000");    // 100K BTC
    private static final int MAX_LIVE_ORDERS = 50_000;

    private final LoadProfile loadProfile;
    private final MetricsCollector metricsCollector;
    private final ReportGenerator reportGenerator;
    private final OrderGenerator orderGenerator;
    private final ClobSystem clobSystem;
    private final AtomicLong orderIdCounter = new AtomicLong(1);
    // Ring-buffer of placed orders available for cancel operations
    private final ConcurrentLinkedDeque<Order> liveOrders = new ConcurrentLinkedDeque<>();

    public LoadTestRunner(LoadProfile loadProfile) {
        this.loadProfile = loadProfile;
        this.metricsCollector = new MetricsCollector(100_000);
        this.reportGenerator = new ReportGenerator();
        this.orderGenerator = new OrderGenerator(loadProfile);
        this.clobSystem = buildClobSystem();
    }

    private ClobSystem buildClobSystem() {
        List<Account> accounts = new ArrayList<>(loadProfile.getNumberOfAccounts());
        for (int i = 0; i < loadProfile.getNumberOfAccounts(); i++) {
            Account account = new Account(new AccountId("account_" + i));
            account.deposit(Asset.BRL, INITIAL_BRL);
            account.deposit(Asset.BTC, INITIAL_BTC);
            accounts.add(account);
        }
        return new ClobSystem(List.of(INSTRUMENT), accounts);
    }

    public void run() {
        logger.info("Starting CLOB load test with configuration: {}", loadProfile);

        ExecutorService executorService = Executors.newFixedThreadPool(loadProfile.getThreadCount());
        AtomicBoolean testRunning = new AtomicBoolean(true);

        try {
            preloadOrderBook();

            List<Future<?>> futures = new ArrayList<>();
            long testDuration = loadProfile.getTestDurationSeconds() * 1000L;
            long testStartTime = System.currentTimeMillis();

            for (int i = 0; i < loadProfile.getThreadCount(); i++) {
                futures.add(executorService.submit(() ->
                        runLoadTestTask(testRunning, testStartTime, testDuration)
                ));
            }

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

    private void preloadOrderBook() {
        int depth = loadProfile.getInitialLiquidityDepth();
        logger.info("Preloading order book with {} orders...", depth);

        double basePrice = loadProfile.getBasePrice();
        double spread = loadProfile.getPriceSpread();
        int accounts = loadProfile.getNumberOfAccounts();
        int placed = 0;

        for (int i = 0; i < depth; i++) {
            try {
                // Alternate BUY/SELL; spread prices away from mid in equal steps
                OrderSide side = (i % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
                double offset = (i / 2 + 1) * (spread / Math.max(1, depth / 10.0));
                double rawPrice = (side == OrderSide.BUY)
                        ? basePrice - offset
                        : basePrice + offset;
                long priceUnits = Math.max(1L, Math.round(rawPrice));

                Order order = new Order(
                        orderIdCounter.getAndIncrement(),
                        side,
                        BigDecimal.valueOf(priceUnits),
                        BigDecimal.ONE,
                        OrderType.LIMIT,
                        new AccountId("account_" + (i % accounts)),
                        INSTRUMENT
                );
                clobSystem.placeOrder(order);
                trackLiveOrder(order);
                placed++;
            } catch (Exception e) {
                logger.warn("Preload order {} skipped: {}", i, e.getMessage());
            }
        }
        logger.info("Order book preloaded with {} orders", placed);
    }

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
                long operationStartTime = System.nanoTime();
                OrderGenerator.OrderData orderData = orderGenerator.generateOrder();
                executeOrder(orderData);
                long latencyNanos = System.nanoTime() - operationStartTime;

                metricsCollector.recordOrderSubmitted();
                metricsCollector.recordLatency(latencyNanos);

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

    private void executeOrder(OrderGenerator.OrderData data) {
        switch (data.operationType) {
            case NEW_ORDER -> {
                Order order = buildOrder(data);
                clobSystem.placeOrder(order);
                trackLiveOrder(order);
                recordOrderMetrics(order);
            }
            case CANCEL -> {
                Order target = liveOrders.pollFirst();
                if (target != null) {
                    try {
                        clobSystem.cancelOrder(target);
                        metricsCollector.recordOrderCanceled();
                    } catch (Exception e) {
                        // Order may already be filled before we could cancel — not a failure
                        logger.debug("Cancel skipped for order {}: {}", target.getOrderId(), e.getMessage());
                    }
                }
            }
            case QUERY -> clobSystem.getOrderBook(INSTRUMENT);
        }
    }

    private Order buildOrder(OrderGenerator.OrderData data) {
        long priceUnits = Math.max(1L, Math.round(data.price));
        return new Order(
                orderIdCounter.getAndIncrement(),
                data.side,
                BigDecimal.valueOf(priceUnits),
                BigDecimal.valueOf(data.quantity),
                OrderType.LIMIT,
                new AccountId(data.accountId),
                INSTRUMENT
        );
    }

    private void trackLiveOrder(Order order) {
        liveOrders.addLast(order);
        // Soft cap — drop the oldest entry when the deque grows too large
        if (liveOrders.size() > MAX_LIVE_ORDERS) {
            liveOrders.pollFirst();
        }
    }

    private void recordOrderMetrics(Order order) {
        switch (order.getStatus()) {
            case FILLED           -> metricsCollector.recordOrderFullyExecuted();
            case PARTIALLY_FILLED -> metricsCollector.recordOrderPartiallyExecuted();
            case CANCELED         -> metricsCollector.recordOrderCanceled();
            default               -> {} // OPEN: resting in book, awaiting a matching counter-order
        }
    }

    public static void main(String[] args) {
        try {
            Dotenv dotenv = Dotenv.load();

            int threadCount            = Integer.parseInt(dotenv.get("THREAD_COUNT",             "10"));
            int testDurationSeconds    = Integer.parseInt(dotenv.get("TEST_DURATION_SECONDS",    "30"));
            int ordersPerSecond        = Integer.parseInt(dotenv.get("ORDERS_PER_SECOND",        "1000"));
            int numberOfAccounts       = Integer.parseInt(dotenv.get("NUMBER_OF_ACCOUNTS",       "50"));
            double basePrice           = Double.parseDouble(dotenv.get("BASE_PRICE",             "100.0"));
            double priceSpread         = Double.parseDouble(dotenv.get("PRICE_SPREAD",           "0.5"));
            double priceVolatility     = Double.parseDouble(dotenv.get("PRICE_VOLATILITY",       "2.0"));
            int initialLiquidityDepth  = Integer.parseInt(dotenv.get("INITIAL_LIQUIDITY_DEPTH",  "500"));
            int newOrderPercentage     = Integer.parseInt(dotenv.get("NEW_ORDER_PERCENTAGE",     "70"));
            int cancelPercentage       = Integer.parseInt(dotenv.get("CANCEL_PERCENTAGE",        "20"));
            int queryPercentage        = Integer.parseInt(dotenv.get("QUERY_PERCENTAGE",         "10"));

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

            new LoadTestRunner(profile).run();

        } catch (Exception e) {
            logger.error("Fatal error in load test runner: ", e);
            ReportGenerator.logError("Fatal error", e);
            System.exit(1);
        }
    }
}
