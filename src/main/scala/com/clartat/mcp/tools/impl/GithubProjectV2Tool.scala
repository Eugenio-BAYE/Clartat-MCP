package com.clartat.mcp.tools.impl

import com.clartat.mcp.domain._
import com.clartat.mcp.domain.github._
import com.clartat.mcp.client.GithubGraphQLClient
import com.clartat.mcp.tools.Tool
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._

/**
 * GitHub Project v2 tool
 * 
 * Fetches items from a GitHub Project v2 for analysis.
 * 
 * Security:
 * - Uses GITHUB_TOKEN from environment variables
 * - Restricted to a single project configured via GITHUB_ORG and PROJECT_NUMBER env vars
 * 
 * Environment variables required:
 * - GITHUB_TOKEN: Personal Access Token with project access
 * - GITHUB_ORG: Organization login (e.g., "Shopifake")
 * - PROJECT_NUMBER: Project number (e.g., "1")
 */
class GithubProjectV2Tool extends Tool {
  
  override val name: String = "github-project"
  
  override val description: String = 
    "Fetches issues from the configured GitHub project for analysis. " +
    "The project is configured via GITHUB_OWNER and GITHUB_REPO environment variables."
  
  override val parameters: List[ToolParameter] = List(
    ToolParameter(
      name = "state",
      paramType = "string",
      description = "Filter issues by state (OPEN, CLOSED, or ALL)",
      required = false
    ),
    ToolParameter(
      name = "limit",
      paramType = "number",
      description = "Maximum number of issues to return",
      required = false
    ),
    ToolParameter(
      name = "search",
      paramType = "string",
      description = "Search term to filter issues by title or body",
      required = false
    )
  )
  
  override def execute(arguments: Json): ToolResult = {
    // Extract parameters
    val stateFilter = arguments.hcursor.get[String]("state").toOption
    val limitOpt = arguments.hcursor.get[Int]("limit").toOption
    val searchTerm = arguments.hcursor.get[String]("search").toOption
    
    // Read environment variables
    val envConfig = for {
      token <- sys.env.get("GITHUB_TOKEN")
      org <- sys.env.get("GITHUB_OWNER")
      projectNumberStr <- sys.env.get("GITHUB_REPO")
    } yield (token, org, projectNumberStr)
    
    envConfig match {
      case None =>
        Tool.failure(
          "GitHub project not configured. Please set GITHUB_TOKEN, GITHUB_OWNER (org), " +
          "and GITHUB_REPO (project number) environment variables in your MCP configuration."
        )
        
      case Some((token, org, projectNumberStr)) =>
        // Try to parse project number
        try {
          val projectNumber = projectNumberStr.toInt
          executeWithConfig(token, org, projectNumber, stateFilter, limitOpt, searchTerm)
        } catch {
          case _: NumberFormatException =>
            Tool.failure(s"GITHUB_REPO must be a number (project number), got: $projectNumberStr")
        }
    }
  }
  
  /**
   * Executes the tool with the configured project
   * 
   * @param token GitHub token
   * @param org Organization login
   * @param projectNumber Project number
   * @param stateFilter Optional state filter (OPEN, CLOSED, or ALL)
   * @param limitOpt Optional limit on number of results
   * @param searchTerm Optional search term for filtering
   * @return Tool execution result
   */
  private def executeWithConfig(
    token: String,
    org: String,
    projectNumber: Int,
    stateFilter: Option[String],
    limitOpt: Option[Int],
    searchTerm: Option[String]
  ): ToolResult = {
    
    val client = new GithubGraphQLClient(token)
    
    client.fetchProjectItems(org, projectNumber) match {
      case Right(items) =>
        formatProjectItems(items, org, projectNumber, stateFilter, limitOpt, searchTerm)
        
      case Left(error) =>
        Tool.failure(s"Failed to fetch project items: $error")
    }
  }
  
  /**
   * Formats project items as a JSON result
   */
  private def formatProjectItems(
    items: List[ProjectV2Item],
    org: String,
    projectNumber: Int,
    stateFilter: Option[String],
    limitOpt: Option[Int],
    searchTerm: Option[String]
  ): ToolResult = {
    
    var issuesOnly = items.flatMap { item =>
      item.content.filter(_.typename == "Issue")
    }
    
    // Apply state filter
    issuesOnly = stateFilter match {
      case Some(state) if state.toUpperCase != "ALL" =>
        issuesOnly.filter(_.state.equalsIgnoreCase(state))
      case _ => issuesOnly
    }
    
    // Apply search filter
    issuesOnly = searchTerm match {
      case Some(term) =>
        val lowerTerm = term.toLowerCase
        issuesOnly.filter { issue =>
          issue.title.toLowerCase.contains(lowerTerm) ||
          issue.body.exists(_.toLowerCase.contains(lowerTerm))
        }
      case None => issuesOnly
    }
    
    // Apply limit
    issuesOnly = limitOpt match {
      case Some(limit) => issuesOnly.take(limit)
      case None => issuesOnly
    }
    
    val filtersApplied = List(
      stateFilter.map(s => s"state=$s"),
      limitOpt.map(l => s"limit=$l"),
      searchTerm.map(t => s"search='$t'")
    ).flatten
    
    val filtersStr = if (filtersApplied.nonEmpty) {
      s"\n**Filters Applied**: ${filtersApplied.mkString(", ")}"
    } else {
      ""
    }
    
    val summary = s"""
      |# GitHub Project Items
      |
      |**Organization**: $org
      |**Project Number**: $projectNumber
      |**Total Items**: ${items.length}
      |**Issues**: ${issuesOnly.length}$filtersStr
      |
      |## Issues
      |
      |${formatIssues(issuesOnly)}
      |
      |## All Items (JSON)
      |
      |${items.asJson.spaces2}
    """.stripMargin
    
    Tool.textSuccess(summary)
  }
  
  /**
   * Formats issues as a markdown list
   */
  private def formatIssues(issues: List[ProjectV2ItemContent]): String = {
    if (issues.isEmpty) {
      "No issues found in this project."
    } else {
      issues.map { issue =>
        s"""
          |### #${issue.number}: ${issue.title}
          |
          |**State**: ${issue.state}
          |**URL**: ${issue.url}
          |**Repository**: ${issue.repository.owner.login}/${issue.repository.name}
          |
          |${issue.body.getOrElse("_No description_")}
          |
          |---
        """.stripMargin
      }.mkString("\n")
    }
  }
}

/**
 * Companion object for GithubProjectV2Tool
 */
object GithubProjectV2Tool {
  def apply(): GithubProjectV2Tool = new GithubProjectV2Tool()
}
