package com.clartat.mcp.tools

import com.clartat.mcp.domain._
import com.clartat.mcp.protocol.McpProtocol
import io.circe.Json

import scala.collection.mutable

/**
 * Central registry for all available tools
 * 
 * Manages tool registration, discovery, and invocation.
 * This makes it easy to add new tools without modifying core logic.
 */
class ToolRegistry {
  
  private val tools: mutable.Map[String, Tool] = mutable.Map.empty
  
  /**
   * Registers a new tool
   * 
   * @param tool The tool to register
   * @return this registry for chaining
   */
  def register(tool: Tool): ToolRegistry = {
    tools(tool.name) = tool
    this
  }
  
  /**
   * Registers multiple tools
   * 
   * @param tools Tools to register
   * @return this registry for chaining
   */
  def registerAll(tools: Tool*): ToolRegistry = {
    tools.foreach(register)
    this
  }
  
  /**
   * Retrieves a tool by name
   * 
   * @param name Tool name
   * @return Optional tool
   */
  def getTool(name: String): Option[Tool] = {
    tools.get(name)
  }
  
  /**
   * Lists all registered tools
   * 
   * @return List of all tools
   */
  def listTools(): List[Tool] = {
    tools.values.toList.sortBy(_.name)
  }
  
  /**
   * Creates JSON schemas for all registered tools
   * 
   * @return List of tool schemas in MCP format
   */
  def listToolSchemas(): List[Json] = {
    listTools().map { tool =>
      McpProtocol.createToolSchema(
        name = tool.name,
        description = tool.description,
        parameters = tool.parameters
      )
    }
  }
  
  /**
   * Executes a tool by name with given arguments
   * 
   * @param name Tool name
   * @param arguments JSON arguments
   * @return Tool execution result or failure if tool not found
   */
  def executeTool(name: String, arguments: Json): ToolResult = {
    getTool(name) match {
      case Some(tool) =>
        if (tool.validateArguments(arguments)) {
          tool.execute(arguments)
        } else {
          ToolFailure(s"Invalid arguments for tool '$name'")
        }
      case None =>
        ToolFailure(s"Tool not found: $name")
    }
  }
  
  /**
   * Gets the number of registered tools
   * 
   * @return Number of tools
   */
  def size: Int = tools.size
  
  /**
   * Checks if a tool exists
   * 
   * @param name Tool name
   * @return true if the tool exists
   */
  def contains(name: String): Boolean = tools.contains(name)
}

/**
 * Companion object for creating tool registries
 */
object ToolRegistry {
  
  /**
   * Creates a new empty tool registry
   * 
   * @return New tool registry
   */
  def empty: ToolRegistry = new ToolRegistry()
  
  /**
   * Creates a tool registry with the given tools
   * 
   * @param tools Initial tools to register
   * @return New tool registry with the tools
   */
  def apply(tools: Tool*): ToolRegistry = {
    empty.registerAll(tools*)
  }
}
