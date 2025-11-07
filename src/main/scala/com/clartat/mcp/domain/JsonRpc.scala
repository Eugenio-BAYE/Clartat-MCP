package com.clartat.mcp.domain

import io.circe.Json

/**
 * JSON-RPC 2.0 domain models
 * @see https://www.jsonrpc.org/specification
 */

/**
 * Represents a JSON-RPC 2.0 request
 * 
 * @param jsonrpc Protocol version (must be "2.0")
 * @param id Unique identifier for the request (None for notifications)
 * @param method The name of the method to be invoked
 * @param params Optional parameters for the method
 */
case class JsonRpcRequest(
  jsonrpc: String,
  id: Option[Json],
  method: String,
  params: Option[Json]
)

/**
 * Represents a JSON-RPC 2.0 response
 * 
 * @param jsonrpc Protocol version (must be "2.0")
 * @param id Request identifier (matches the request id)
 * @param result Method result (mutually exclusive with error)
 * @param error Error object (mutually exclusive with result)
 */
case class JsonRpcResponse(
  jsonrpc: String,
  id: Option[Json],
  result: Option[Json] = None,
  error: Option[JsonRpcError] = None
)

/**
 * Represents a JSON-RPC 2.0 error object
 * 
 * @param code Error code (standard codes: -32700 to -32600)
 * @param message Human-readable error description
 * @param data Optional additional error information
 */
case class JsonRpcError(
  code: Int,
  message: String,
  data: Option[Json] = None
)

/**
 * Standard JSON-RPC error codes
 */
object JsonRpcErrorCode {
  val ParseError: Int = -32700
  val InvalidRequest: Int = -32600
  val MethodNotFound: Int = -32601
  val InvalidParams: Int = -32602
  val InternalError: Int = -32603
  
  // Server error codes range: -32000 to -32099
  val ServerErrorStart: Int = -32099
  val ServerErrorEnd: Int = -32000
}
