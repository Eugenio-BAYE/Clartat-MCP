package com.clartat.mcp.service

import com.clartat.mcp.domain._
import com.clartat.mcp.protocol.{JsonRpcProtocol, McpProtocol}
import com.clartat.mcp.tools.ToolRegistry
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._

/**
 * MCP request handler service
 * 
 * This service handles all MCP protocol requests and coordinates
 * between the protocol layer and tool execution.
 * 
 * @param toolRegistry The registry containing all available tools
 */
class McpRequestHandler(toolRegistry: ToolRegistry) {
  
  /**
   * Handles a JSON-RPC request and returns the appropriate response
   * 
   * @param request The JSON-RPC request to handle
   * @return JSON-RPC response
   */
  def handleRequest(request: JsonRpcRequest): JsonRpcResponse = {
    request.method match {
      case "initialize" => handleInitialize(request)
      case "tools/list" => handleToolsList(request)
      case "tools/call" => handleToolsCall(request)
      case "notifications/initialized" => handleNotification(request)
      case _ => JsonRpcProtocol.methodNotFoundResponse(request.id, request.method)
    }
  }
  
  /**
   * Handles the initialize request
   * 
   * @param request The initialize request
   * @return Response with server capabilities
   */
  private def handleInitialize(request: JsonRpcRequest): JsonRpcResponse = {
    val result = McpProtocol.createInitializeResult()
    JsonRpcProtocol.successResponse(request.id, result.asJson)
  }
  
  /**
   * Handles the tools/list request
   * 
   * @param request The tools list request
   * @return Response with list of available tools
   */
  private def handleToolsList(request: JsonRpcRequest): JsonRpcResponse = {
    val toolSchemas = toolRegistry.listToolSchemas()
    val result = McpProtocol.createToolsListResult(toolSchemas)
    JsonRpcProtocol.successResponse(request.id, result)
  }
  
  /**
   * Handles the tools/call request
   * 
   * This method executes a tool with the provided arguments.
   * 
   * @param request The tool call request
   * @return Response with tool execution result
   */
  private def handleToolsCall(request: JsonRpcRequest): JsonRpcResponse = {
    request.params match {
      case Some(params) =>
        extractToolNameAndArguments(params) match {
          case Some((toolName, arguments)) =>
            executeToolAndCreateResponse(request.id, toolName, arguments)
          case None =>
            JsonRpcProtocol.invalidParamsResponse(
              request.id,
              "Expected params with 'name' and 'arguments' fields"
            )
        }
      case None =>
        JsonRpcProtocol.invalidParamsResponse(
          request.id,
          "Missing params for tools/call"
        )
    }
  }
  
  /**
   * Handles notification requests (no response required)
   * 
   * @param request The notification request
   * @return Empty response (will not be sent)
   */
  private def handleNotification(request: JsonRpcRequest): JsonRpcResponse = {
    // Log notification if needed
    System.err.println(s"Received notification: ${request.method}")
    
    // Return empty response (won't be sent for notifications)
    JsonRpcResponse(
      jsonrpc = "2.0",
      id = None,
      result = None,
      error = None
    )
  }
  
  /**
   * Extracts tool name and arguments from params JSON
   * 
   * @param params The params JSON
   * @return Optional tuple of (tool name, arguments)
   */
  private def extractToolNameAndArguments(params: Json): Option[(String, Json)] = {
    val cursor = params.hcursor
    for {
      name <- cursor.get[String]("name").toOption
      arguments <- cursor.get[Json]("arguments").toOption
    } yield (name, arguments)
  }
  
  /**
   * Executes a tool and creates the appropriate response
   * 
   * @param requestId The request ID
   * @param toolName The name of the tool to execute
   * @param arguments The tool arguments
   * @return JSON-RPC response with execution result
   */
  private def executeToolAndCreateResponse(
    requestId: Option[Json],
    toolName: String,
    arguments: Json
  ): JsonRpcResponse = {
    val toolResult = toolRegistry.executeTool(toolName, arguments)
    val resultJson = McpProtocol.toolResultToJson(toolResult)
    JsonRpcProtocol.successResponse(requestId, resultJson)
  }
  
  /**
   * Checks if a request requires a response
   * 
   * Notifications (requests without an id) don't require responses
   * 
   * @param request The request to check
   * @return true if a response should be sent
   */
  def shouldRespond(request: JsonRpcRequest): Boolean = {
    request.id.isDefined
  }
}

/**
 * Companion object for creating request handlers
 */
object McpRequestHandler {
  
  /**
   * Creates a request handler with the given tool registry
   * 
   * @param toolRegistry The tool registry to use
   * @return New request handler
   */
  def apply(toolRegistry: ToolRegistry): McpRequestHandler = {
    new McpRequestHandler(toolRegistry)
  }
}
