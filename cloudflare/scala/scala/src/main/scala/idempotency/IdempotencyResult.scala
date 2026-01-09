package idempotency

/**
  * Represents the result of attempting to store a response in the idempotency store 
  */
sealed trait IdempotencyResult 

/**
  * The response was successfully stored (first write) 
  */
case object Stored extends IdempotencyResult 

/**
  * A response with the same key already exists 
  * 
  * @param response The previously stored response  
  */
case class AlreadyExists(response: StoredResponse) extends IdempotencyResult  