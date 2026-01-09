package idempotency

import java.time.{Duration, Instant}

/**
  * Idempotency store that ensures requests with the same idempotency key 
  * produce exactly one logical result, even when retried 
  * 
  * Semantics: 
    * - First writer wins 
    * - Duplicate keys return stored response 
    * - Expired keys may be reused 
    * - No partial or in-progress state allowed 
  */
trait IdempotencyStore {

    /**
      * Retrieve a stored response for the given key 
      * 
      * @param key The idempotency key 
      * @return Some(response) if key exists and has not expired, None otherwise
      */
    def get(key: String): Option[StoredResponse] 

    /**
      * Store a response only if the key does not already exist 
      * 
      * This operation is atomic - concurrent calls with the same key will result  
      * in exactly one successful store, with others receiving AlreadyExists
      * 
      * @param key The idempotency key 
      * @param response The response to store 
      * @param ttl Time-to-live duration for the stored response 
      * @return Stored if successfully stored, AlreadyExists(response) if key already exists  
      */
    def putIfAbsent(
        key: String, 
        response: StoredResponse, 
        ttl: Duration 
    ): IdempotencyResult  
}
