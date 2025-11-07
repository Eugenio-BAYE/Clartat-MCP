package com.clartat.mcp.cli

import com.clartat.mcp.tools.{DefaultTools, ToolRegistry}
import io.circe.Json

/**
 * Command-line interface for the MCP server
 * 
 * Provides a way to invoke tools directly from the command line
 * for testing and quick operations.
 */
object CliHandler {
  
  /**
   * Handles CLI arguments and executes the appropriate action
   * 
   * Supports two formats:
   * - Named form: --add 9 8
   * - Positional form: 9 8 (assumes "add" tool)
   * 
   * @param args Command line arguments
   * @param toolRegistry The tool registry to use
   * @return true if CLI mode was handled, false if server mode should start
   */
  def handleCliArgs(args: Array[String], toolRegistry: ToolRegistry): Boolean = {
    if (args.isEmpty) {
      return false // Start server mode
    }
    
    // Handle named form: --add 9 8
    if (args.length >= 3 && args(0).startsWith("--")) {
      val toolName = args(0).substring(2) // Remove "--" prefix
      val toolArgs = args.drop(1)
      executeToolCli(toolName, toolArgs, toolRegistry)
      return true
    }
    
    // Handle positional form: 9 8 (assumes "add" tool)
    if (args.length == 2) {
      executeToolCli("add", args, toolRegistry)
      return true
    }
    
    // Invalid arguments, print usage
    printUsage()
    false
  }
  
  /**
   * Executes a tool from CLI arguments
   * 
   * @param toolName Name of the tool to execute
   * @param args Tool arguments
   * @param toolRegistry The tool registry
   */
  private def executeToolCli(
    toolName: String,
    args: Array[String],
    toolRegistry: ToolRegistry
  ): Unit = {
    toolRegistry.getTool(toolName) match {
      case Some(tool) =>
        // For the "add" tool, we expect two integer arguments
        if (toolName == "add" && args.length == 2) {
          try {
            val a = args(0).toInt
            val b = args(1).toInt
            val arguments = Json.obj(
              "a" -> Json.fromInt(a),
              "b" -> Json.fromInt(b)
            )
            val result = tool.execute(arguments)
            println(formatCliResult(result))
          } catch {
            case _: NumberFormatException =>
              System.err.println(s"Error: Invalid integers for $toolName")
              printUsage()
          }
        } else {
          System.err.println(s"Error: Invalid arguments for tool '$toolName'")
          printUsage()
        }
      case None =>
        System.err.println(s"Error: Unknown tool '$toolName'")
        printAvailableTools(toolRegistry)
    }
  }
  
  /**
   * Formats a tool result for CLI output
   * 
   * @param result The tool result
   * @return Formatted string
   */
  private def formatCliResult(result: com.clartat.mcp.domain.ToolResult): String = {
    result match {
      case com.clartat.mcp.domain.ToolSuccess(content) =>
        // Extract text from MCP format
        val cursor = content.hcursor
        cursor.downField("content").downArray.downField("text").as[String]
          .getOrElse(content.noSpaces)
      case com.clartat.mcp.domain.ToolFailure(message, _) =>
        s"Error: $message"
    }
  }
  
  /**
   * Prints usage information
   */
  private def printUsage(): Unit = {
    System.err.println("""
      |Usage:
      |  Server mode:
      |    java -jar clartat-mcp.jar
      |
      |  CLI mode (add tool):
      |    java -jar clartat-mcp.jar --add <a> <b>
      |    java -jar clartat-mcp.jar <a> <b>
      |
      |Examples:
      |  java -jar clartat-mcp.jar --add 9 8
      |  java -jar clartat-mcp.jar 9 8
      |""".stripMargin)
  }
  
  /**
   * Prints available tools
   * 
   * @param toolRegistry The tool registry
   */
  private def printAvailableTools(toolRegistry: ToolRegistry): Unit = {
    System.err.println("\nAvailable tools:")
    toolRegistry.listTools().foreach { tool =>
      System.err.println(s"  --${tool.name}: ${tool.description}")
    }
  }
}
