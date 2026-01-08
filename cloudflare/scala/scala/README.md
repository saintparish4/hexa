# Cloudflare Rate Limiter

A thread-safe sliding window rate limiter implementation in Scala 3.

## Purpose Contract

This project implements **two core capabilities** for revenue-critical, high-throughput APIs:

### X: Capabilities

1. **Rate Limiter**: A sliding-window rate limiter that enforces request limits per key, protecting infrastructure from traffic spikes
2. **Idempotency Store**: An idempotency store that ensures duplicate requests do not produce duplicate side effects, protecting business correctness

### Y: Constraints

This implementation operates under the following constraints:

- **Correctness under concurrency**: Must be deterministic and thread-safe without data races
- **No global locks**: Fine-grained per-key synchronization only
- **Fully testable**: No external dependencies required for testing
- **Deterministic behavior**: Same inputs always produce same outputs
- **Library-style**: Test-driven, composable components
- **Performance**: O(1)–O(log n) per operation
- **Thread-safe**: Concurrent access must be safe without global synchronization

### Z: Non-Goals

This project explicitly does **not** implement:

- HTTP server implementation
- Database or external persistence
- Akka, Play, Spark, or streaming frameworks
- Distributed consensus
- UI or dashboards
- Persistent storage
- Networking
- HTTP handlers
- Framework-specific integrations

The focus is on correctness, clarity, and tradeoff reasoning for high-throughput API platforms, independent of HTTP frameworks or infrastructure.

## Overview

This implementation provides a concurrent, per-key rate limiter using a sliding window algorithm. It is designed for high-throughput, revenue-critical APIs where correctness under concurrency is paramount.

### Key Features

- **Sliding Window Algorithm**: More accurate than fixed windows, prevents burst allowance at window boundaries
- **Thread-Safe**: Fine-grained per-key synchronization using `AtomicReference` with compare-and-swap
- **No Global Locks**: Independent keys can be processed concurrently without contention
- **Accurate Retry-After**: Precise calculation of when the next request can be attempted
- **Deterministic**: Same inputs always produce same outputs

## Quick Start

### Prerequisites

- Java 11 or higher
- sbt 1.9.x

### Build and Test

```bash
# Compile
sbt compile

# Run tests
sbt test

# Run specific test
sbt "testOnly com.cloudflare.ratelimiter.SlidingWindowRateLimiterSpec"

# Continuous testing
sbt ~test
```