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
        // Return tools as an array following MCP standard with JSON Schema inputSchema
        val addTool = Json.obj(
          "name" -> Json.fromString("add"),
          "description" -> Json.fromString("Adds two integers and returns their sum"),
          "inputSchema" -> Json.obj(
            "type" -> Json.fromString("object"),
            "properties" -> Json.obj(
              "a" -> Json.obj(
                "type" -> Json.fromString("number"),
                "description" -> Json.fromString("First number to add")
              ),
              "b" -> Json.obj(
                "type" -> Json.fromString("number"),
                "description" -> Json.fromString("Second number to add")
              )
            ),
            "required" -> Json.arr(Json.fromString("a"), Json.fromString("b"))
          )
        )
        val tools = Json.obj(
          "tools" -> Json.arr(addTool)
        )
        JsonRpcResponse(
          jsonrpc = "2.0",
          id = request.id,
          result = Some(tools),
          error = None
        )
      
      case "tools/call" =>
        // MCP standard uses "tools/call" with tool name in params
        System.err.println(s"tools/call with params: ${request.params.map(_.noSpaces).getOrElse("None")}")
        val maybeResult: Option[Json] = request.params.flatMap { p =>
          val cursor = p.hcursor
          for {
            name <- cursor.get[String]("name").toOption
            arguments <- cursor.get[Json]("arguments").toOption
            if name == "add"
            a <- arguments.hcursor.get[Int]("a").toOption
            b <- arguments.hcursor.get[Int]("b").toOption
          } yield Json.obj(
            "content" -> Json.arr(
              Json.obj(
                "type" -> Json.fromString("text"),
                "text" -> Json.fromString(s"${a + b}")
              )
            )
          )
        }
        maybeResult match {
          case Some(res) =>
            JsonRpcResponse(
              jsonrpc = "2.0",
              id = request.id,
              result = Some(res),
              error = None
            )
          case None =>
            JsonRpcResponse(
              jsonrpc = "2.0",
              id = request.id,
              result = None,
              error = Some(JsonRpcError(
                code = -32602,
                message = "Invalid params for tools/call"
              ))
            )
        }
      
      case "tools/add" =>
        // Log des params reçus pour debug
        System.err.println(s"tools/add called with params: ${request.params.map(_.noSpaces).getOrElse("None")}")
        // attend params sous la forme { "a": Int, "b": Int }
        // retourne toujours un "result" non-null que le client peut lire :
        // - succès : { isError: false, sum: Int }
        // - erreur  : { isError: true, message: String }
        val maybeResult: Option[Json] = request.params.flatMap { p =>
          val cursor = p.hcursor
          for {
            a <- cursor.get[Int]("a").toOption
            b <- cursor.get[Int]("b").toOption
          } yield Json.obj(
            "isError" -> Json.fromBoolean(false),
            "sum" -> Json.fromInt(a + b)
          )
        }
        maybeResult match {
          case Some(res) =>
            JsonRpcResponse(
              jsonrpc = "2.0",
              id = request.id,
              result = Some(res),
              error = None
            )
          case None =>
            val errMsg = "Invalid params: expected {a: Int, b: Int}"
            val res = Json.obj(
              "isError" -> Json.fromBoolean(true),
              "message" -> Json.fromString(errMsg)
            )
            JsonRpcResponse(
              jsonrpc = "2.0",
              id = request.id,
              result = Some(res),
              error = None
            )
        }
      
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
    // CLI helper: allow quick local invocation:
    // - named form : java -jar clartat-mcp.jar --add 9 8
    // - positional form : java -jar clartat-mcp.jar 9 8
    if (args.length == 2) {
      try {
        val a = args(0).toInt
        val b = args(1).toInt
        val res = Json.obj("sum" -> Json.fromInt(a + b))
        println(res.noSpaces)
        return
      } catch {
        case _: NumberFormatException =>
          // not two integers, fallthrough to normal server mode
      }
    }
    if (args.length >= 3 && args(0) == "--add") {
       try {
         val a = args(1).toInt
         val b = args(2).toInt
         val res = Json.obj("sum" -> Json.fromInt(a + b))
         println(res.noSpaces)
         return
       } catch {
         case _: NumberFormatException =>
           System.err.println("Invalid integers for --add. Usage: --add 9 8")
           return
       }
     }
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
                
                // Ne répondre que si ce n'est pas une notification OR si la réponse contient un result
                if (request.id.isDefined || response.result.isDefined) {
                  println(response.asJson.noSpaces)
                  System.out.flush()
                }
              
              case Left(error) =>
                System.err.println(s"Parse error: ${error.getMessage}")
                // Return a non-null result so clients that read result.isError don't crash
                val res = Json.obj(
                  "isError" -> Json.fromBoolean(true),
                  "message" -> Json.fromString("Parse error: " + error.getMessage)
                )
                val errorResponse = JsonRpcResponse(
                  jsonrpc = "2.0",
                  id = None,
                  result = Some(res),
                  error = None
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
