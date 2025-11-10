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
    ),
    ToolParameter(
      name = "size",
      paramType = "string",
      description = "Issue size: XS, S, M, L, or XL (optional)",
      required = false
    ),
    ToolParameter(
      name = "priority",
      paramType = "string",
      description = "Issue priority: P0, P1, P2, P3, P4, or P5 (optional)",
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
    val sizeOpt = arguments.hcursor.get[String]("size").toOption
    val priorityOpt = arguments.hcursor.get[String]("priority").toOption
    
    // Debug: Log extraction results
    System.err.println(s"[create-github-issue] Title extraction: $titleResult")
    System.err.println(s"[create-github-issue] Body: $bodyOpt")
    System.err.println(s"[create-github-issue] Size: $sizeOpt")
    System.err.println(s"[create-github-issue] Priority: $priorityOpt")
    
    // Validate size
    val validSizes = Set("XS", "S", "M", "L", "XL")
    sizeOpt match {
      case Some(size) if !validSizes.contains(size.toUpperCase) =>
        return Tool.failure(s"Invalid size '$size'. Must be one of: ${validSizes.mkString(", ")}")
      case _ => // Valid or not provided
    }
    
    // Validate priority
    val validPriorities = Set("P0", "P1", "P2", "P3", "P4", "P5")
    priorityOpt match {
      case Some(priority) if !validPriorities.contains(priority.toUpperCase) =>
        return Tool.failure(s"Invalid priority '$priority'. Must be one of: ${validPriorities.mkString(", ")}")
      case _ => // Valid or not provided
    }
    
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
        
        // Normalize size and priority to uppercase
        val normalizedSize = sizeOpt.map(_.toUpperCase)
        val normalizedPriority = priorityOpt.map(_.toUpperCase)
        
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
              executeWithConfig(token, org, projectNumber, repoName, title, bodyOpt, normalizedSize, normalizedPriority)
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
   * @param size Optional size (XS, S, M, L, XL)
   * @param priority Optional priority (P0-P5)
   * @return Tool execution result
   */
  private def executeWithConfig(
    token: String,
    org: String,
    projectNumber: Int,
    repoName: Option[String],
    title: String,
    body: Option[String],
    size: Option[String],
    priority: Option[String]
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
                    
                    // Step 5: Update custom fields if provided
                    val fieldsToUpdate = List(
                      size.map(s => ("Size", s)),
                      priority.map(p => ("Priority", p))
                    ).flatten
                    
                    if (fieldsToUpdate.nonEmpty) {
                      updateCustomFields(client, org, projectNumber, projectId, projectItemId, fieldsToUpdate) match {
                        case Left(errors) =>
                          // Issue created and added to project, but fields failed
                          Tool.failure(
                            s"Issue created (#${issueResult.number}) and added to project successfully!\n" +
                            s"However, some custom fields could not be updated:\n${errors.mkString("\n")}\n" +
                            s"Issue URL: ${issueResult.url}"
                          )
                        case Right(_) =>
                          formatSuccess(issueResult, owner, repo, title, body, addedToProject = true, size, priority)
                      }
                    } else {
                      formatSuccess(issueResult, owner, repo, title, body, addedToProject = true, size, priority)
                    }
                }
            }
        }
    }
  }
  
  /**
   * Updates custom fields on a project item
   */
  private def updateCustomFields(
    client: GithubGraphQLClient,
    org: String,
    projectNumber: Int,
    projectId: String,
    projectItemId: String,
    fields: List[(String, String)]
  ): Either[List[String], Unit] = {
    
    val fieldNames = fields.map(_._1)
    
    // Get field IDs
    client.getProjectFieldIds(org, projectNumber, fieldNames) match {
      case Left(error) =>
        Left(List(s"Failed to get project field IDs: $error"))
        
      case Right(fieldIds) =>
        val errors = fields.flatMap { case (fieldName, fieldValue) =>
          fieldIds.get(fieldName) match {
            case None =>
              Some(s"Field '$fieldName' not found in project")
            case Some(fieldId) =>
              client.updateProjectItemField(projectId, projectItemId, fieldId, fieldValue) match {
                case Left(error) => Some(s"Failed to update $fieldName: $error")
                case Right(_) => None
              }
          }
        }
        
        if (errors.isEmpty) Right(()) else Left(errors)
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
    addedToProject: Boolean = false,
    size: Option[String] = None,
    priority: Option[String] = None
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
    
    val customFieldsInfo = {
      val fields = List(
        size.map(s => s"**Size**: $s"),
        priority.map(p => s"**Priority**: $p")
      ).flatten
      
      if (fields.nonEmpty) {
        "\n" + fields.mkString("\n")
      } else {
        ""
      }
    }
    
    val summary = s"""
      |# ✅ Issue Created Successfully
      |
      |**Repository**: $owner/$repo
      |**Issue Number**: #${result.number}
      |**Title**: $title
      |**URL**: ${result.url}
      |$projectStatus$customFieldsInfo
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

