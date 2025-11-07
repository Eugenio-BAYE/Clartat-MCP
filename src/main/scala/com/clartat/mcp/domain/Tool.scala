package com.clartat.mcp.domain

import io.circe.Json

/**
 * Represents a tool parameter definition
 * 
 * @param name Parameter name
 * @param description Parameter description
 * @param paramType Parameter type (e.g., "number", "string", "boolean")
 * @param required Whether the parameter is required
 */
case class ToolParameter(
  name: String,
  description: String,
  paramType: String,
  required: Boolean = true
)

/**
 * Represents the result of a tool execution
 */
sealed trait ToolResult

/**
 * Successful tool execution result
 * 
 * @param content The result content as JSON
 */
case class ToolSuccess(content: Json) extends ToolResult

/**
 * Failed tool execution result
 * 
 * @param errorMessage Description of the error
 * @param errorCode Optional error code
 */
case class ToolFailure(
  errorMessage: String,
  errorCode: Option[Int] = None
) extends ToolResult
