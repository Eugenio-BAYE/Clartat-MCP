package com.clartat.mcp.tools

import com.clartat.mcp.domain._
import io.circe.Json

/**
 * Base trait for all MCP tools
 * 
 * A tool is a callable function that can be invoked through the MCP protocol.
 * Each tool must define its name, description, parameters, and execution logic.
 */
trait Tool {
  
  /**
   * Unique name of the tool
   */
  def name: String
  
  /**
   * Human-readable description of what the tool does
   */
  def description: String
  
  /**
   * List of parameters accepted by this tool
   */
  def parameters: List[ToolParameter]
  
  /**
   * Executes the tool with the given arguments
   * 
   * @param arguments JSON object containing the tool arguments
   * @return Result of the tool execution
   */
  def execute(arguments: Json): ToolResult
  
  /**
   * Validates that the provided arguments match the expected parameters
   * 
   * @param arguments JSON object containing the tool arguments
   * @return true if arguments are valid, false otherwise
   */
  def validateArguments(arguments: Json): Boolean = {
    System.err.println(s"[Tool.$name] Validating arguments: ${arguments.noSpaces}")
    
    val cursor = arguments.hcursor
    val requiredParams = parameters.filter(_.required)
    
    System.err.println(s"[Tool.$name] Required parameters: ${requiredParams.map(_.name).mkString(", ")}")
    
    val allValid = requiredParams.forall { param =>
      val exists = cursor.downField(param.name).succeeded
      System.err.println(s"[Tool.$name] Parameter '${param.name}' exists: $exists")
      exists
    }
    
    System.err.println(s"[Tool.$name] Validation result: $allValid")
    allValid
  }
}

/**
 * Companion object with utility methods
 */
object Tool {
  
  /**
   * Creates a generic error result
   * 
   * @param message Error message
   * @return Tool failure result
   */
  def failure(message: String): ToolResult = {
    ToolFailure(message)
  }
  
  /**
   * Creates a successful text result
   * 
   * @param text Text content
   * @return Tool success result with text content
   */
  def textSuccess(text: String): ToolResult = {
    ToolSuccess(
      Json.obj(
        "content" -> Json.arr(
          Json.obj(
            "type" -> Json.fromString("text"),
            "text" -> Json.fromString(text)
          )
        )
      )
    )
  }
  
  /**
   * Creates a successful JSON result
   * 
   * @param json JSON content
   * @return Tool success result with JSON content
   */
  def jsonSuccess(json: Json): ToolResult = {
    ToolSuccess(json)
  }
}
