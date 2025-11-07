package com.clartat.mcp.domain

import io.circe.Json

/**
 * Represents a tool parameter definition
 * 
 * @param name Parameter name
 * @param description Parameter description
 * @param paramType Parameter type (e.g., "number", "string", "boolean", "array")
 * @param required Whether the parameter is required
 * @param items Optional specification of array item type (required when paramType is "array")
 */
case class ToolParameter(
  name: String,
  description: String,
  paramType: String,
  required: Boolean = true,
  items: Option[ToolParameterItems] = None
)

/**
 * Represents the item type specification for array parameters
 * 
 * @param itemType The type of items in the array (e.g., "string", "number")
 */
case class ToolParameterItems(
  itemType: String
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
