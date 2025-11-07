package com.clartat.mcp.tools

import com.clartat.mcp.tools.impl._

/**
 * Default tool set for the MCP server
 * 
 * This object provides a convenient way to get all default tools.
 * To add a new tool:
 * 1. Create a new class in com.clartat.mcp.tools.impl that extends Tool
 * 2. Add it to the defaultTools list below
 * 3. That's it! The tool will be automatically registered and available.
 */
object DefaultTools {
  
  /**
   * List of all default tools
   * 
   * Add new tools here to make them available in the server
   */
  val defaultTools: List[Tool] = List(
    AddTool()
    // Add more tools here, for example:
    // SubtractTool(),
    // MultiplyTool(),
    // DivideTool(),
    // etc.
  )
  
  /**
   * Creates a tool registry with all default tools registered
   * 
   * @return Tool registry with default tools
   */
  def createRegistry(): ToolRegistry = {
    ToolRegistry(defaultTools*)
  }
}
