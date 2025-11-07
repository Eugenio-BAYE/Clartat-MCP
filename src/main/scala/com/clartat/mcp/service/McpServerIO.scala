package com.clartat.mcp.service

import com.clartat.mcp.domain._
import com.clartat.mcp.protocol.JsonRpcProtocol
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.io.StdIn

/**
 * MCP server I/O handler
 * 
 * Manages communication over stdin/stdout using the JSON-RPC protocol.
 * Handles message parsing, request dispatching, and response writing.
 */
class McpServerIO(requestHandler: McpRequestHandler) {
  
  /**
   * Starts the server loop, reading from stdin and writing to stdout
   * 
   * This method blocks until stdin is closed.
   */
  def startServerLoop(): Unit = {
    System.err.println("MCP Server started on stdin/stdout")
    
    try {
      Iterator.continually(StdIn.readLine())
        .takeWhile(_ != null)
        .foreach(processLine)
    } catch {
      case e: Exception =>
        System.err.println(s"Server error: ${e.getMessage}")
        e.printStackTrace(System.err)
    }
    
    System.err.println("MCP Server stopped")
  }
  
  /**
   * Processes a single line of input
   * 
   * @param line The input line to process
   */
  private def processLine(line: String): Unit = {
    if (line.trim.nonEmpty) {
      parseRequest(line) match {
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
    System.err.println(s"Received request: ${request.method}")
    
    val response = requestHandler.handleRequest(request)
    
    // Only send a response if the request had an ID (not a notification)
    // or if the response contains a result/error
    if (shouldSendResponse(request, response)) {
      sendResponse(response)
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
   * @param request The original request
   * @param response The response to potentially send
   * @return true if the response should be sent
   */
  private def shouldSendResponse(
    request: JsonRpcRequest,
    response: JsonRpcResponse
  ): Boolean = {
    request.id.isDefined || response.result.isDefined || response.error.isDefined
  }
  
  /**
   * Sends a response to stdout
   * 
   * @param response The response to send
   */
  private def sendResponse(response: JsonRpcResponse): Unit = {
    println(response.asJson.noSpaces)
    System.out.flush()
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
