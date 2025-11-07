package com.clartat.mcp.tools.impl

import com.clartat.mcp.domain._
import com.clartat.mcp.tools.Tool
import io.circe.Json

/**
 * Add tool - Adds two integers
 * 
 * This is an example tool that demonstrates the tool implementation pattern.
 * New tools should follow this structure.
 */
class AddTool extends Tool {
  
  override val name: String = "add"
  
  override val description: String = "Adds two integers and returns their sum"
  
  override val parameters: List[ToolParameter] = List(
    ToolParameter(
      name = "a",
      description = "First number to add",
      paramType = "number",
      required = true
    ),
    ToolParameter(
      name = "b",
      description = "Second number to add",
      paramType = "number",
      required = true
    )
  )
  
  override def execute(arguments: Json): ToolResult = {
    val cursor = arguments.hcursor
    
    val result = for {
      a <- cursor.get[Int]("a").toOption
      b <- cursor.get[Int]("b").toOption
    } yield {
      val sum = a + b
      Tool.textSuccess(sum.toString)
    }
    
    result.getOrElse {
      Tool.failure("Expected integer arguments 'a' and 'b'")
    }
  }
}

/**
 * Companion object for AddTool
 */
object AddTool {
  def apply(): AddTool = new AddTool()
}
