package com.clartat.mcp.tools

import com.clartat.mcp.tools.impl._

/**
 * Default tool set for the MCP server
 * 
 * This object provides the GitHub Project v2 tool for analyzing GitHub Projects.
 */
object DefaultTools {
  
  /**
   * List of all available tools
   */
  val defaultTools: List[Tool] = List(
    GithubProjectV2Tool()
  )
  
  /**
   * Registers all default tools in a registry
   * 
   * @param registry The registry to populate
   * @return The populated registry
   */
  def registerAll(registry: ToolRegistry): ToolRegistry = {
    registry.registerAll(defaultTools*)
  }
  
  /**
   * Creates a new tool registry with all default tools
   * 
   * @return A populated tool registry
   */
  def createRegistry(): ToolRegistry = {
    val registry = new ToolRegistry()
    registerAll(registry)
  }
}
