import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*

import scala.io.StdIn
import scala.util.{Try, Success, Failure}

case class JsonRpcRequest(
  jsonrpc: String,
  id: Option[Json],
  method: String,
  params: Option[Json]
)

case class JsonRpcResponse(
  jsonrpc: String,
  id: Option[Json],
  result: Option[Json],
  error: Option[JsonRpcError]
)

case class JsonRpcError(
  code: Int,
  message: String,
  data: Option[Json] = None
)

case class InitializeResult(
  protocolVersion: String,
  capabilities: Capabilities,
  serverInfo: ServerInfo
)

case class Capabilities(
  tools: Option[ToolsCapability] = None
)

case class ToolsCapability(
  listChanged: Option[Boolean] = None
)

case class ServerInfo(
  name: String,
  version: String
)

object McpServer {
  
  def handleRequest(request: JsonRpcRequest): JsonRpcResponse = {
    request.method match {
      case "initialize" =>
        val result = InitializeResult(
          protocolVersion = "2024-11-05",
          capabilities = Capabilities(
            tools = Some(ToolsCapability(listChanged = Some(false)))
          ),
          serverInfo = ServerInfo(
            name = "clartat-mcp",
            version = "0.1.0"
          )
        )
        JsonRpcResponse(
          jsonrpc = "2.0",
          id = request.id,
          result = Some(result.asJson),
          error = None
        )
      
      case "tools/list" =>
        val tools = Json.obj(
          "tools" -> Json.arr()
        )
        JsonRpcResponse(
          jsonrpc = "2.0",
          id = request.id,
          result = Some(tools),
          error = None
        )
      
      case "notifications/initialized" =>
        // Pas de réponse pour les notifications
        JsonRpcResponse(
          jsonrpc = "2.0",
          id = None,
          result = None,
          error = None
        )
      
      case _ =>
        JsonRpcResponse(
          jsonrpc = "2.0",
          id = request.id,
          result = None,
          error = Some(JsonRpcError(
            code = -32601,
            message = s"Method not found: ${request.method}"
          ))
        )
    }
  }

  def main(args: Array[String]): Unit = {
    System.err.println("MCP Server started on stdin/stdout")
    
    try {
      Iterator.continually(StdIn.readLine())
        .takeWhile(_ != null)
        .foreach { line =>
          if (line.trim.nonEmpty) {
            decode[JsonRpcRequest](line) match {
              case Right(request) =>
                System.err.println(s"Received request: ${request.method}")
                val response = handleRequest(request)
                
                // Ne répondre que si ce n'est pas une notification
                if (request.id.isDefined || response.id.isDefined) {
                  println(response.asJson.noSpaces)
                  System.out.flush()
                }
              
              case Left(error) =>
                System.err.println(s"Parse error: ${error.getMessage}")
                val errorResponse = JsonRpcResponse(
                  jsonrpc = "2.0",
                  id = None,
                  result = None,
                  error = Some(JsonRpcError(
                    code = -32700,
                    message = "Parse error"
                  ))
                )
                println(errorResponse.asJson.noSpaces)
                System.out.flush()
            }
          }
        }
    } catch {
      case e: Exception =>
        System.err.println(s"Server error: ${e.getMessage}")
        e.printStackTrace(System.err)
    }
    
    System.err.println("MCP Server stopped")
  }
}
