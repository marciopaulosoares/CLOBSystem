# CLOB Load Test

Load testing tool for the Central Limit Order Book system.

## Requirements

- Java 21+
- Maven 3.8+

## Steps to run

1. Clone the repository and navigate to `clob-load-test/`
2. Copy `env-example.txt` to `.env` and adjust the parameters (optional — defaults are applied automatically)
3. Run `./run.sh`
4. Results are printed to the console and saved to `logs/report.txt`

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
