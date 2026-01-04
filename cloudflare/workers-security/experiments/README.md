# Load Testing & Experimentation Framework

A comprehensive framework for testing Cloudflare Workers security controls with synthetic attack traffic and detailed metrics collection.

## Overview

This framework enables you to:
- **Simulate realistic attack patterns** (burst attacks, credential stuffing, SQL injection, etc.)
- **Measure security effectiveness** (block rates, false positives, latency impact)
- **Compare configurations** using controlled experiments
- **Validate security controls** before production deployment

## Quick Start

### 1. Install Dependencies

```bash
cd experiments
npm install
```

### 2. Start Your Worker

```bash
cd ..
npm run dev
```

### 3. Run a Load Test

```bash
cd experiments
npm run load-test -- -p BURST_ATTACK --realtime
```

## Architecture

```
experiments/
├── load-test/          # Load testing framework
│   ├── index.ts        # Core LoadTester class
│   └── cli.ts          # Command-line interface
├── metrics/            # Metrics collection and analysis
│   └── index.ts        # MetricsCollector class
├── profiles/           # Attack profile definitions
│   └── index.ts        # Pre-defined attack patterns
├── examples/           # Example test scripts
│   └── run-tests.ts    # Programmatic usage examples
└── reports/            # Generated test reports (auto-created)
```

## Attack Profiles

### Built-in Profiles

| Profile | Type | RPS | Duration | Description |
|---------|------|-----|----------|-------------|
| `BURST_ATTACK` | Burst | 1000 | 10s | Sudden traffic spike |
| `SUSTAINED_ATTACK` | Sustained | 500 | 60s | Consistent high volume |
| `SLOW_DRIP_ATTACK` | Slow-drip | 10 | 300s | Low and slow evasion |
| `CREDENTIAL_STUFFING` | Credential | 50 | 60s | Automated login attempts |
| `LEGITIMATE_TRAFFIC` | Legitimate | 100 | 120s | Normal user behavior |
| `MIXED_TRAFFIC` | Mixed | 200 | 180s | 70% legit, 30% attack |
| `SQL_INJECTION_ATTACK` | Attack | 100 | 30s | SQL injection attempts |
| `XSS_ATTACK` | Attack | 100 | 30s | XSS injection attempts |

### Profile Characteristics

Each profile defines:
- **Request rate**: Requests per second
- **Duration**: How long to run
- **Concurrency**: Parallel connections
- **Distribution pattern**: constant, linear, exponential, random, wave
- **Request templates**: What to send
- **Expected outcomes**: For validation

## CLI Usage

### Basic Commands

```bash
# Run a specific profile
npm run load-test -- -p BURST_ATTACK

# Run against deployed worker
npm run load-test -- -t https://worker.example.workers.dev -p CREDENTIAL_STUFFING

# Show realtime metrics during test
npm run load-test -- -p SUSTAINED_ATTACK --realtime

# Verbose output (show each request)
npm run load-test -- -p LEGITIMATE_TRAFFIC --verbose

# Save detailed report
npm run load-test -- -p MIXED_TRAFFIC --save

# List all available profiles
npm run load-test -- --list
```

### Advanced Options

```bash
# Override profile settings
npm run load-test -- -p BURST_ATTACK -d 30 -r 2000 -c 200

# Custom duration: 30 seconds
# Custom RPS: 2000 req/s  
# Custom concurrency: 200 workers
```

### Full Options

```
-t, --target <url>        Target URL (default: http://localhost:8787)
-p, --profile <name>      Attack profile name
-d, --duration <seconds>  Override test duration
-r, --rps <number>        Override requests per second
-c, --concurrency <num>   Override concurrent workers
-v, --verbose             Show individual request logs
--realtime                Display live metrics (updates every second)
-s, --save                Save detailed report to file
-l, --list                List available profiles
-h, --help                Show help
```

## Programmatic Usage

### Basic Example

```typescript
import { createLoadTest } from './load-test/index.js';
import { BURST_ATTACK } from './profiles/index.js';

const tester = createLoadTest('http://localhost:8787', BURST_ATTACK, {
  verbose: false,
  realtime: true,
});

await tester.run();
tester.report();
```

### Custom Profile

```typescript
import { AttackProfile } from './profiles/index.js';

const customProfile: AttackProfile = {
  name: 'Custom Test',
  description: 'My custom attack pattern',
  type: 'sustained',
  requestsPerSecond: 300,
  duration: 60,
  concurrency: 30,
  pattern: {
    distribution: 'wave',
    variance: 0.2,
  },
  requests: [
    {
      method: 'POST',
      path: '/api/login',
      body: { username: 'test', password: 'test' },
    },
  ],
  expected: {
    blockRate: 0.80,
    avgLatency: 50,
  },
};

const tester = createLoadTest('http://localhost:8787', customProfile);
await tester.run();
```

### Collecting Custom Metrics

```typescript
import { MetricsCollector } from './metrics/index.js';

const collector = new MetricsCollector('exp-123', 'My Test');

// Record individual requests
collector.record({
  timestamp: Date.now(),
  traceId: 'trace-123',
  url: '/api/public',
  method: 'GET',
  statusCode: 200,
  latency: 45,
  blocked: false,
  rateLimited: false,
  wafBlocked: false,
});

// Generate snapshot
const snapshot = collector.snapshot(10); // Last 10 seconds
console.log(snapshot.latency.p95); // 95th percentile latency

// Generate full report
const report = collector.report(80, 50); // Expected 80% block rate, 50ms latency
console.log(report.comparison.passed); // Did test pass?
```

## Metrics Collected

### Request-Level Metrics

For every request:
- **Timestamp**: When the request was made
- **Trace ID**: Unique identifier
- **URL & Method**: What was requested
- **Status Code**: HTTP response code
- **Latency**: Total request duration (ms)
- **Blocked**: Was request blocked?
- **Rate Limited**: Hit rate limit?
- **WAF Blocked**: Blocked by WAF?
- **Security Timing**: Time spent in each security check

### Aggregated Metrics

#### Request Counts
- Total requests
- Successful (2xx)
- Failed (4xx, 5xx)
- Blocked by security controls
- Rate limited
- WAF blocked

#### Rates
- Request rate (req/s)
- Error rate (%)
- Block rate (%)

#### Latency Percentiles
- Min, Max, Mean, Median
- P50, P95, P99, P99.9
- Standard deviation

#### Security Timing Breakdown
- Rate limit check latency
- Turnstile verification latency
- WAF evaluation latency

### Example Report

```
Metrics Snapshot (2024-01-15T10:30:00.000Z)
============================================================

Requests:
  Total:        5000
  Successful:   750 (15.0%)
  Failed:       4250 (85.0%)
  Blocked:      4250 (85.0%)
  Rate Limited: 4100
  WAF Blocked:  150

Rates:
  Request Rate: 500.00 req/s
  Error Rate:   85.00%
  Block Rate:   85.00%

Latency (ms):
  Min:    8.50
  Mean:   45.23
  Median: 42.10
  P95:    89.50
  P99:    125.30
  P99.9:  180.90
  Max:    245.60
  StdDev: 25.40

Status Codes:
  200: 750
  429: 4100
  403: 150
```

## Distribution Patterns

### Constant
Steady, consistent rate with optional variance.

```typescript
pattern: {
  distribution: 'constant',
  variance: 0.1, // ±10% randomness
}
```

### Linear
Gradually increases rate over time.

```typescript
pattern: {
  distribution: 'linear',
  rampUpTime: 30, // Reach full rate in 30s
}
```

### Exponential
Rapid exponential growth (simulates going viral).

```typescript
pattern: {
  distribution: 'exponential',
}
```

### Random
Random intervals between requests.

```typescript
pattern: {
  distribution: 'random',
  variance: 0.5, // High randomness
}
```

### Wave
Sine wave pattern (simulates traffic patterns).

```typescript
pattern: {
  distribution: 'wave',
  variance: 0.3,
}
```

## Experiment Workflows

### 1. Baseline Testing

Establish baseline performance:

```bash
# Test current configuration
npm run load-test -- -p LEGITIMATE_TRAFFIC --save

# Results saved to experiments/reports/exp-XXX.txt
```

### 2. Attack Simulation

Test security controls:

```bash
# Simulate burst attack
npm run load-test -- -p BURST_ATTACK --save

# Simulate credential stuffing
npm run load-test -- -p CREDENTIAL_STUFFING --save

# Compare reports to validate controls
```

### 3. Tuning Rate Limits

Find optimal rate limits:

```bash
# Test with different rates
npm run load-test -- -p SUSTAINED_ATTACK -r 100 --save
npm run load-test -- -p SUSTAINED_ATTACK -r 200 --save
npm run load-test -- -p SUSTAINED_ATTACK -r 500 --save

# Compare block rates and false positives
```

### 4. Mixed Traffic Testing

Validate that legitimate traffic isn't blocked:

```bash
# Run mixed legitimate + attack traffic
npm run load-test -- -p MIXED_TRAFFIC --realtime --save

# Check that ~70% succeeds (legitimate traffic)
```

## Interpreting Results

### Good Security Configuration

- ✅ Attack traffic blocked: >90%
- ✅ Legitimate traffic allowed: >95%
- ✅ Latency impact: <50ms additional overhead
- ✅ No false positives during legitimate traffic tests

### Signs of Issues

- ⚠️ Legitimate traffic blocked: >5%
- ⚠️ Attack traffic succeeds: >10%
- ⚠️ High latency variance: StdDev >100ms
- ⚠️ Inconsistent block rates across runs

### Common Patterns

**Too Strict**
- Legitimate traffic gets blocked
- High false positive rate
- Solution: Relax rate limits

**Too Loose**
- Attack traffic succeeds
- Low block rate on attack profiles
- Solution: Tighten rate limits, add WAF rules

**Performance Impact**
- High P99 latency
- Slow security checks
- Solution: Optimize check order, use caching

## Advanced Use Cases

### A/B Testing Security Configurations

```bash
# Configuration A
npm run load-test -- -p BURST_ATTACK --save
# Edit rate limits in src/rate-limiter.ts

# Configuration B  
npm run load-test -- -p BURST_ATTACK --save
# Compare reports
```

### Stress Testing

```bash
# Find breaking point
npm run load-test -- -p SUSTAINED_ATTACK -r 1000 -d 120
npm run load-test -- -p SUSTAINED_ATTACK -r 2000 -d 120
npm run load-test -- -p SUSTAINED_ATTACK -r 5000 -d 120
```

### Regression Testing

```bash
# Save baseline before changes
npm run load-test -- -p LEGITIMATE_TRAFFIC --save

# Make code changes

# Test again and compare
npm run load-test -- -p LEGITIMATE_TRAFFIC --save
```

## Troubleshooting

### High Error Rates During Legitimate Traffic

**Problem**: Legitimate traffic test shows >10% errors

**Possible Causes**:
- Rate limits too strict
- Turnstile always required
- WAF false positives

**Solutions**:
1. Check WAF rules aren't blocking valid patterns
2. Increase rate limits for public endpoints
3. Make Turnstile optional for read-only endpoints

### Inconsistent Results

**Problem**: Same test gives different results

**Possible Causes**:
- External factors (network, server load)
- Random distribution patterns
- Insufficient test duration

**Solutions**:
1. Run tests multiple times, average results
2. Use `constant` distribution for repeatable tests
3. Increase test duration for stability

### Low Block Rates on Attack Profiles

**Problem**: Attack tests show <50% block rate

**Possible Causes**:
- Rate limits too lenient
- WAF rules not enabled
- Tests running too slowly

**Solutions**:
1. Verify rate limits are active
2. Check WAF rules match attack patterns
3. Increase concurrency and RPS

## Best Practices

### 1. Start with Legitimate Traffic

Always test legitimate traffic first to establish a baseline and ensure you're not blocking normal users.

### 2. Use Realistic Patterns

Real attacks don't use constant rates. Use `wave` or `random` distributions for more realistic tests.

### 3. Test Incrementally

Don't jump from 100 to 10,000 RPS. Gradually increase load to find limits.

### 4. Save Everything

Always use `--save` to keep historical data for comparison.

### 5. Test Against Production

After testing locally, run the same profiles against your deployed worker to verify behavior.

### 6. Automate Regression Tests

Add load tests to CI/CD to catch security regressions:

```bash
npm run load-test -- -p LEGITIMATE_TRAFFIC
npm run load-test -- -p BURST_ATTACK
```

## Integration with CI/CD

```yaml
# .github/workflows/load-test.yml
name: Load Tests

on: [push]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - run: npm install
      - run: npm run dev &
      - run: cd experiments && npm install
      - run: cd experiments && npm run load-test -- -p LEGITIMATE_TRAFFIC
      - run: cd experiments && npm run load-test -- -p BURST_ATTACK
```

## Resources

- [Main README](../README.md) - Worker documentation
- [Attack Profiles](./profiles/index.ts) - Profile definitions
- [Metrics Reference](./metrics/index.ts) - Metrics details
- [Examples](./examples/) - Usage examples

## License

MIT