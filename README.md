# CLOB System

## Overview

This project implements a high-performance **Central Limit Order Book (CLOB)** in Java, focused on low latency, deterministic execution, and high throughput.
It includes a separate module for load testing to simulate real-world trading conditions.

---

## Core Components

![Alt text](./clob-system/docs/object-design.v.0.0.1.png)

* **OrderBook**
  Maintains bid and ask sides using price-time priority.

* **MatchingEngine**
  Processes orders using a Strategy pattern to support different order types.

* **Account**
  Manages balances with locking, debiting, and settlement during trade execution.

* **Concurrency Model**
  Uses `StampedLock` to ensure thread safety with minimal contention.

---

## Build and Run

From the project root:

```bash
mvn clean install
```

Run the core system or load test module using the generated artifacts.

---

## Running the Main Scenarios

`Main.java` in the `clob-system` module demonstrates five basic trading scenarios:
full fill, partial fill, no match, cancel, and multi-level sweep.

Run from the project root:

```bash
mvn -pl clob-system exec:java -Dexec.mainClass=com.mb.crypto.clob.Main
```

Or from inside the `clob-system` directory:

```bash
mvn exec:java -Dexec.mainClass=com.mb.crypto.clob.Main
```

Expected output (one block per scenario):

```
=== Case 1: Full Fill ===
  alice ask            → FILLED
  bob bid              → FILLED
  order book:
    (empty)
alice BRL: 500.00000000 | bob BTC: 1.00000000

=== Case 2: Partial Fill ===
  alice ask (3 BTC)    → PARTIALLY_FILLED
  bob bid  (1 BTC)     → FILLED
ask remaining qty (satoshis): 200000000
  order book:
    ASK   500 BRL  qty=1 orders

=== Case 3: Resting Orders, No Match ===
  order book:
    ASK   500 BRL  qty=1 orders
    BID   400 BRL  qty=1 orders

=== Case 4: Cancel Order ===
before cancel — book asks: 1 level(s)
  alice ask            → CANCELED
after cancel  — book asks: 0 level(s)

=== Case 5: Aggressor Sweeps Multiple Levels ===
  alice ask @490       → FILLED
  carol ask @500       → FILLED
  bob   bid @500       → FILLED
  order book:
    (empty)
bob BTC: 2.00000000
```

| Scenario | What it demonstrates |
|---|---|
| Full Fill | Matching buy and sell at the same price — both orders filled, book empty |
| Partial Fill | Aggressor smaller than resting order — resting stays in book as `PARTIALLY_FILLED` |
| No Match | Bid below ask — both orders rest in book, no trade |
| Cancel | Resting order removed before any match — balance unlocked |
| Multi-level Sweep | One aggressor consumes two price levels in price-time order |

---

## Load Testing

The [`clob-load-test`](clob-load-test/README.md) module stress-tests the engine under sustained concurrent traffic, simulating realistic market behavior: order placement, cancellations, and book queries across multiple accounts and price levels.

It measures throughput (orders/sec), average and tail latencies (P95, P99), execution rate, and failed operations — all printed to the console and saved to `logs/report.txt`.

**Quick start:**

```bash
cd clob-load-test
cp env-example.txt .env   # optional — defaults are applied automatically
./run.sh
```

Key parameters (via `.env`):

| Parameter | Default | Description |
|---|---|---|
| `THREAD_COUNT` | `10` | Concurrent threads |
| `TEST_DURATION_SECONDS` | `30` | How long the test runs |
| `ORDERS_PER_SECOND` | `1000` | Target throughput |
| `NUMBER_OF_ACCOUNTS` | `50` | Simulated trading accounts |
| `INITIAL_LIQUIDITY_DEPTH` | `500` | Orders pre-loaded before the test |

See the [full parameter reference](clob-load-test/README.md#parameters) for all options.

---

## AI Usage

This project was developed using AI-assisted techniques following the **4D AI Fluency Framework**.

* AI was used for:

    * boilerplate code generation
    * structural design alignment with UML
    * enforcing technical constraints

* The final architecture and decisions remain manually validated to ensure correctness and performance.
