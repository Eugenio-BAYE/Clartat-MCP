package com.clartat.mcp.protocol

import com.clartat.mcp.domain._
import io.circe.Json
import io.circe.syntax._

/**
 * JSON-RPC 2.0 protocol handler
 * 
 * Provides utilities for creating JSON-RPC responses following the specification.
 */
object JsonRpcProtocol {
  
  /**
   * Creates a successful JSON-RPC response
   * 
   * @param id Request identifier
   * @param result Result value
   * @return JSON-RPC response
   */
  def successResponse(id: Option[Json], result: Json): JsonRpcResponse = {
    JsonRpcResponse(
      jsonrpc = "2.0",
      id = id,
      result = Some(result),
      error = None
    )
  }
  
  /**
   * Creates an error JSON-RPC response
   * 
   * @param id Request identifier
   * @param code Error code
   * @param message Error message
   * @param data Optional additional error data
   * @return JSON-RPC response
   */
  def errorResponse(
    id: Option[Json],
    code: Int,
    message: String,
    data: Option[Json] = None
  ): JsonRpcResponse = {
    JsonRpcResponse(
      jsonrpc = "2.0",
      id = id,
      result = None,
      error = Some(JsonRpcError(code, message, data))
    )
  }
  
  /**
   * Creates a method not found error response
   * 
   * @param id Request identifier
   * @param methodName Name of the unknown method
   * @return JSON-RPC response
   */
  def methodNotFoundResponse(id: Option[Json], methodName: String): JsonRpcResponse = {
    errorResponse(
      id = id,
      code = JsonRpcErrorCode.MethodNotFound,
      message = s"Method not found: $methodName"
    )
  }
  
  /**
   * Creates an invalid parameters error response
   * 
   * @param id Request identifier
   * @param reason Reason for the invalid parameters
   * @return JSON-RPC response
   */
  def invalidParamsResponse(id: Option[Json], reason: String): JsonRpcResponse = {
    errorResponse(
      id = id,
      code = JsonRpcErrorCode.InvalidParams,
      message = s"Invalid params: $reason"
    )
  }
  
  /**
   * Creates a parse error response
   * 
   * @param errorMessage Error message from parsing
   * @return JSON-RPC response
   */
  def parseErrorResponse(errorMessage: String): JsonRpcResponse = {
    errorResponse(
      id = None,
      code = JsonRpcErrorCode.ParseError,
      message = s"Parse error: $errorMessage"
    )
  }
  
  /**
   * Checks if a request is a notification (no id field)
   * 
   * @param request JSON-RPC request
   * @return true if the request is a notification
   */
  def isNotification(request: JsonRpcRequest): Boolean = {
    request.id.isEmpty
  }
}
