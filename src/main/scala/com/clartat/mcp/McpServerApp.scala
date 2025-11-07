package com.clartat.mcp

import com.clartat.mcp.cli.CliHandler
import com.clartat.mcp.service.{McpRequestHandler, McpServerIO}
import com.clartat.mcp.tools.DefaultTools

/**
 * Main application entry point for the MCP server
 * 
 * This is a clean, minimal entry point that:
 * - Initializes the tool registry with default tools
 * - Handles CLI arguments for direct tool invocation
 * - Starts the MCP server on stdin/stdout for MCP protocol communication
 */
object McpServerApp {
  
  /**
   * Application entry point
   * 
   * @param args Command line arguments
   */
  def main(args: Array[String]): Unit = {
    // Initialize tool registry with all default tools
    val toolRegistry = DefaultTools.createRegistry()
    
    // Check if CLI mode was requested
    val isCliMode = CliHandler.handleCliArgs(args, toolRegistry)
    
    if (!isCliMode) {
      // Start server mode
      startServer(toolRegistry)
    }
  }
  
  /**
   * Starts the MCP server in server mode
   * 
   * @param toolRegistry The tool registry to use
   */
  private def startServer(toolRegistry: com.clartat.mcp.tools.ToolRegistry): Unit = {
    // Create request handler with the tool registry
    val requestHandler = McpRequestHandler(toolRegistry)
    
    // Create server I/O handler
    val serverIO = McpServerIO(requestHandler)
    
    // Start the server loop (blocks until stdin closes)
    serverIO.startServerLoop()
  }
}
