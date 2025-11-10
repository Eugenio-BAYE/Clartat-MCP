package com.clartat.mcp.tools.impl

import com.clartat.mcp.domain._
import com.clartat.mcp.domain.github._
import com.clartat.mcp.client.GithubGraphQLClient
import com.clartat.mcp.tools.Tool
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._

/**
 * GitHub Create Issue tool
 * 
 * Creates a new issue in the repository associated with the configured GitHub Project.
 * 
 * Security:
 * - Uses GITHUB_TOKEN from environment variables
 * - Restricted to repositories in the configured project
 * 
 * Environment variables required:
 * - GITHUB_TOKEN: Personal Access Token with repo and project access
 * - GITHUB_OWNER: Organization login
 * - GITHUB_REPO: Project number
 */
class GithubCreateIssueTool extends Tool {
  
  override val name: String = "create-github-issue"
  
  override val description: String = 
    "Creates a new GitHub issue in the repository associated with the configured project. " +
    "The issue is created with a title and optional body."
  
  override val parameters: List[ToolParameter] = List(
    ToolParameter(
      name = "title",
      paramType = "string",
      description = "Issue title (required)",
      required = true
    ),
    ToolParameter(
      name = "body",
      paramType = "string",
      description = "Issue body/description (optional)",
      required = false
    )
  )
  
  override def execute(arguments: Json): ToolResult = {
    // Extract parameters
    val titleResult = arguments.hcursor.get[String]("title")
    val bodyOpt = arguments.hcursor.get[String]("body").toOption
    
    // Validate title
    titleResult match {
      case Left(_) =>
        Tool.failure("Title is required")
        
      case Right(title) if title.trim.isEmpty =>
        Tool.failure("Title cannot be empty")
        
      case Right(title) =>
        // Read environment variables
        val envConfig = for {
          token <- sys.env.get("GITHUB_TOKEN")
          org <- sys.env.get("GITHUB_OWNER")
          projectNumberStr <- sys.env.get("GITHUB_REPO")
        } yield (token, org, projectNumberStr)
        
        envConfig match {
          case None =>
            Tool.failure(
              "GitHub project not configured. Please set GITHUB_TOKEN, GITHUB_OWNER, " +
              "and GITHUB_REPO environment variables in your MCP configuration."
            )
            
          case Some((token, org, projectNumberStr)) =>
            // Try to parse project number
            try {
              val projectNumber = projectNumberStr.toInt
              executeWithConfig(token, org, projectNumber, title, bodyOpt)
            } catch {
              case _: NumberFormatException =>
                Tool.failure(s"GITHUB_REPO must be a number (project number), got: $projectNumberStr")
            }
        }
    }
  }
  
  /**
   * Executes the tool with the configured project
   * 
   * @param token GitHub token
   * @param org Organization login
   * @param projectNumber Project number
   * @param title Issue title
   * @param body Optional issue body
   * @return Tool execution result
   */
  private def executeWithConfig(
    token: String,
    org: String,
    projectNumber: Int,
    title: String,
    body: Option[String]
  ): ToolResult = {
    
    val client = new GithubGraphQLClient(token)
    
    // Step 1: Get default repository from project
    client.getProjectRepository(org, projectNumber) match {
      case Left(error) =>
        Tool.failure(error)
        
      case Right((owner, repo)) =>
        // Step 2: Create the issue
        client.createIssue(owner, repo, title, body) match {
          case Left(error) =>
            Tool.failure(s"Failed to create issue: $error")
            
          case Right(result) =>
            formatSuccess(result, owner, repo, title, body)
        }
    }
  }
  
  /**
   * Formats the success response
   */
  private def formatSuccess(
    result: CreateIssueResult,
    owner: String,
    repo: String,
    title: String,
    body: Option[String]
  ): ToolResult = {
    
    val bodyPreview = body match {
      case Some(b) if b.length > 100 => b.take(100) + "..."
      case Some(b) => b
      case None => "_No description_"
    }
    
    val summary = s"""
      |# ✅ Issue Created Successfully
      |
      |**Repository**: $owner/$repo
      |**Issue Number**: #${result.number}
      |**Title**: $title
      |**URL**: ${result.url}
      |
      |## Description
      |
      |$bodyPreview
      |
      |## Details
      |
      |```json
      |${result.asJson.spaces2}
      |```
    """.stripMargin
    
    Tool.textSuccess(summary)
  }
}

/**
 * Companion object for GithubCreateIssueTool
 */
object GithubCreateIssueTool {
  def apply(): GithubCreateIssueTool = new GithubCreateIssueTool()
}

