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
    "Requires a 'title' parameter (string) and accepts an optional 'body' parameter (string). " +
    "Example: {\"title\": \"Fix login bug\", \"body\": \"Users cannot log in with OAuth\"}"
  
  override val parameters: List[ToolParameter] = List(
    ToolParameter(
      name = "title",
      paramType = "string",
      description = "Issue title (required)",
      required = false  // Set to false to bypass validation, we'll check manually
    ),
    ToolParameter(
      name = "body",
      paramType = "string",
      description = "Issue body/description (optional)",
      required = false
    )
  )
  
  override def execute(arguments: Json): ToolResult = {
    // Debug: Log received arguments
    System.err.println(s"[create-github-issue] Received arguments: ${arguments.noSpaces}")
    
    // Handle case where arguments might be empty object or null
    if (arguments.isNull || arguments.asObject.exists(_.isEmpty)) {
      return Tool.failure(
        "No arguments provided. This tool requires a 'title' parameter.\n\n" +
        "Note: There may be a compatibility issue with how Cursor passes arguments to this tool.\n" +
        "You can test the tool manually with this command:\n\n" +
        "echo '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"create-github-issue\",\"arguments\":{\"title\":\"Test Issue\",\"body\":\"Test body\"}}}' | java -jar clartat-mcp.jar"
      )
    }
    
    // Extract parameters
    val titleResult = arguments.hcursor.get[String]("title")
    val bodyOpt = arguments.hcursor.get[String]("body").toOption
    
    // Debug: Log extraction results
    System.err.println(s"[create-github-issue] Title extraction: $titleResult")
    System.err.println(s"[create-github-issue] Body: $bodyOpt")
    
    // Validate title
    titleResult match {
      case Left(error) =>
        Tool.failure(s"Title is required. Error: ${error.getMessage}")
        
      case Right(title) if title.trim.isEmpty =>
        Tool.failure("Title cannot be empty")
        
      case Right(title) =>
        // Read environment variables
        val envConfig = for {
          token <- sys.env.get("GITHUB_TOKEN")
          org <- sys.env.get("GITHUB_OWNER")
          projectNumberStr <- sys.env.get("GITHUB_REPO")
        } yield (token, org, projectNumberStr)
        
        // Optional: explicit repository name
        val repoName = sys.env.get("GITHUB_REPO_NAME")
        
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
              executeWithConfig(token, org, projectNumber, repoName, title, bodyOpt)
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
   * @param repoName Optional explicit repository name
   * @param title Issue title
   * @param body Optional issue body
   * @return Tool execution result
   */
  private def executeWithConfig(
    token: String,
    org: String,
    projectNumber: Int,
    repoName: Option[String],
    title: String,
    body: Option[String]
  ): ToolResult = {
    
    val client = new GithubGraphQLClient(token)
    
    // Step 1: Get repository (explicit or auto-detect)
    client.getProjectRepository(org, projectNumber, repoName) match {
      case Left(error) =>
        Tool.failure(error)
        
      case Right((owner, repo)) =>
        // Step 2: Create the issue
        client.createIssue(owner, repo, title, body) match {
          case Left(error) =>
            Tool.failure(s"Failed to create issue: $error")
            
          case Right(issueResult) =>
            // Step 3: Get project ID
            client.getProjectId(org, projectNumber) match {
              case Left(error) =>
                Tool.failure(
                  s"Issue created successfully (#${issueResult.number}) but failed to add to project: $error\n" +
                  s"Issue URL: ${issueResult.url}"
                )
                
              case Right(projectId) =>
                // Step 4: Add issue to project
                client.addIssueToProject(projectId, issueResult.id) match {
                  case Left(error) =>
                    Tool.failure(
                      s"Issue created successfully (#${issueResult.number}) but failed to add to project: $error\n" +
                      s"Issue URL: ${issueResult.url}\n" +
                      s"You can add it manually to the project."
                    )
                    
                  case Right(projectItemId) =>
                    System.err.println(s"Successfully added issue to project (item ID: $projectItemId)")
                    formatSuccess(issueResult, owner, repo, title, body, addedToProject = true)
                }
            }
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
    body: Option[String],
    addedToProject: Boolean = false
  ): ToolResult = {
    
    val bodyPreview = body match {
      case Some(b) if b.length > 100 => b.take(100) + "..."
      case Some(b) => b
      case None => "_No description_"
    }
    
    val projectStatus = if (addedToProject) {
      "✅ **Added to Project**"
    } else {
      "⚠️ Not added to project"
    }
    
    val summary = s"""
      |# ✅ Issue Created Successfully
      |
      |**Repository**: $owner/$repo
      |**Issue Number**: #${result.number}
      |**Title**: $title
      |**URL**: ${result.url}
      |$projectStatus
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

