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
  
  override val parameters: List[ToolParameter] = List()
  
  override def execute(arguments: Json): ToolResult = {
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
          executeWithConfig(token, org, projectNumber)
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
   * @return Tool execution result
   */
  private def executeWithConfig(
    token: String,
    org: String,
    projectNumber: Int
  ): ToolResult = {
    
    val client = new GithubGraphQLClient(token)
    
    client.fetchProjectItems(org, projectNumber) match {
      case Right(items) =>
        formatProjectItems(items, org, projectNumber)
        
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
    projectNumber: Int
  ): ToolResult = {
    
    val issuesOnly = items.flatMap { item =>
      item.content.filter(_.typename == "Issue")
    }
    
    val summary = s"""
      |# GitHub Project Items
      |
      |**Organization**: $org
      |**Project Number**: $projectNumber
      |**Total Items**: ${items.length}
      |**Issues**: ${issuesOnly.length}
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
