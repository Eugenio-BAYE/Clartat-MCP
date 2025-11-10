package com.clartat.mcp.protocol

import com.clartat.mcp.domain._
import io.circe.Json
import io.circe.syntax._

/**
 * MCP protocol handler
 * 
 * Provides utilities for creating MCP-specific responses.
 */
object McpProtocol {
  
  /**
   * Creates the initialize response
   * 
   * @return Initialize result with server capabilities
   */
  def createInitializeResult(): InitializeResult = {
    InitializeResult(
      protocolVersion = McpConfig.ProtocolVersion,
      capabilities = Capabilities(
        tools = Some(ToolsCapability(listChanged = Some(true)))
      ),
      serverInfo = ServerInfo(
        name = McpConfig.ServerName,
        version = McpConfig.ServerVersion
      )
    )
  }
  
  /**
   * Creates a tool schema in MCP format
   * 
   * @param name Tool name
   * @param description Tool description
   * @param parameters List of tool parameters
   * @return JSON representation of the tool
   */
  def createToolSchema(
    name: String,
    description: String,
    parameters: List[ToolParameter]
  ): Json = {
    val properties = Json.obj(
      parameters.map { param =>
        val baseFields = List(
          "type" -> Json.fromString(param.paramType),
          "description" -> Json.fromString(param.description)
        )
        
        // Add items field for array types
        val fields = if (param.paramType == "array") {
          param.items match {
            case Some(items) =>
              baseFields :+ ("items" -> Json.obj(
                "type" -> Json.fromString(items.itemType)
              ))
            case None =>
              // Array type must have items defined
              throw new IllegalArgumentException(
                s"Parameter '${param.name}' is of type 'array' but has no 'items' specification"
              )
          }
        } else {
          baseFields
        }
        
        param.name -> Json.obj(fields*)
      }*
    )
    
    val required = Json.arr(
      parameters.filter(_.required).map(p => Json.fromString(p.name))*
    )
    
    // Use both "inputSchema" (MCP spec, Cursor) and "parameters" (VSCode) for maximum compatibility
    val schemaObject = Json.obj(
      "type" -> Json.fromString("object"),
      "properties" -> properties,
      "required" -> required
    )
    
    Json.obj(
      "name" -> Json.fromString(name),
      "description" -> Json.fromString(description),
      "inputSchema" -> schemaObject,
      "parameters" -> schemaObject
    )
  }
  
  /**
   * Creates a tools list response
   * 
   * @param tools List of tool schemas
   * @return JSON representation of the tools list
   */
  def createToolsListResult(tools: List[Json]): Json = {
    Json.obj(
      "tools" -> Json.arr(tools*)
    )
  }
  
  /**
   * Creates a tool call result in MCP format
   * 
   * @param content The result content as a string
   * @return JSON representation of the tool call result
   */
  def createToolCallResult(content: String): Json = {
    Json.obj(
      "content" -> Json.arr(
        Json.obj(
          "type" -> Json.fromString("text"),
          "text" -> Json.fromString(content)
        )
      )
    )
  }
  
  /**
   * Converts a ToolResult to JSON
   * 
   * @param result Tool execution result
   * @return JSON representation
   */
  def toolResultToJson(result: ToolResult): Json = result match {
    case ToolSuccess(content) => content
    case ToolFailure(message, _) => createToolCallResult(s"Error: $message")
  }
}
