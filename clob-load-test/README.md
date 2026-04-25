# Example Report

================================================================================
CLOB LOAD TEST REPORT
================================================================================
Generated: 2026-04-25 20:10:31

LOAD CONFIGURATION
--------------------------------------------------------------------------------
Thread Count:              20
Test Duration:            60 seconds
Target Throughput:        5000 orders/sec
Number of Accounts:       100

MARKET BEHAVIOR CONFIGURATION
--------------------------------------------------------------------------------
Base Price:               $100.00
Price Spread:             $0.50
Price Volatility:         2.00%
Initial Liquidity Depth:  1000 orders

OPERATION DISTRIBUTION
--------------------------------------------------------------------------------
New Orders:               70%
Cancellations:            20%
Queries:                  10%

TEST RESULTS
--------------------------------------------------------------------------------
Actual Test Duration:     60 seconds

ORDER STATISTICS
--------------------------------------------------------------------------------
Total Orders Submitted:   276,961
Fully Executed:           67,812
Partially Executed:       7,079
Total Executed:           74,891
Canceled:                 15,989
Failed Operations:        0

PERFORMANCE METRICS
--------------------------------------------------------------------------------
Execution Rate:           27.04%
Throughput:               4616.02 orders/sec
Average Latency:          0.2496 ms
P95 Latency:              0.6707 ms
P99 Latency:              0.8742 ms

================================================================================

# CLOB Load Test

Load testing tool for the Central Limit Order Book system.

## Requirements

- Java 21+
- Maven 3.8+

## Steps to run

1. Clone the repository.
2. **Important:** From the root of the repository (where the main `pom.xml` is located), run:

	```
	mvn clean install
	```

	This step installs all modules, including the parent POM, into your local Maven repository. It is required before running the load test scripts for the first time or after cloning the project.

3. Navigate to `clob-load-test/`
4. Copy `env-example.txt` to `.env` and adjust the parameters (optional — defaults are applied automatically)
5. **On Linux/macOS:** Run `./run.sh`
   
	**On Windows:** Run `run.bat`
6. Results are printed to the console and saved to `logs/report.txt`

## Parameters

| Parameter | Description | Default |
|---|---|---|
| `THREAD_COUNT` | Number of concurrent threads | `10` |
| `TEST_DURATION_SECONDS` | How long the test runs | `30` |
| `ORDERS_PER_SECOND` | Target operations per second | `1000` |
| `NUMBER_OF_ACCOUNTS` | Number of simulated trading accounts | `50` |
| `BASE_PRICE` | Mid-market price (BRL) | `100.0` |
| `PRICE_SPREAD` | Bid-ask spread (BRL) | `0.5` |
| `PRICE_VOLATILITY` | Random price deviation (BRL) | `2.0` |
| `INITIAL_LIQUIDITY_DEPTH` | Orders preloaded before the test starts | `500` |
| `NEW_ORDER_PERCENTAGE` | % of operations that place new orders | `70` |
| `CANCEL_PERCENTAGE` | % of operations that cancel orders | `20` |
| `QUERY_PERCENTAGE` | % of operations that query the order book | `10` |

> `NEW_ORDER_PERCENTAGE + CANCEL_PERCENTAGE + QUERY_PERCENTAGE` must equal `100`.
