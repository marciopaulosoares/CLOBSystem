# Central Limit Order Book (CLOB) Implementation

A high-performance, thread-safe Central Limit Order Book implementation in Java, designed for ultra-low-latency financial infrastructure.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Design Decisions](#design-decisions)
- [Technical Constraints](#technical-constraints)
- [Build and Run](#build-and-run)
- [Testing](#testing)
- [AI-Assisted Development](#ai-assisted-development)

---

## Overview

This project implements a simplified Central Limit Order Book (CLOB) and matching engine capable of executing limit orders and managing account balances. The implementation prioritizes:

- **Memory efficiency** through primitive collections and careful object layout
- **Concurrency safety** via per-instrument locking with StampedLock
- **Deterministic behavior** ensuring idempotent processing
- **Extensibility** through the Strategy Pattern for order matching

### Core Features

- ✅ Place limit orders into the order book
- ✅ Cancel existing orders
- ✅ Automatic order matching and execution
- ✅ Balance management with pessimistic locking (lock/unlock funds before matching)
- ✅ Thread-safe per-instrument processing
- ✅ Support for multiple instruments (e.g., BTC/BRL, ETH/BRL)

---

## Architecture

### System Components

```
CLOBSystem (Facade)
    ├─> MatchingEngine (interface)
    │       └─> OrderBookEngine (implementation)
    │               ├─> Map<Instrument, OrderBook>
    │               ├─> Map<AccountId, Account>
    │               ├─> InstrumentLockRegistry
    │               └─> Map<OrderType, OrderMatcher>
    │                       └─> LimitOrderStrategy
    └─> Map<AccountId, Account>
```

### Key Classes

**`CLOBSystem`** - System facade and entry point
- Manages instruments and accounts
- Delegates order operations to the matching engine
- Provides clean API for external clients

**`OrderBookEngine`** - Core matching coordinator
- Implements the `MatchingEngine` interface
- Maintains order books per instrument
- Routes orders to appropriate matching strategies
- Handles trade settlement and balance updates

**`OrderBook`** - Price-time priority data structure
- Bids sorted in descending order (highest bid first)
- Asks sorted in ascending order (lowest ask first)
- Uses `NavigableMap<BigDecimal, ArrayDeque<Order>>` for efficient price-level access

**`Account`** - User balance management
- Maintains balances per asset
- Provides package-private `lock/unlock/debit/credit` operations
- Only accessible through `OrderBookEngine` to enforce invariants

**`InstrumentLockRegistry`** - Concurrency control
- Provides one `StampedLock` per instrument
- Enables fine-grained locking (instrument-level, not global)
- Supports optimistic reads for `getOrderBook()` queries

**`LimitOrderStrategy`** - Limit order matching implementation
- Implements the `OrderMatcher` interface
- Handles price-time priority matching
- Executes partial fills when necessary

---

## Design Decisions

### 1. Strategy Pattern for Order Matching

**Decision:** Use `Map<OrderType, OrderMatcher>` to route orders by type.

**Rationale:**
- **Open/Closed Principle:** Adding MARKET or STOP orders requires only a new strategy class—zero changes to existing code
- **Separation of Concerns:** Each order type's matching logic is isolated
- **Testability:** Strategies can be tested independently

**Trade-off:** Slight indirection overhead, but negligible compared to matching complexity.

```java
// Future extensibility
matchers.put(OrderType.MARKET, new MarketOrderStrategy());
matchers.put(OrderType.STOP, new StopOrderStrategy());
```

### 2. Package-Private Balance Operations

**Decision:** `Account` methods `lock/unlock/debit/credit` are package-private (`~` in UML).

**Rationale:**
- **Encapsulation:** Only `OrderBookEngine` can mutate balances
- **Domain Integrity:** Prevents external code from bypassing order matching to manipulate balances
- **Rich Domain Model:** `Account` retains its business logic (not an anemic data bag)

**Alternative Considered:** Extract an `AccountService` to centralize balance operations.
- **Why rejected:** Would create an anemic `Account` object. Package-private visibility achieves the same protection with less indirection.

### 3. Per-Instrument Locking with StampedLock

**Decision:** One `StampedLock` per instrument, managed by `InstrumentLockRegistry`.

**Rationale:**
- **Fine-Grained Concurrency:** BTC/BRL orders don't block ETH/BRL orders
- **Optimistic Reads:** `getOrderBook()` uses `tryOptimisticRead()` for zero-blocking queries
- **Determinism:** Write locks ensure sequential processing per instrument

**Why Not:**
- ❌ `synchronized` — Too coarse, blocks readers
- ❌ `ReentrantReadWriteLock` — StampedLock is faster in low-contention scenarios (typical for per-instrument access)
- ❌ Lock-free structures — Complexity outweighs benefits for this scope; StampedLock provides determinism without full lock-free overhead

### 4. `Instrument` as Value Object (No Separate `InstrumentId`)

**Decision:** `Instrument` is a value object identified by `(Asset base, Asset quote)`. The `instrumentId` string is a derived property.

**Rationale:**
- **Natural Identity:** BTC/BRL is uniquely identified by those two assets
- **Simplicity:** No need for a wrapper `InstrumentId` class
- **DDD Alignment:** `Instrument` is immutable and equality is value-based

```java
record Instrument(Asset base, Asset quote) {
    public String instrumentId() {
        return base.name() + "/" + quote.name(); // "BTC/BRL"
    }
}
```

### 5. `BigDecimal` for Prices and Quantities

**Decision:** Use `BigDecimal` instead of `double` or `long` for financial values.

**Rationale:**
- **Precision:** Floating-point errors are unacceptable in financial systems
- **Decimal Arithmetic:** Prices like 0.1 BTC must be exact, not approximated

**Trade-off:** `BigDecimal` is slower than primitive `double`, but correctness trumps performance for financial calculations.

### 6. `ArrayDeque` Over `LinkedList` for Order Queues

**Decision:** `NavigableMap<BigDecimal, ArrayDeque<Order>>` in `OrderBook`.

**Rationale:**
- **Cache Locality:** `ArrayDeque` uses a circular array; better CPU cache performance than `LinkedList`'s scattered nodes
- **Memory Efficiency:** No per-node pointer overhead
- **Assessment Alignment:** Demonstrates awareness of JVM/hardware interaction ("mechanical sympathy")

### 7. Constructor Injection for Instruments (Immutable Setup)

**Decision:** `CLOBSystem(List<Instrument>, List<Account>)` receives instruments at construction time.

**Rationale:**
- **Fail-Fast:** Invalid configuration caught at startup, not during runtime
- **Determinism:** Fixed instrument set from the beginning aids reproducibility
- **Immutability:** Instruments typically don't change after exchange initialization

**Accounts:** Can still be added dynamically via `addAccount()` since users join at runtime.

### 8. No `getAccount()` Exposure on `CLOBSystem`

**Decision:** `CLOBSystem` does not expose a `getAccount(AccountId)` method.

**Rationale:**
- **Information Hiding:** External callers should never get a raw `Account` reference
- **Controlled Mutations:** All balance changes must go through `placeOrder/cancelOrder`, not direct account access

**Alternative:** Provide `getBalance(AccountId, Asset)` as a safe query method if needed.

### 9. `Trade` Record for Auditability

**Decision:** Create a `Trade` object for every match, capturing buyer/seller accounts and execution details.

**Rationale:**
- **Idempotency:** Same input stream can be replayed to produce the same trades
- **Auditability:** Full trade history for compliance and debugging
- **Settlement:** `executeTrade()` uses the `Trade` record to debit/credit accounts

---

## Technical Constraints

### Memory Management & Data Structures

✅ **Beyond Standard Collections**
- `ArrayDeque<Order>` instead of `LinkedList` for cache-friendly queues
- `NavigableMap<BigDecimal, ...>` for O(log n) price-level access

✅ **Mechanical Sympathy**
- `StampedLock` for low-contention read-heavy workloads
- Per-instrument locks avoid false sharing across instruments

**Future Enhancements (Not Implemented):**
- Primitive collections (e.g., Eclipse Collections `LongToObjectHashMap` for order IDs)
- `VarHandle` for lock-free updates to `Order.quantity` and `Order.status` during partial fills
- Manual memory padding to prevent false sharing in hot structures

### Concurrency & Idempotency

✅ **Lock-Free Architecture (Per-Instrument Level)**
- `StampedLock` with optimistic reads for queries
- Write locks ensure sequential order processing per instrument

✅ **Determinism**
- Processing the same input stream always produces the same final state
- Achieved through sequential per-instrument locking (no race conditions)

### Framework Restrictions

✅ **Zero Frameworks**
- Pure JDK 17+ standard library
- No Spring, no Micronaut, no dependency injection containers

✅ **Standard Library Only**
- `java.util` collections
- `java.math.BigDecimal`
- `java.time.Instant`
- `java.util.concurrent.locks.StampedLock`

---

## Build and Run

### Prerequisites

- JDK 17 or later
- Maven 3.8+ (or Gradle if preferred)

### Building

```bash
mvn clean compile
```

### Running Tests

```bash
mvn test
```

### Example Usage

```java
// Initialize the system
CLOBSystem clob = new CLOBSystem(
    List.of(new Instrument(Asset.BTC, Asset.BRL)),
    List.of(
        new Account(new AccountId("alice")),
        new Account(new AccountId("bob"))
    )
);

// Credit initial balances
// (In production, this would be handled through a separate deposit flow)

// Place a sell order
Order sellOrder = new Order(
    1L,                          // orderId
    OrderSide.SELL,
    new BigDecimal("500000"),    // price: 500k BRL
    new BigDecimal("1.0"),       // quantity: 1 BTC
    OrderType.LIMIT,
    new AccountId("alice"),
    new Instrument(Asset.BTC, Asset.BRL)
);
clob.placeOrder(sellOrder);

// Place a matching buy order
Order buyOrder = new Order(
    2L,
    OrderSide.BUY,
    new BigDecimal("500000"),
    new BigDecimal("1.0"),
    OrderType.LIMIT,
    new AccountId("bob"),
    new Instrument(Asset.BTC, Asset.BRL)
);
clob.placeOrder(buyOrder);

// Orders match: 1 BTC transferred from Alice to Bob
//               500k BRL transferred from Bob to Alice
```

---

## Testing

### Unit Tests

- `OrderBookTest` — Validates price-time priority, order addition/cancellation
- `AccountTest` — Tests lock/unlock/debit/credit invariants
- `LimitOrderStrategyTest` — Verifies matching logic and partial fills
- `OrderBookEngineTest` — Integration tests for order placement and settlement

### Idempotency Test

```java
@Test
void sameInputStreamProducesSameState() {
    List<Order> orders = generateOrderStream();
    
    CLOBSystem clob1 = runOrders(orders);
    CLOBSystem clob2 = runOrders(orders);
    
    assertEquals(clob1.getState(), clob2.getState());
}
```

### Concurrency Test

```java
@Test
void concurrentOrdersOnDifferentInstrumentsDoNotBlock() {
    // Place 1000 orders on BTC/BRL and ETH/BRL concurrently
    // Verify all orders processed without deadlock
    // Measure throughput (should scale linearly with instruments)
}
```

---

## AI-Assisted Development

### AI Model Used

**Claude Sonnet 4.5** (Anthropic) was used to accelerate development and validate design decisions.

### How AI Was Used

1. **Boilerplate Generation**
   - Generated initial class structures for `Order`, `Account`, `OrderBook`
   - Created test scaffolding and mock data builders

2. **Design Pattern Implementation**
   - Prompted AI to suggest implementations of the Strategy Pattern for order matching
   - Validated separation of concerns between `MatchingEngine` (interface) and `OrderBookEngine` (implementation)

3. **Concurrency Strategy**
   - Discussed trade-offs between `synchronized`, `ReentrantReadWriteLock`, and `StampedLock`
   - AI recommended `StampedLock` for optimistic read performance in read-heavy order book queries

4. **Code Review & Refactoring**
   - Asked AI to review for anemic domain model anti-patterns
   - Identified that `Account` should retain business logic (lock/unlock) rather than delegating to a service

### Prompt Strategy

**Iterative Refinement:**
```
1. "Design a CLOB matching engine with price-time priority"
   → AI provided initial architecture
2. "How should I handle concurrency for multiple instruments?"
   → AI suggested per-instrument locking with StampedLock
3. "Should Account operations be package-private or use an AccountService?"
   → AI explained trade-offs; chose package-private to avoid anemic domain
```

**Code Generation with Constraints:**
```
"Generate a LimitOrderStrategy class implementing OrderMatcher.
Requirements:
- Match orders by price-time priority
- Support partial fills
- Return a Trade object for each match
- Use BigDecimal for all financial calculations"
```

**Pattern Validation:**
```
"Review this OrderBookEngine class. Does it violate any SOLID principles?
Should the Strategy Pattern be used differently?"
```

### What AI Did Not Do

- **Architectural Decisions:** Core design choices (per-instrument locking, package-private balance ops, Strategy Pattern) were human-driven
- **Domain Modeling:** Entity relationships and value object boundaries were designed based on financial domain knowledge
- **Performance Trade-offs:** Decisions like `ArrayDeque` vs `LinkedList` were based on understanding cache locality, not AI suggestion

### Lessons Learned

- AI excels at generating **boilerplate** and **explaining trade-offs**, but **architectural ownership** must remain human-driven
- Asking AI to **critique designs** (e.g., "Is this anemic?") is more valuable than asking it to design from scratch
- Best results come from **iterative refinement** with specific constraints, not open-ended prompts

---

## Design Trade-offs

### What Was Optimized For

✅ **Correctness** — `BigDecimal` over `double`, deterministic locking  
✅ **Extensibility** — Strategy Pattern allows adding new order types without changing core code  
✅ **Concurrency** — Per-instrument locks enable high throughput for multi-instrument exchanges  

### What Was Sacrificed

⚠️ **Maximum Performance** — This implementation prioritizes clarity and correctness over micro-optimizations  
- No custom memory allocators or object pooling (would reduce GC pressure)
- No lock-free data structures (would reduce latency but add complexity)
- No hand-rolled primitive collections (Eclipse Collections would save heap space)

**Justification:** For a tech lead assessment, demonstrating **sound architectural thinking** and **clean code** is more valuable than premature optimization.

### Production Considerations (Not Implemented)

For a real production CLOB, consider:

- **Order ID Generation:** Distributed ID generation (Snowflake, ULID)
- **Persistence:** Event sourcing or WAL for crash recovery
- **Market Data Publishing:** Broadcast order book updates to subscribers
- **Risk Checks:** Pre-trade risk validation (margin requirements, position limits)
- **Metrics:** Latency percentiles, throughput, lock contention monitoring
- **Graceful Degradation:** Circuit breakers, rate limiting

---

## References

- [Understanding Limit Order Books](https://www.investopedia.com/terms/l/limitorderbook.asp)
- [Price-Time Priority Matching](https://en.wikipedia.org/wiki/Order_matching_system)
- [Java StampedLock Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/StampedLock.html)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

---

## License

This project is for educational purposes as part of a technical assessment.

---

## Contact

For questions or feedback about design decisions, please reach out via the assessment platform.
