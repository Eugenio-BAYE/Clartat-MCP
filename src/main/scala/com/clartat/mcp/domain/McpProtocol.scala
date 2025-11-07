package com.clartat.mcp.domain

/**
 * Model Context Protocol (MCP) domain models
 * @see https://modelcontextprotocol.io/
 */

/**
 * Result of the initialize method
 * 
 * @param protocolVersion MCP protocol version supported
 * @param capabilities Server capabilities
 * @param serverInfo Information about the server
 */
case class InitializeResult(
  protocolVersion: String,
  capabilities: Capabilities,
  serverInfo: ServerInfo
)

/**
 * Server capabilities
 * 
 * @param tools Optional tools capability
 */
case class Capabilities(
  tools: Option[ToolsCapability] = None
)

/**
 * Tools capability configuration
 * 
 * @param listChanged Whether the server supports notifying about tool list changes
 */
case class ToolsCapability(
  listChanged: Option[Boolean] = None
)

/**
 * Server information
 * 
 * @param name Server name
 * @param version Server version
 */
case class ServerInfo(
  name: String,
  version: String
)

/**
 * Server configuration constants
 */
object McpConfig {
  val ProtocolVersion: String = "2024-11-05"
  val ServerName: String = "clartat-mcp"
  val ServerVersion: String = "0.1.0"
}
