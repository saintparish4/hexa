package idempotency

import java.time.Instant

/**
  * Represents a stored response in the idempotency store 
  * 
  * @param status HTTP status code 
  * @param body Response body 
  * @param headers HTTP headers 
  * @param createdAt Timestamp when the response was created  
  */
case class StoredResponse(
    status: Int,
    body: String,
    headers: Map[String, String],
    createdAt: Instant 
)

