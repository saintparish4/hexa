package rateLimiter

import munit.FunSuite
import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

class SlidingWindowRateLimiterSpec extends FunSuite {
  
  test("burst within limit - all requests should be allowed") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 5, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // All 5 requests should be allowed
    (1 to 5).foreach { i =>
      val result = limiter.allow("user1", now.plusMillis(i))
      assertEquals(result, Allow, s"Request $i should be allowed")
    }
  }
  
  test("burst exceeding limit - excess requests should be rejected") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 3, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // First 3 should be allowed
    (1 to 3).foreach { i =>
      val result = limiter.allow("user1", now.plusMillis(i))
      assertEquals(result, Allow, s"Request $i should be allowed")
    }
    
    // 4th request should be rejected
    val result = limiter.allow("user1", now.plusMillis(4))
    assert(result.isInstanceOf[Rejected], "4th request should be rejected")
  }
  
  test("boundary window behavior - exactly at limit") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 2, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // First 2 requests allowed
    assertEquals(limiter.allow("user1", now), Allow)
    assertEquals(limiter.allow("user1", now.plusMillis(100)), Allow)
    
    // 3rd request rejected
    val rejected = limiter.allow("user1", now.plusMillis(200))
    assert(rejected.isInstanceOf[Rejected])
  }
  
  test("accurate retry-after calculation") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 2, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // Fill the window
    limiter.allow("user1", now)
    limiter.allow("user1", now.plusMillis(100))
    
    // Next request should be rejected with retry-after
    val result = limiter.allow("user1", now.plusMillis(200))
    result match {
      case Rejected(retryAfter) =>
        // Should retry after the first request expires (at now + 1000ms)
        // Current time is now + 200ms, so retry after should be ~800ms
        val expectedRetryMs = 800
        val actualRetryMs = retryAfter.toMillis
        assert(actualRetryMs >= expectedRetryMs - 10 && actualRetryMs <= expectedRetryMs + 10,
          s"RetryAfter should be around ${expectedRetryMs}ms, got ${actualRetryMs}ms")
      case _ => fail("Expected Rejected decision")
    }
  }
  
  test("window sliding behavior - old timestamps expire") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 2, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // Fill the window at time T
    assertEquals(limiter.allow("user1", now), Allow)
    assertEquals(limiter.allow("user1", now.plusMillis(100)), Allow)
    
    // Request at T+200 should be rejected (window still full)
    assert(limiter.allow("user1", now.plusMillis(200)).isInstanceOf[Rejected])
    
    // Request at T+1100 should be allowed (first request expired)
    assertEquals(limiter.allow("user1", now.plusMillis(1100)), Allow)
  }
  
  test("concurrent access correctness - multiple threads, same key") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 10, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // Launch 20 concurrent requests for the same key
    val futures = (1 to 20).map { i =>
      Future {
        limiter.allow("user1", now.plusMillis(i))
      }
    }
    
    val results = Await.result(Future.sequence(futures), 5.seconds)
    val allowedCount = results.count(_ == Allow)
    val rejectedCount = results.count(_.isInstanceOf[Rejected])
    
    // Exactly 10 should be allowed, 10 rejected
    assertEquals(allowedCount, 10, "Should allow exactly 10 requests")
    assertEquals(rejectedCount, 10, "Should reject exactly 10 requests")
  }
  
  test("different keys are isolated") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 2, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // Fill window for user1
    assertEquals(limiter.allow("user1", now), Allow)
    assertEquals(limiter.allow("user1", now.plusMillis(100)), Allow)
    assert(limiter.allow("user1", now.plusMillis(200)).isInstanceOf[Rejected])
    
    // user2 should have independent limit
    assertEquals(limiter.allow("user2", now.plusMillis(300)), Allow)
    assertEquals(limiter.allow("user2", now.plusMillis(400)), Allow)
    assert(limiter.allow("user2", now.plusMillis(500)).isInstanceOf[Rejected])
  }
  
  test("clear() resets all keys") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 2, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // Fill window
    assertEquals(limiter.allow("user1", now), Allow)
    assertEquals(limiter.allow("user1", now.plusMillis(100)), Allow)
    assert(limiter.allow("user1", now.plusMillis(200)).isInstanceOf[Rejected])
    
    // Clear and retry
    limiter.clear()
    assertEquals(limiter.allow("user1", now.plusMillis(300)), Allow)
  }
  
  test("large burst scenario") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 100, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // 100 requests should be allowed
    (1 to 100).foreach { i =>
      assertEquals(limiter.allow("user1", now.plusMillis(i)), Allow)
    }
    
    // 101st should be rejected
    assert(limiter.allow("user1", now.plusMillis(101)).isInstanceOf[Rejected])
  }
  
  test("time moves backward handling") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 2, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    // Request at T
    assertEquals(limiter.allow("user1", now), Allow)
    
    // Request at T-100 (time moved backward) - should still work
    // The timestamp will be before the window start, so it will be filtered out
    assertEquals(limiter.allow("user1", now.minusMillis(100)), Allow)
  }
  
  test("zero millisecond precision") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 3, windowSize = Duration.ofMillis(100))
    val now = Instant.now()
    
    // Fill in 100ms window
    assertEquals(limiter.allow("user1", now), Allow)
    assertEquals(limiter.allow("user1", now.plusMillis(50)), Allow)
    assertEquals(limiter.allow("user1", now.plusMillis(99)), Allow)
    
    // Should be rejected
    assert(limiter.allow("user1", now.plusMillis(99)).isInstanceOf[Rejected])
    
    // After window slides, should be allowed
    assertEquals(limiter.allow("user1", now.plusMillis(150)), Allow)
  }
  
  test("single request limit") {
    val limiter = new SlidingWindowRateLimiter(maxRequests = 1, windowSize = Duration.ofSeconds(1))
    val now = Instant.now()
    
    assertEquals(limiter.allow("user1", now), Allow)
    assert(limiter.allow("user1", now.plusMillis(100)).isInstanceOf[Rejected])
    assertEquals(limiter.allow("user1", now.plusMillis(1001)), Allow)
  }
}
