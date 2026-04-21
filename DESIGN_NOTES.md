# Design Notes

## Why `Balance` keeps `BigDecimal` while the matching engine uses `long`

The order book hot path (`OrderBookEngine`, `Order`) stores prices as plain `long` BRL
and quantities as `long` satoshis (`Scales.QUANTITY_SCALE = 100_000_000`).
`Balance` and `Account` deliberately stay on `BigDecimal`. Here is why.

### Different jobs, different constraints

| Layer | Primary concern | Representation |
|---|---|---|
| Matching engine | Throughput — millions of compare/match ops per second | `long` fixed-point |
| Balance / settlement | Correctness — no rounding, exact audit trail, regulatory reporting | `BigDecimal` |

The matching engine compares and subtracts integers millions of times per second.
`long` arithmetic is branch-free and allocation-free; `BigDecimal` would add heap
pressure and GC pauses on that critical path.

`Balance` runs only on settlement (once per trade, not once per comparison).
The extra allocation cost is negligible, but the correctness properties are essential.

### `BigDecimal` gives exact decimal semantics at the boundary

Users deposit and withdraw in human-readable decimal amounts (`"0.5"`, `"1000.00"`).
Representing those as `long` satoshis requires a conversion that can silently truncate
if the input has more decimal places than `QUANTITY_DECIMALS` allows.

`BigDecimal` rejects any loss of precision at the point of entry
(`movePointRight(...).longValueExact()` throws `ArithmeticException` on truncation),
so the invariant "what the user deposited is exactly what the ledger records" is enforced
by the type system rather than by discipline.

### Settlement math crosses asset types

A single trade debits BTC from the seller and credits BRL to the seller (and vice versa
for the buyer). The exchange rate between assets is not a fixed-point integer — it is
an arbitrary decimal ratio. Doing that arithmetic in `long` fixed-point requires choosing
a common scale in advance and risks overflow or precision loss when the ratio is
irrational relative to that scale.

`BigDecimal` arithmetic is exact for any finite decimal ratio, which makes settlement
ledger entries verifiable to the last unit without needing a manual scale contract
between the two asset representations.

### Auditability and regulatory requirements

Financial systems are required to produce audit logs where every credit and debit
can be reconciled to the exact decimal value the user saw. `BigDecimal.toPlainString()`
produces that value losslessly. A `long` satoshi value requires a conversion step that,
if applied with the wrong scale, silently produces a wrong number in the audit log.

### Overflow safety

`long` can represent at most ~9.2 × 10¹⁸ satoshis (~92 billion BTC).
For a single instrument that is safe, but a balance ledger that aggregates deposits,
credits, and partial fills over time could accumulate values large enough to overflow
in an adversarial or buggy scenario. `BigDecimal` has no overflow; it will always
produce the arithmetically correct result regardless of magnitude.

### The boundary is explicit and tested

The conversion from user-facing `BigDecimal` to internal `long` happens at exactly
one point — the `Order` constructor — and it is validated there
(`longValueExact()` + positivity check). Everything inside the matching engine
is pure `long` math. Everything inside the settlement ledger is pure `BigDecimal` math.
The two worlds meet only at that single, auditable conversion point.
