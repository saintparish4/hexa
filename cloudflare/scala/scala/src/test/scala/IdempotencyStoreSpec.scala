package com.cloudflare.idempotency

import munit.FunSuite
import java.time.{Duration, Instant}
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import idempotency.{IdempotencyStore, InMemoryIdempotencyStore, StoredResponse, IdempotencyResult, Stored, AlreadyExists}

class IdempotencyStoreSpec extends FunSuite {
  
  // Helper to create a test response
  def createResponse(
    status: Int = 200,
    body: String = "success",
    headers: Map[String, String] = Map("Content-Type" -> "application/json")
  ): StoredResponse = {
    StoredResponse(
      status = status,
      body = body,
      headers = headers,
      createdAt = Instant.now()
    )
  }
  
  test("duplicate retries with same key - should return stored response") {
    val store = InMemoryIdempotencyStore()
    val key = "request-123"
    val response = createResponse(status = 201, body = "created")
    val ttl = Duration.ofMinutes(5)
    
    // First request - should store
    val result1 = store.putIfAbsent(key, response, ttl)
    assertEquals(result1, Stored)
    
    // Second request (retry) - should get existing response
    val result2 = store.putIfAbsent(key, createResponse(status = 200, body = "different"), ttl)
    result2 match {
      case AlreadyExists(storedResponse) =>
        assertEquals(storedResponse.status, 201)
        assertEquals(storedResponse.body, "created")
      case Stored =>
        fail("Expected AlreadyExists, got Stored")
    }
    
    // Third request (another retry) - should still get the first response
    val result3 = store.putIfAbsent(key, createResponse(status = 500, body = "error"), ttl)
    result3 match {
      case AlreadyExists(storedResponse) =>
        assertEquals(storedResponse.status, 201)
        assertEquals(storedResponse.body, "created")
      case Stored =>
        fail("Expected AlreadyExists, got Stored")
    }
    
    // Verify we can retrieve it
    val retrieved = store.get(key)
    assert(retrieved.isDefined)
    assertEquals(retrieved.get.status, 201)
    assertEquals(retrieved.get.body, "created")
  }
  
  test("concurrent duplicate writes - first writer wins, others get AlreadyExists") {
    val store = InMemoryIdempotencyStore()
    val key = "concurrent-request"
    val ttl = Duration.ofMinutes(5)
    
    // Spawn 20 concurrent writes with different response bodies
    val futures = (1 to 20).map { i =>
      Future {
        val response = createResponse(status = 200, body = s"response-$i")
        store.putIfAbsent(key, response, ttl)
      }
    }
    
    // Wait for all to complete
    val results = Await.result(Future.sequence(futures), 5.seconds)
    
    // Exactly one should be Stored
    val storedCount = results.count(_ == Stored)
    assertEquals(storedCount, 1, "Expected exactly one successful store")
    
    // All others should be AlreadyExists
    val alreadyExistsCount = results.count(_.isInstanceOf[AlreadyExists])
    assertEquals(alreadyExistsCount, 19, "Expected 19 AlreadyExists results")
    
    // All AlreadyExists should have the same response (the winning one)
    val existingResponses = results.collect {
      case AlreadyExists(response) => response.body
    }
    
    assert(existingResponses.nonEmpty)
    val firstBody = existingResponses.head
    existingResponses.foreach { body =>
      assertEquals(body, firstBody, "All AlreadyExists should return the same response")
    }
    
    // Verify only one entry in store
    assertEquals(store.size, 1)
  }
  
  test("TTL expiry correctness - expired entries not returned, keys can be reused") {
    val store = InMemoryIdempotencyStore()
    val key = "expiring-request"
    
    // Store with 1 second TTL
    val response1 = createResponse(status = 200, body = "first")
    val result1 = store.putIfAbsent(key, response1, Duration.ofSeconds(1))
    assertEquals(result1, Stored)
    
    // Immediately retrievable
    val retrieved1 = store.get(key)
    assert(retrieved1.isDefined)
    assertEquals(retrieved1.get.body, "first")
    
    // Wait for expiry
    Thread.sleep(1100) // 1.1 seconds
    
    // Should now return None (expired)
    val retrieved2 = store.get(key)
    assert(retrieved2.isEmpty, "Expired entry should not be returned")
    
    // Key should be reusable after expiry
    val response2 = createResponse(status = 201, body = "second")
    val result2 = store.putIfAbsent(key, response2, Duration.ofMinutes(5))
    assertEquals(result2, Stored, "Should be able to store after expiry")
    
    // New value should be retrievable
    val retrieved3 = store.get(key)
    assert(retrieved3.isDefined)
    assertEquals(retrieved3.get.body, "second")
  }
  
  test("TTL expiry - putIfAbsent on expired key stores new value") {
    val store = InMemoryIdempotencyStore()
    val key = "expiring-put"
    
    // Store with short TTL
    val response1 = createResponse(body = "first")
    store.putIfAbsent(key, response1, Duration.ofMillis(500))
    
    // Wait for expiry
    Thread.sleep(600)
    
    // putIfAbsent should succeed with new value
    val response2 = createResponse(body = "second")
    val result = store.putIfAbsent(key, response2, Duration.ofMinutes(5))
    assertEquals(result, Stored)
    
    // Should get the new value
    val retrieved = store.get(key)
    assert(retrieved.isDefined)
    assertEquals(retrieved.get.body, "second")
  }
  
  test("retry storm - multiple concurrent requests with same key") {
    val store = InMemoryIdempotencyStore()
    val key = "storm-request"
    val ttl = Duration.ofMinutes(5)
    
    // Simulate a retry storm: 100 concurrent requests
    val stormSize = 100
    val futures = (1 to stormSize).map { i =>
      Future {
        // All trying to store slightly different responses
        val response = createResponse(status = 200, body = s"attempt-$i")
        store.putIfAbsent(key, response, ttl)
      }
    }
    
    // Wait for storm to complete
    val results = Await.result(Future.sequence(futures), 10.seconds)
    
    // Exactly one should succeed
    val storedCount = results.count(_ == Stored)
    assertEquals(storedCount, 1, "Exactly one request should succeed in a retry storm")
    
    // All others should get AlreadyExists
    val alreadyExistsCount = results.count(_.isInstanceOf[AlreadyExists])
    assertEquals(alreadyExistsCount, stormSize - 1)
    
    // All AlreadyExists responses should be identical
    val bodies = results.collect {
      case AlreadyExists(response) => response.body
    }.distinct
    
    assertEquals(bodies.size, 1, "All retries should get the same stored response")
    
    // Verify store state is consistent
    assertEquals(store.size, 1, "Store should contain exactly one entry")
    
    val retrieved = store.get(key)
    assert(retrieved.isDefined, "Stored entry should be retrievable")
  }
  
  test("partial failure simulation - verify no partial state") {
    val store = InMemoryIdempotencyStore()
    val key = "partial-test"
    val ttl = Duration.ofMinutes(5)
    
    // Store initial value
    val response1 = createResponse(body = "initial")
    assertEquals(store.putIfAbsent(key, response1, ttl), Stored)
    
    // Attempt to store different value (should fail)
    val response2 = createResponse(body = "different")
    val result = store.putIfAbsent(key, response2, ttl)
    
    result match {
      case AlreadyExists(storedResponse) =>
        // Should get back the original response, not a mix
        assertEquals(storedResponse.body, "initial")
        
      case Stored =>
        fail("Should not have stored a second value")
    }
    
    // Verify get returns consistent state
    val retrieved = store.get(key)
    assert(retrieved.isDefined)
    assertEquals(retrieved.get.body, "initial")
    
    // No partial state should exist
    assertEquals(store.size, 1, "Should have exactly one entry")
  }
  
  test("get on non-existent key returns None") {
    val store = InMemoryIdempotencyStore()
    
    val result = store.get("does-not-exist")
    assert(result.isEmpty)
  }
  
  test("multiple keys are isolated") {
    val store = InMemoryIdempotencyStore()
    val ttl = Duration.ofMinutes(5)
    
    val response1 = createResponse(body = "key1-response")
    val response2 = createResponse(body = "key2-response")
    
    // Store two different keys
    assertEquals(store.putIfAbsent("key1", response1, ttl), Stored)
    assertEquals(store.putIfAbsent("key2", response2, ttl), Stored)
    
    // Both should be retrievable independently
    val retrieved1 = store.get("key1")
    assert(retrieved1.isDefined)
    assertEquals(retrieved1.get.body, "key1-response")
    
    val retrieved2 = store.get("key2")
    assert(retrieved2.isDefined)
    assertEquals(retrieved2.get.body, "key2-response")
    
    assertEquals(store.size, 2)
  }
  
  test("response fields are preserved correctly") {
    val store = InMemoryIdempotencyStore()
    val key = "detailed-response"
    
    val response = StoredResponse(
      status = 201,
      body = """{"id": 123, "status": "created"}""",
      headers = Map(
        "Content-Type" -> "application/json",
        "X-Request-Id" -> "abc-123",
        "Location" -> "/api/resources/123"
      ),
      createdAt = Instant.parse("2024-01-01T12:00:00Z")
    )
    
    store.putIfAbsent(key, response, Duration.ofMinutes(5))
    
    val retrieved = store.get(key)
    assert(retrieved.isDefined)
    
    val stored = retrieved.get
    assertEquals(stored.status, 201)
    assertEquals(stored.body, """{"id": 123, "status": "created"}""")
    assertEquals(stored.headers.size, 3)
    assertEquals(stored.headers("Content-Type"), "application/json", "Content-Type header should match")
    assertEquals(stored.headers("X-Request-Id"), "abc-123", "X-Request-Id header should match")
    assertEquals(stored.headers("Location"), "/api/resources/123", "Location header should match")
    assertEquals(stored.createdAt, Instant.parse("2024-01-01T12:00:00Z"))
  }
  
  test("cleanupExpired removes only expired entries") {
    val store = InMemoryIdempotencyStore()
    
    // Store some entries with different TTLs
    store.putIfAbsent("short-ttl", createResponse(body = "expires-soon"), Duration.ofMillis(500))
    store.putIfAbsent("long-ttl", createResponse(body = "stays"), Duration.ofMinutes(5))
    
    assertEquals(store.size, 2)
    
    // Wait for short TTL to expire
    Thread.sleep(600)
    
    // Both still in store (lazy cleanup)
    assertEquals(store.size, 2)
    
    // Manual cleanup
    val removed = store.cleanupExpired()
    assertEquals(removed, 1, "Should remove one expired entry")
    
    // Now only one remains
    assertEquals(store.size, 1)
    
    // Long TTL entry should still be accessible
    val retrieved = store.get("long-ttl")
    assert(retrieved.isDefined)
    assertEquals(retrieved.get.body, "stays")
    
    // Short TTL entry should be gone
    assert(store.get("short-ttl").isEmpty)
  }
  
  test("validSize counts only non-expired entries") {
    val store = InMemoryIdempotencyStore()
    
    // Store entries with different TTLs
    store.putIfAbsent("key1", createResponse(), Duration.ofMillis(500))
    store.putIfAbsent("key2", createResponse(), Duration.ofMinutes(5))
    store.putIfAbsent("key3", createResponse(), Duration.ofMinutes(5))
    
    assertEquals(store.size, 3)
    assertEquals(store.validSize, 3)
    
    // Wait for one to expire
    Thread.sleep(600)
    
    // Total size unchanged (lazy cleanup)
    assertEquals(store.size, 3)
    
    // But valid size should be 2
    assertEquals(store.validSize, 2)
  }
  
  test("clear removes all entries") {
    val store = InMemoryIdempotencyStore()
    val ttl = Duration.ofMinutes(5)
    
    // Store multiple entries
    store.putIfAbsent("key1", createResponse(), ttl)
    store.putIfAbsent("key2", createResponse(), ttl)
    store.putIfAbsent("key3", createResponse(), ttl)
    
    assertEquals(store.size, 3)
    
    // Clear all
    store.clear()
    
    assertEquals(store.size, 0)
    assert(store.get("key1").isEmpty)
    assert(store.get("key2").isEmpty)
    assert(store.get("key3").isEmpty)
  }
  
  test("constructor validation - null checks") {
    val store = InMemoryIdempotencyStore()
    val response = createResponse()
    val ttl = Duration.ofMinutes(5)
    
    // Null key
    intercept[IllegalArgumentException] {
      store.putIfAbsent(null, response, ttl)
    }
    
    intercept[IllegalArgumentException] {
      store.get(null)
    }
    
    // Null response
    intercept[IllegalArgumentException] {
      store.putIfAbsent("key", null, ttl)
    }
    
    // Null TTL
    intercept[IllegalArgumentException] {
      store.putIfAbsent("key", response, null)
    }
    
    // Zero TTL
    intercept[IllegalArgumentException] {
      store.putIfAbsent("key", response, Duration.ZERO)
    }
    
    // Negative TTL
    intercept[IllegalArgumentException] {
      store.putIfAbsent("key", response, Duration.ofSeconds(-5))
    }
  }
  
  test("concurrent get and putIfAbsent are safe") {
    val store = InMemoryIdempotencyStore()
    val key = "concurrent-rw"
    val ttl = Duration.ofMinutes(5)
    
    // Mix of writes and reads
    val futures = (1 to 50).map { i =>
      Future {
        if (i % 2 == 0) {
          // Write
          store.putIfAbsent(key, createResponse(body = s"write-$i"), ttl)
        } else {
          // Read
          store.get(key)
        }
      }
    }
    
    // Should complete without deadlock or exception
    val results = Await.result(Future.sequence(futures), 5.seconds)
    
    // Should have one stored entry
    assert(store.size > 0, "Store should have at least one entry")
    
    // get should return a consistent value
    val retrieved = store.get(key)
    assert(retrieved.isDefined)
  }
}