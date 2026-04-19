# Prompt for AI: Generate Java CLOB Boilerplate from UML Diagram

## Context

I have a UML class diagram (attached as PDF/image) for a Central Limit Order Book (CLOB) implementation. I need you to generate complete Java boilerplate code that follows the diagram structure while adhering to specific performance and concurrency requirements.

---

## Your Task

Generate production-quality Java code for all classes in the diagram, following these strict technical constraints:

### A. Memory Management & Data Structures

**REQUIRED:**
1. Use `ArrayDeque<Order>` instead of `LinkedList` for order queues (better cache locality)
2. Use `BigDecimal` for all financial values (price, quantity, balances) - NEVER use `double` or `float`
3. Use `NavigableMap<BigDecimal, ArrayDeque<Order>>` for OrderBook bid/ask structure
4. Use `TreeMap` with explicit comparators:
   - Bids: `new TreeMap<>(Comparator.reverseOrder())` // highest price first
   - Asks: `new TreeMap<>(Comparator.naturalOrder())` // lowest price first
5. Mark all financial domain objects as `final` where possible
6. Use Java records for immutable value objects where appropriate
7. Use minimal DDD Structure - no anemic models, but also no over-engineering with rich domain logic in entities

**OPTIONAL (but mention as TODO comments):**
- Consider primitive collections (Eclipse Collections) for `orderId` maps
- Consider `VarHandle` for lock-free updates to Order.status and Order.quantity

### B. Concurrency & Idempotency

**REQUIRED:**
1. Use `StampedLock` (NOT synchronized or ReentrantReadWriteLock) in `InstrumentLockRegistry`
2. Implement optimistic read pattern for `getOrderBook()` queries:
   ```java
   long stamp = lock.tryOptimisticRead();
   // read operation
   if (!lock.validate(stamp)) {
       stamp = lock.readLock();
       try {
           // re-read
       } finally {
           lock.unlockRead(stamp);
       }
   }
   ```
3. Use write locks for `placeOrder()` and `cancelOrder()`
4. Make `Account` balance methods package-private (NOT public)
5. Mark `Order.status` and `Order.quantity` as `volatile`
6. All operations must be deterministic - same input = same output

**THREAD SAFETY PATTERN:**
```java
// In OrderBookEngine
public void placeOrder(Order order) {
    StampedLock lock = lockRegistry.getLock(order.instrument());
    long stamp = lock.writeLock();
    try {
        // place order logic
        // match logic
        // execute trade if match found
    } finally {
        lock.unlockWrite(stamp);
    }
}
```

### C. Framework Restrictions

**PROHIBITED:**
- ❌ NO Spring Framework
- ❌ NO Micronaut
- ❌ NO dependency injection containers
- ❌ NO external libraries except JDK 17+

**ALLOWED:**
- ✅ Pure JDK 17+ only
- ✅ `java.util` collections
- ✅ `java.util.concurrent.locks.StampedLock`
- ✅ `java.math.BigDecimal`
- ✅ `java.time.Instant`

---

## Code Generation Requirements

### 1. Package Structure Example

```
com.clob/
├── domain/
│   ├── Account.java
│   ├── AccountId.java
│   ├── Asset.java (enum)
│   ├── Balance.java
│   ├── Instrument.java
│   ├── Order.java
│   ├── OrderSide.java (enum)
│   ├── OrderStatus.java (enum)
│   ├── OrderType.java (enum)
│   └── Trade.java
├── orderbook/
│   ├── OrderBook.java
│   └── InstrumentLockRegistry.java
├── matching/
│   ├── MatchingEngine.java (interface)
│   ├── OrderMatcher.java (interface)
│   ├── OrderBookEngine.java (implements MatchingEngine)
│   └── LimitOrderStrategy.java (implements OrderMatcher)
└── CLOBSystem.java
```

### 2. Class Implementation Guidelines

**For each class, include:**

✅ **Complete field declarations** with correct types from diagram
✅ **Constructor(s)** - use constructor injection where dependencies exist
✅ **Method signatures** matching the diagram (respect visibility: `+` public, `-` private, `~` package-private)
✅ **Javadoc comments** explaining purpose and constraints
✅ **Null checks** using `Objects.requireNonNull()` for all parameters
✅ **Empty method bodies** with `// TODO: implement` comments
✅ **Defensive copies** where needed (e.g., returning collections)
✅ **Add comments only class level not method level

**Example expected quality:**

```java
package com.clob.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a user account with balances across multiple assets.
 * 
 * Thread-safety: Balance mutation methods (lock/unlock/debit/credit) are 
 * package-private and should only be called within a StampedLock write section
 * by OrderBookEngine.
 */
public final class Account {
    private final AccountId id;
    private final Map<Asset, Balance> balancesByAsset;
    private final List<Trade> tradeHistory;
    
    public Account(AccountId id) {
        this.id = Objects.requireNonNull(id, "AccountId cannot be null");
        this.balancesByAsset = new ConcurrentHashMap<>();
        this.tradeHistory = new ArrayList<>();
    }
    
    /**
     * Locks (reserves) a specific amount of an asset for an order.
     * Package-private to prevent external balance manipulation.
     * 
     * @param asset the asset to lock
     * @param amount the amount to lock (must be positive)
     * @throws IllegalArgumentException if insufficient available balance
     */
    void lock(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        // TODO: implement - check available balance, move to locked
    }
    
    // ... other methods
    
    public BigDecimal getBalance(Asset asset) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        // TODO: implement - return total balance (available + locked)
        return BigDecimal.ZERO;
    }
    
    public List<Trade> getTradeHistory() {
        // Return defensive copy to prevent external modification
        return new ArrayList<>(tradeHistory);
    }
}
```

### 3. Specific Implementation Notes

**OrderBook:**
```java
public class OrderBook {
    private final Instrument instrument;
    // Bids: highest price first (reverse order)
    private final NavigableMap<BigDecimal, ArrayDeque<Order>> bids = 
        new TreeMap<>(Comparator.reverseOrder());
    // Asks: lowest price first (natural order)
    private final NavigableMap<BigDecimal, ArrayDeque<Order>> asks = 
        new TreeMap<>();
    
    // Constructor, methods...
}
```

**InstrumentLockRegistry:**
```java
public class InstrumentLockRegistry {
    private final Map<Instrument, StampedLock> locks = new ConcurrentHashMap<>();
    
    public StampedLock getLock(Instrument instrument) {
        return locks.computeIfAbsent(instrument, k -> new StampedLock());
    }
}
```

**CLOBSystem Constructor:**
```java
public CLOBSystem(List<Instrument> instruments, List<Account> accounts) {
    Objects.requireNonNull(instruments, "Instruments cannot be null");
    Objects.requireNonNull(accounts, "Accounts cannot be null");
    
    this.accounts = accounts.stream()
        .collect(Collectors.toMap(Account::getId, Function.identity()));
    
    this.matchingEngine = new OrderBookEngine(instruments, this.accounts);
}
```

**Strategy Pattern Setup:**
```java
// In OrderBookEngine constructor
this.matchers = Map.of(
    OrderType.LIMIT, new LimitOrderStrategy()
    // MARKET and STOP strategies would be added here
);
```

### 4. What NOT to implement (leave as TODO)

- ❌ Don't implement the actual matching logic in `LimitOrderStrategy.match()` - just stub it
- ❌ Don't implement balance arithmetic in `Account` - just validation stubs
- ❌ Don't implement trade execution in `OrderBookEngine.executeTrade()` - just outline
- ✅ DO implement all the structure, types, locking patterns, and method signatures

---

## Validation Checklist

Before submitting the generated code, verify:

- [ ] All classes from the diagram are present
- [ ] All field types match the diagram (especially `BigDecimal`, `ArrayDeque`, `NavigableMap`)
- [ ] Visibility modifiers match (`+` = public, `-` = private, `~` = package-private)
- [ ] `StampedLock` is used (not synchronized)
- [ ] `Account` mutation methods are package-private
- [ ] Bids use `reverseOrder()`, asks use `naturalOrder()`
- [ ] All financial values are `BigDecimal` (no double/float)
- [ ] Constructor injection is used for dependencies
- [ ] No Spring/framework annotations
- [ ] All parameters have null checks
- [ ] Javadoc exists for all public classes and methods

---

## Expected Output Format

For each Java file, provide:

1. **Package declaration**
2. **Imports** (organized, no wildcards)
3. **Class Javadoc** with purpose and thread-safety notes
4. **Complete class structure** with all fields, constructors, methods
5. **TODO comments** for unimplemented logic

**Total expected files: ~14 Java files**

Generate the complete boilerplate now. Focus on correctness of structure, types, and concurrency patterns - not on business logic implementation.

---

## Attachment
docs/object-design.v.0.0.1.drawio.pdf

---

## Example Response Format I Expect

```java
// File: com/clob/domain/Order.java
package com.clob.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a limit order in the order book.
 * 
 * Thread-safety: status and quantity are volatile to allow 
 * lock-free reads. Updates should use VarHandle or occur within
 * a StampedLock write section.
 */
public final class Order {
    private final long orderId;
    private final OrderSide side;
    private final BigDecimal price;
    private volatile BigDecimal quantity; // mutable for partial fills
    private final Instant createdAt;
    private final Instant updatedAt;
    private volatile OrderStatus status;
    private final OrderType type;
    private final AccountId accountId;
    private final Instrument instrument;
    
    public Order(long orderId, OrderSide side, BigDecimal price, 
                 BigDecimal quantity, OrderType type, 
                 AccountId accountId, Instrument instrument) {
        this.orderId = orderId;
        this.side = Objects.requireNonNull(side);
        this.price = Objects.requireNonNull(price);
        this.quantity = Objects.requireNonNull(quantity);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = OrderStatus.OPEN;
        this.type = Objects.requireNonNull(type);
        this.accountId = Objects.requireNonNull(accountId);
        this.instrument = Objects.requireNonNull(instrument);
        
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
    
    // Getters
    public long getOrderId() { return orderId; }
    public OrderSide getSide() { return side; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public OrderType getType() { return type; }
    public AccountId getAccountId() { return accountId; }
    public Instrument getInstrument() { return instrument; }
    
    /**
     * Updates the remaining quantity for partial fills.
     * Should be called within a StampedLock write section.
     */
    void updateQuantity(BigDecimal newQuantity) {
        // TODO: consider using VarHandle.setVolatile for lock-free update
        this.quantity = newQuantity;
    }
    
    /**
     * Updates the order status (OPEN -> CANCELED, etc).
     * Should be called within a StampedLock write section.
     */
    void updateStatus(OrderStatus newStatus) {
        // TODO: consider using VarHandle.setVolatile for lock-free update
        this.status = newStatus;
    }
}
```

Continue with all other classes...
