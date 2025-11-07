package com.clartat.mcp.tools.impl

import com.clartat.mcp.domain._
import com.clartat.mcp.domain.github._
import com.clartat.mcp.client.GithubClient
import com.clartat.mcp.tools.Tool
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._

/**
 * GitHub Project tool
 * 
 * Fetches issues from a GitHub repository for analysis.
 * 
 * Security:
 * - Uses GITHUB_TOKEN from environment variables
 * - Restricted to a single project configured via GITHUB_OWNER and GITHUB_REPO env vars
 * 
 * Environment variables required:
 * - GITHUB_TOKEN: Personal Access Token with repo access
 * - GITHUB_OWNER: Repository owner (e.g., "octocat")
 * - GITHUB_REPO: Repository name (e.g., "hello-world")
 */
class GithubProjectTool extends Tool {
  
  override val name: String = "github-project"
  
  override val description: String = 
    "Fetches issues from the configured GitHub project for analysis. " +
    "The project is configured via GITHUB_OWNER and GITHUB_REPO environment variables."
  
  override val parameters: List[ToolParameter] = List(
    ToolParameter(
      name = "state",
      description = "Issue state filter: 'open', 'closed', or 'all' (default: 'open')",
      paramType = "string",
      required = false
    ),
    ToolParameter(
      name = "labels",
      description = "Optional array of label names to filter by",
      paramType = "array",
      required = false,
      items = Some(ToolParameterItems(itemType = "string"))
    ),
    ToolParameter(
      name = "per_page",
      description = "Number of items per page (max 100, default: 100)",
      paramType = "number",
      required = false
    )
  )
  
  override def execute(arguments: Json): ToolResult = {
    // Read environment variables
    val envConfig = for {
      token <- sys.env.get("GITHUB_TOKEN")
      owner <- sys.env.get("GITHUB_OWNER")
      repo <- sys.env.get("GITHUB_REPO")
    } yield (token, owner, repo)
    
    envConfig match {
      case None =>
        Tool.failure(
          "GitHub project not configured. Please set GITHUB_TOKEN, GITHUB_OWNER, " +
          "and GITHUB_REPO environment variables in your MCP configuration."
        )
        
      case Some((token, owner, repo)) =>
        executeWithConfig(token, owner, repo, arguments)
    }
  }
  
  /**
   * Executes the tool with the configured project
   * 
   * @param token GitHub token
   * @param owner Repository owner
   * @param repo Repository name
   * @param arguments Tool arguments
   * @return Tool execution result
   */
  private def executeWithConfig(
    token: String,
    owner: String,
    repo: String,
    arguments: Json
  ): ToolResult = {
    
    val cursor = arguments.hcursor
    
    // Parse optional parameters
    val state = cursor.get[String]("state").toOption.getOrElse("open")
    val labels = cursor.get[List[String]]("labels").toOption
    val perPage = cursor.get[Int]("per_page").toOption.getOrElse(100)
    
    // Validate state parameter
    if (!Set("open", "closed", "all").contains(state)) {
      return Tool.failure(s"Invalid state '$state'. Must be 'open', 'closed', or 'all'.")
    }
    
    // Validate per_page parameter
    if (perPage < 1 || perPage > 100) {
      return Tool.failure(s"Invalid per_page '$perPage'. Must be between 1 and 100.")
    }
    
    // Create GitHub client and fetch issues
    val client = GithubClient(token)
    
    System.err.println(s"Fetching issues from $owner/$repo (state: $state)...")
    
    val fetchResult: Either[String, List[GithubIssue]] = client.fetchIssues(owner, repo, state, labels, perPage)
    
    fetchResult match {
      case Right(issuesList) =>
        val result = GithubIssuesResult(
          issues = issuesList,
          summary = GithubIssuesSummary.fromIssues(issuesList)
        )
        
        System.err.println(s"Retrieved ${issuesList.size} issues from $owner/$repo")
        
        Tool.jsonSuccess(
          Json.obj(
            "content" -> Json.arr(
              Json.obj(
                "type" -> Json.fromString("text"),
                "text" -> Json.fromString(
                  s"Successfully retrieved ${issuesList.size} issues from $owner/$repo"
                )
              )
            ),
            "data" -> result.asJson
          )
        )
        
      case Left(error) =>
        System.err.println(s"GitHub API error: $error")
        Tool.failure(error)
    }
  }
}

/**
 * Companion object for GithubProjectTool
 */
object GithubProjectTool {
  def apply(): GithubProjectTool = new GithubProjectTool()
}
