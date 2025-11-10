package com.clartat.mcp.service

import com.clartat.mcp.domain._
import com.clartat.mcp.protocol.JsonRpcProtocol
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.io.StdIn
import java.io.{BufferedReader, InputStreamReader}

/**
 * MCP server I/O handler
 * 
 * Manages communication over stdin/stdout using the JSON-RPC protocol.
 * Supports two message formats:
 * - Simple newline-delimited JSON (for basic clients)
 * - LSP-style Content-Length headers (for advanced clients like Cursor)
 * 
 * Handles message parsing, request dispatching, and response writing.
 */
class McpServerIO(requestHandler: McpRequestHandler) {
  
  private val reader = new BufferedReader(new InputStreamReader(System.in))
  
  // Track what format the client is using (detected from first message)
  private var useContentLengthFormat: Boolean = false
  private var formatDetected: Boolean = false
  
  // Track client type for compatibility
  private var clientName: Option[String] = None
  
  /**
   * Starts the server loop, reading from stdin and writing to stdout
   * 
   * This method blocks until stdin is closed.
   */
  def startServerLoop(): Unit = {
    System.err.println("MCP Server started on stdin/stdout")
    System.err.println("Supporting both newline-delimited and Content-Length message formats")
    
    try {
      Iterator.continually(readMessage())
        .takeWhile(_.isDefined)
        .foreach { msgOpt =>
          msgOpt.foreach(processMessage)
        }
    } catch {
      case e: Exception =>
        System.err.println(s"Server error: ${e.getMessage}")
        e.printStackTrace(System.err)
    }
    
    System.err.println("MCP Server stopped")
  }
  
  /**
   * Reads a message from stdin, supporting both formats:
   * - Simple newline-delimited JSON
   * - LSP-style Content-Length headers
   * 
   * @return Optional message string
   */
  private def readMessage(): Option[String] = {
    try {
      val firstLine = reader.readLine()
      if (firstLine == null) return None
      
      // Check if it's a Content-Length header
      if (firstLine.startsWith("Content-Length:")) {
        // Detect that client uses Content-Length format
        if (!formatDetected) {
          useContentLengthFormat = true
          formatDetected = true
          System.err.println("Detected Content-Length format - will respond with same format")
        }
        readContentLengthMessage(firstLine)
      } else {
        // Simple newline-delimited JSON
        if (!formatDetected) {
          useContentLengthFormat = false
          formatDetected = true
          System.err.println("Detected newline-delimited format - will respond with same format")
        }
        if (firstLine.trim.nonEmpty) Some(firstLine) else readMessage()
      }
    } catch {
      case e: Exception =>
        System.err.println(s"Error reading message: ${e.getMessage}")
        None
    }
  }
  
  /**
   * Reads a message with Content-Length header (LSP-style)
   * 
   * @param headerLine The first header line containing Content-Length
   * @return Optional message content
   */
  private def readContentLengthMessage(headerLine: String): Option[String] = {
    try {
      // Parse Content-Length
      val length = headerLine.split(":")(1).trim.toInt
      System.err.println(s"Reading message with Content-Length: $length")
      
      // Read remaining headers until empty line
      var line = reader.readLine()
      while (line != null && line.trim.nonEmpty) {
        line = reader.readLine()
      }
      
      // Read the content with exact length
      val content = new Array[Char](length)
      var totalRead = 0
      
      while (totalRead < length) {
        val read = reader.read(content, totalRead, length - totalRead)
        if (read == -1) {
          System.err.println(s"Unexpected EOF while reading content (read $totalRead of $length bytes)")
          return None
        }
        totalRead += read
      }
      
      val message = new String(content)
      System.err.println(s"Read Content-Length message: ${message.take(100)}...")
      Some(message)
      
    } catch {
      case e: NumberFormatException =>
        System.err.println(s"Invalid Content-Length format: ${e.getMessage}")
        None
      case e: Exception =>
        System.err.println(s"Error reading Content-Length message: ${e.getMessage}")
        None
    }
  }
  
  /**
   * Processes a single message
   * 
   * @param message The message to process
   */
  private def processMessage(message: String): Unit = {
    if (message.trim.nonEmpty) {
      parseRequest(message) match {
        case Right(request) => handleRequest(request)
        case Left(error) => handleParseError(error)
      }
    }
  }
  
  /**
   * Parses a JSON-RPC request from a string
   * 
   * @param line The JSON string to parse
   * @return Either a parse error or the parsed request
   */
  private def parseRequest(line: String): Either[Error, JsonRpcRequest] = {
    decode[JsonRpcRequest](line)
  }
  
  /**
   * Handles a successfully parsed request
   * 
   * @param request The request to handle
   */
  private def handleRequest(request: JsonRpcRequest): Unit = {
    val requestType = if (request.id.isDefined) "request" else "notification"
    System.err.println(s"Received $requestType: ${request.method} (id: ${request.id.getOrElse("none")})")
    
    // Extract client info from initialize request
    if (request.method == "initialize" && clientName.isEmpty) {
      request.params.foreach { params =>
        val cursor = params.hcursor
        clientName = cursor.downField("clientInfo").downField("name").as[String].toOption
        if (clientName.isEmpty) {
          // Try alternative path
          clientName = cursor.downField("clientInfo").get[String]("name").toOption
        }
        clientName match {
          case Some(name) => 
            val lower = name.toLowerCase
            val useLegacy = lower.contains("vscode") || lower.contains("cursor")
            System.err.println(s"Detected client: $name")
            System.err.println(s"Will use legacy format (parameters): $useLegacy")
          case None =>
            System.err.println("Warning: Could not detect client name, using default format (inputSchema)")
        }
      }
    }
    
    val response = requestHandler.handleRequest(request, clientName)
    
    // Only send a response if the request had an ID (not a notification)
    if (shouldSendResponse(request, response)) {
      sendResponse(response)
    } else {
      System.err.println(s"No response sent for notification: ${request.method}")
    }
  }
  
  /**
   * Handles a parse error by sending an error response
   * 
   * @param error The parsing error
   */
  private def handleParseError(error: Error): Unit = {
    System.err.println(s"Parse error: ${error.getMessage}")
    val errorResponse = JsonRpcProtocol.parseErrorResponse(error.getMessage)
    sendResponse(errorResponse)
  }
  
  /**
   * Determines if a response should be sent
   * 
   * JSON-RPC 2.0 spec: Notifications (requests without an id) MUST NOT receive a response.
   * Only requests with an id should get a response.
   * 
   * @param request The original request
   * @param response The response to potentially send
   * @return true if the response should be sent
   */
  private def shouldSendResponse(
    request: JsonRpcRequest,
    response: JsonRpcResponse
  ): Boolean = {
    // Only send response if the request has an ID
    // Notifications (without ID) must not receive any response
    request.id.isDefined
  }
  
  /**
   * Sends a response to stdout
   * 
   * Uses the same format as the client (auto-detected from first message):
   * - Content-Length headers (LSP-style) if client uses it
   * - Simple newline-delimited JSON otherwise
   * 
   * @param response The response to send
   */
  private def sendResponse(response: JsonRpcResponse): Unit = {
    val jsonContent = response.asJson.noSpaces
    
    if (useContentLengthFormat) {
      // Send with Content-Length header (LSP-style)
      val contentBytes = jsonContent.getBytes("UTF-8")
      val contentLength = contentBytes.length
      
      // Important: Use \r\n for line endings and NO trailing newline after JSON
      print(s"Content-Length: $contentLength\r\n\r\n")
      print(jsonContent)
      System.out.flush()
      
      System.err.println(s"Sent response with Content-Length: $contentLength")
    } else {
      // Send simple newline-delimited JSON
      println(jsonContent)
      System.out.flush()
      
      System.err.println(s"Sent response (newline-delimited)")
    }
    
    System.err.println(s"Response preview: ${jsonContent.take(150)}...")
  }
}

/**
 * Companion object for creating server IO handlers
 */
object McpServerIO {
  
  /**
   * Creates a server IO handler with the given request handler
   * 
   * @param requestHandler The request handler to use
   * @return New server IO handler
   */
  def apply(requestHandler: McpRequestHandler): McpServerIO = {
    new McpServerIO(requestHandler)
  }
}
