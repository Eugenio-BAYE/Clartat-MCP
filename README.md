# Clartat MCP Server

A clean, extensible Model Context Protocol (MCP) server implementation in Scala 3.

## Project Structure
 
```
src/main/scala/com/clartat/mcp/
├── McpServerApp.scala              # Main application entry point
├── cli/
│   └── CliHandler.scala           # Command-line interface handler
├── domain/
│   ├── JsonRpc.scala              # JSON-RPC 2.0 domain models
│   ├── McpProtocol.scala          # MCP protocol models
│   └── Tool.scala                 # Tool domain models
├── protocol/
│   ├── JsonRpcProtocol.scala      # JSON-RPC utilities
│   └── McpProtocol.scala          # MCP protocol utilities
├── service/
│   ├── McpRequestHandler.scala    # Request handling logic
│   └── McpServerIO.scala          # I/O handling (stdin/stdout)
└── tools/
    ├── Tool.scala                 # Base Tool trait
    ├── ToolRegistry.scala         # Tool registry and management
    ├── DefaultTools.scala         # Default tool configuration
    └── impl/
        └── AddTool.scala          # Example tool implementation
```

## Architecture

This project follows clean architecture principles with clear separation of concerns:

### Domain Layer (`domain/`)
- **Pure domain models** with no external dependencies
- `JsonRpc.scala`: JSON-RPC 2.0 request/response models
- `McpProtocol.scala`: MCP-specific protocol models
- `Tool.scala`: Tool execution results and parameters

### Protocol Layer (`protocol/`)
- **Protocol handling utilities** for creating responses
- `JsonRpcProtocol.scala`: JSON-RPC response builders, error codes
- `McpProtocol.scala`: MCP format builders (tool schemas, results)

### Tools Layer (`tools/`)
- **Extensible tool system** with registry pattern
- `Tool.scala`: Base trait for all tools
- `ToolRegistry.scala`: Central registry for tool management
- `DefaultTools.scala`: Default tool configuration
- `impl/`: Tool implementations

### Service Layer (`service/`)
- **Business logic and orchestration**
- `McpRequestHandler.scala`: Routes requests to appropriate handlers
- `McpServerIO.scala`: Manages stdin/stdout communication

### CLI Layer (`cli/`)
- **Command-line interface** for direct tool invocation
- `CliHandler.scala`: Handles CLI arguments and execution

### Application Layer
- `McpServerApp.scala`: Main entry point, wires everything together

## Building

```bash
# Compile
sbt compile

# Create fat JAR
sbt assembly
```

The JAR will be created at `target/scala-3.7.3/clartat-mcp.jar`

## Usage

### Server Mode (MCP Protocol)

```json
{
	"servers": {
        "clartat-mcp": {
        "command": "java",
        "args": [
            "-jar",
            "{PathToYouJar}/clartat-mcp.jar"
        ],
        "env": {}
        }
    }
}
```

The server communicates via stdin/stdout using the JSON-RPC 2.0 protocol.

### CLI Mode (Direct Tool Invocation)

```bash
# Named form
java -jar target/scala-3.7.3/clartat-mcp.jar --add 9 8

# Positional form (assumes "add" tool)
java -jar target/scala-3.7.3/clartat-mcp.jar 9 8
```

## Adding a New Tool

Adding a new tool is simple and requires only 3 steps:

### Step 1: Create the Tool Class

Create a new file in `src/main/scala/com/clartat/mcp/tools/impl/`:

```scala
package com.clartat.mcp.tools.impl

import com.clartat.mcp.domain._
import com.clartat.mcp.tools.Tool
import io.circe.Json

/**
 * Multiply tool - Multiplies two integers
 */
class MultiplyTool extends Tool {
  
  override val name: String = "multiply"
  
  override val description: String = "Multiplies two integers and returns their product"
  
  override val parameters: List[ToolParameter] = List(
    ToolParameter(
      name = "a",
      description = "First number",
      paramType = "number",
      required = true
    ),
    ToolParameter(
      name = "b",
      description = "Second number",
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
      val product = a * b
      Tool.textSuccess(product.toString)
    }
    
    result.getOrElse {
      Tool.failure("Expected integer arguments 'a' and 'b'")
    }
  }
}

object MultiplyTool {
  def apply(): MultiplyTool = new MultiplyTool()
}
```

### Step 2: Register the Tool

Add your tool to `src/main/scala/com/clartat/mcp/tools/DefaultTools.scala`:

```scala
val defaultTools: List[Tool] = List(
  AddTool(),
  MultiplyTool()  // Add your new tool here!
)
```

### Step 3: Done!


## Tool Implementation Guide

### Tool Trait Methods

```scala
trait Tool {
  def name: String                        // Unique tool identifier
  def description: String                 // Human-readable description
  def parameters: List[ToolParameter]     // Parameter definitions
  def execute(arguments: Json): ToolResult // Execution logic
}
```

### Parameter Types

```scala
ToolParameter(
  name = "param_name",
  description = "What this parameter does",
  paramType = "number" | "string" | "boolean" | "object" | "array",
  required = true | false
)
```

### Return Results

```scala
// Success with text
Tool.textSuccess("Result text")

// Success with JSON
Tool.jsonSuccess(Json.obj("key" -> Json.fromString("value")))

// Failure
Tool.failure("Error message")
```

## MCP Protocol Methods

The server implements the following MCP methods:

- `initialize`: Returns server capabilities and version
- `tools/list`: Lists all available tools
- `tools/call`: Executes a tool with arguments
- `notifications/initialized`: Handles initialization notification
