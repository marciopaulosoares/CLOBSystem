Create a **short and objective README.md** for a Java application that implements a **Central Limit Order Book (CLOB)** and a **load testing module**.

## Context

* The system follows a high-performance CLOB design with:

  * OrderBook using price-time priority
  * MatchingEngine with Strategy pattern
  * Accounts with balance locking and settlement

* Concurrency handled using `StampedLock`

* Focus on:

  * low latency
  * deterministic behavior
  * high throughput

* There is a second module:

  * `clob-load-test`
  * simulates high load with concurrent order flow
  * measures throughput and latency

* The project was built using **AI-assisted development**

  * Mention usage of **The 4D AI Fluency Framework**
  * AI used mainly for boilerplate generation and structure

## Requirements for README

* Be **short and direct**
* Focus only on **core concepts and architecture**
* Include:

  1. Project overview
  2. Core components (brief)
  3. How to build and run
  4. Load test description
  5. AI usage summary

## Constraints

* Do NOT include:

  * code examples
  * class implementations
  * long explanations
  * emotional or marketing language

* Use a **technical and neutral tone**

## Output

Generate a complete `README.md` in markdown format.
