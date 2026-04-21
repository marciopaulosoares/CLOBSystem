# CLOB Load Test Framework

A comprehensive load testing framework for the Central Limit Order Book (CLOB) system. Simulates real-world high-throughput trading environments with concurrent orders and configurable market conditions.

## Project Structure

```
clob-load-test/
├── pom.xml                           # Maven configuration
├── .env                              # Load test configuration (create from env-example.txt)
├── env-example.txt                   # Configuration template with descriptions
├── src/
│   └── main/
│       ├── java/com/mb/crypto/loadtest/
│       │   ├── LoadTestRunner.java   # Main entry point
│       │   ├── LoadProfile.java      # Configuration object
│       │   ├── OrderGenerator.java   # Order generation logic
│       │   ├── MetricsCollector.java # Metrics collection
│       │   └── ReportGenerator.java  # Report generation
│       └── resources/
│           └── logback.xml           # Logging configuration
└── logs/                             # Output directory
    ├── execution.log                 # Main execution log
    ├── errors.log                    # Error log
    └── report.txt                    # Summary report
```

## Core Components

### LoadProfile
Configurable load profile that defines:
- **Concurrency**: Thread count, test duration, orders/sec, number of accounts
- **Market Behavior**: Base price, spread, volatility, initial liquidity
- **Operations**: Distribution of new orders, cancellations, queries

### OrderGenerator
Generates realistic orders based on configurable market behavior:
- Price model with volatility and spread
- Random account selection
- Operation type distribution
- Buy/sell order generation

### MetricsCollector
Thread-safe metrics collection:
- Order counters (submitted, executed, canceled, failed)
- Latency tracking (average, P95, P99)
- Throughput calculation
- Execution rate calculation

### ReportGenerator
Generates human-readable reports:
- Console output
- File-based reporting (logs/report.txt)
- Error logging (logs/errors.log)

### LoadTestRunner
Main orchestrator that:
- Manages concurrent executor threads
- Preloads order book with liquidity
- Throttles order generation to target throughput
- Collects metrics and generates reports

## Helper Scripts

Two shell scripts are provided to simplify the build and run process:

### build-and-run.sh (Parent Directory)

Located in the parent directory (where both projects are siblings).

```bash
./build-and-run.sh [path/to/mb-crypto]
```

**Features:**
- ✓ Compiles mb-crypto dependency first
- ✓ Compiles clob-load-test
- ✓ Auto-creates .env from env-example.txt if missing
- ✓ Displays all configuration parameters
- ✓ Asks for confirmation before running the load test
- ✓ Runs the load test immediately if confirmed

**Usage:**
```bash
# Default path (./mb-crypto)
./build-and-run.sh

# Custom path
./build-and-run.sh /path/to/mb-crypto
```

### run.sh (clob-load-test Directory)

Located in the clob-load-test directory.

```bash
./run.sh
```

**Features:**
- ✓ Displays current .env configuration
- ✓ Option to edit parameters before running
- ✓ Asks for confirmation before execution
- ✓ Shows where detailed results are logged

**Usage:**
```bash
cd clob-load-test
./run.sh
```

## Requirements

- Java 21 or higher
- Maven 3.8+
- The main CLOB system JAR in local Maven repository

## Building

### Automated Build (Recommended)

From the parent directory (both `mb-crypto` and `clob-load-test` as siblings):

```bash
./build-and-run.sh
```

Or with custom mb-crypto path:
```bash
./build-and-run.sh /path/to/mb-crypto
```

### Manual Build

Build main CLOB system first:
```bash
cd ./mb-crypto
mvn clean install
```

Build load test project:
```bash
cd ./clob-load-test
mvn clean package
```

## Running

### Quick Start (Automated Setup)

From the parent directory (where both `mb-crypto` and `clob-load-test` folders exist):

```bash
# Option 1: With default mb-crypto path (./mb-crypto)
./build-and-run.sh

# Option 2: With custom mb-crypto path
./build-and-run.sh /path/to/mb-crypto
```

The script will:
1. ✓ Build mb-crypto
2. ✓ Build clob-load-test
3. ✓ Display your configuration
4. ✓ Ask if you want to run the load test immediately

### Manual Setup

Before running the load test, configure your parameters:

```bash
# Create .env file from the example template
cp env-example.txt .env

# Edit .env with your desired parameters
vi .env
```

### Run the Load Test

**Option 1: Using the helper script (from clob-load-test directory)**
```bash
./run.sh
```

The script will:
- Display current configuration
- Offer to edit parameters before running
- Ask for confirmation before starting the test
- Show where to find detailed results

**Option 2: Using Maven exec plugin**
```bash
mvn exec:java -Dexec.mainClass="com.mb.crypto.loadtest.LoadTestRunner"
```

**Option 3: Run as JAR**
```bash
java -jar target/clob-load-test-1.0-jar-with-dependencies.jar
```

**Option 4: From parent project (if using multi-module)**
```bash
cd /path/to/mb-crypto
mvn -pl clob-load-test exec:java
```

## Configuration

Load test parameters are configured via a `.env` file in the project root directory. 

### Configuration File

Copy the example configuration to get started:
```bash
cp env-example.txt .env
```

Then edit `.env` with your desired parameters:

```properties
# Thread Configuration
THREAD_COUNT=20
TEST_DURATION_SECONDS=60

# Order Generation
ORDERS_PER_SECOND=5000
NUMBER_OF_ACCOUNTS=100

# Market Behavior
BASE_PRICE=100.0
PRICE_SPREAD=0.5
PRICE_VOLATILITY=2.0

# Order Book
INITIAL_LIQUIDITY_DEPTH=1000

# Operation Distribution
NEW_ORDER_PERCENTAGE=70
CANCEL_PERCENTAGE=20
QUERY_PERCENTAGE=10
```

### Configuration Parameters

- **THREAD_COUNT**: Number of concurrent threads (default: 10)
- **TEST_DURATION_SECONDS**: How long to run the test in seconds (default: 30)
- **ORDERS_PER_SECOND**: Target orders per second (default: 1000)
- **NUMBER_OF_ACCOUNTS**: Number of trading accounts to simulate (default: 50)
- **BASE_PRICE**: Base order price in dollars (default: 100.0)
- **PRICE_SPREAD**: Bid-ask spread in dollars (default: 0.5)
- **PRICE_VOLATILITY**: Price volatility as a percentage (default: 2.0)
- **INITIAL_LIQUIDITY_DEPTH**: Initial orders to preload (default: 500)
- **NEW_ORDER_PERCENTAGE**: % of new orders operation (default: 70)
- **CANCEL_PERCENTAGE**: % of cancellation operations (default: 20)
- **QUERY_PERCENTAGE**: % of query operations (default: 10)

**Note**: NEW_ORDER_PERCENTAGE + CANCEL_PERCENTAGE + QUERY_PERCENTAGE must equal 100.

### Configuration Example

For detailed parameter descriptions and preset configurations, refer to `env-example.txt`

**Preset Configurations**:

- **Light Load Test**: `THREAD_COUNT=5` `ORDERS_PER_SECOND=500` (good for development)
- **Medium Load Test**: `THREAD_COUNT=20` `ORDERS_PER_SECOND=5000` (standard testing)
- **Heavy Load Test**: `THREAD_COUNT=50` `ORDERS_PER_SECOND=10000` (stress testing)
- **Production-like**: `THREAD_COUNT=100` `ORDERS_PER_SECOND=50000` (maximum load)

## Output

### Console Output
```
================================================================================
CLOB LOAD TEST REPORT
================================================================================
Generated: 2026-04-21 14:30:45

LOAD CONFIGURATION
...
TEST RESULTS
  Actual Test Duration:     30 seconds

ORDER STATISTICS
  Total Orders Submitted:   150,000
  Fully Executed:           90,000
  Partially Executed:       37,500
  Total Executed:           127,500
  Canceled:                 22,500
  Failed Operations:        0

PERFORMANCE METRICS
  Execution Rate:           85.00%
  Throughput:               5,000.00 orders/sec
  Average Latency:          0.5234 ms
  P95 Latency:              1.2345 ms
  P99 Latency:              2.1234 ms
================================================================================
```

### Files Generated
- `logs/execution.log` - Detailed execution logs
- `logs/errors.log` - Error messages and stack traces
- `logs/report.txt` - Human-readable test summary

## Performance Notes

- Uses `System.nanoTime()` for precise latency measurement
- Uses `LongAdder` for lock-free counter updates
- Uses `AtomicLong` for circular buffer storage
- Minimal object creation in hot paths
- Circular buffer with fixed size to avoid memory spikes

## Features

✓ Configurable load profiles
✓ High-performance metrics collection
✓ Realistic order generation with market model
✓ Concurrent execution with thread pooling
✓ Latency percentile tracking (p95, p99)
✓ Comprehensive error handling and logging
✓ Professional reporting with file output
✓ Scalable to thousands of orders/sec

## Integration with ClobSystem

The load test currently uses simulated execution. To integrate with the real ClobSystem:

1. Update `executeOrder()` method in `LoadTestRunner.java`
2. Call actual ClobSystem methods:
   - `clobSystem.placeOrder(accountId, side, price, quantity)`
   - `clobSystem.cancelOrder(orderId)`
   - `clobSystem.getOrderStatus(orderId)`
3. Track execution outcomes (fully filled, partial, cancelled)
4. Record metrics accordingly