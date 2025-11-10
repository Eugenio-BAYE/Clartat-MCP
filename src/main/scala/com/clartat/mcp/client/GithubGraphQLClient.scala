package com.clartat.mcp.client

import com.clartat.mcp.domain.github._
import sttp.client3._
import io.circe._, io.circe.parser._, io.circe.syntax._

/**
 * GitHub GraphQL API client
 * 
 * Provides methods to interact with GitHub GraphQL API for Projects v2
 */
class GithubGraphQLClient(token: String) {
  
  private val backend = HttpURLConnectionBackend()
  private val graphqlUrl = "https://api.github.com/graphql"
  
  /**
   * Fetches items from a GitHub Project v2
   * 
   * @param ownerLogin Organization or user login (e.g., "Shopifake" or "Eugenio-BAYE")
   * @param projectNumber Project number (e.g., 1)
   * @return Either an error message or the list of project items
   */
  def fetchProjectItems(
    ownerLogin: String,
    projectNumber: Int
  ): Either[String, List[ProjectV2Item]] = {
    
    // Try organization first
    fetchProjectItemsWithOwnerType(ownerLogin, projectNumber, "organization") match {
      case Right(items) => Right(items)
      case Left(error) if error.contains("NOT_FOUND") || error.contains("Could not resolve to an Organization") =>
        // Fallback to user
        System.err.println(s"Not an organization, trying as user...")
        fetchProjectItemsWithOwnerType(ownerLogin, projectNumber, "user")
      case Left(error) => Left(error)
    }
  }
  
  /**
   * Fetches items from a GitHub Project v2 with a specific owner type
   * 
   * @param ownerLogin Organization or user login
   * @param projectNumber Project number
   * @param ownerType Either "organization" or "user"
   * @return Either an error message or the list of project items
   */
  private def fetchProjectItemsWithOwnerType(
    ownerLogin: String,
    projectNumber: Int,
    ownerType: String
  ): Either[String, List[ProjectV2Item]] = {
    
    val query = s"""
      query {
        $ownerType(login: "$ownerLogin") {
          projectV2(number: $projectNumber) {
            items(first: 100) {
              nodes {
                id
                content {
                  __typename
                  ... on Issue {
                    number
                    title
                    url
                    state
                    body
                    repository {
                      name
                      owner {
                        login
                      }
                    }
                  }
                  ... on PullRequest {
                    number
                    title
                    url
                    state
                    body
                    repository {
                      name
                      owner {
                        login
                      }
                    }
                  }
                }
                fieldValues(first: 20) {
                  nodes {
                    __typename
                    ... on ProjectV2ItemFieldTextValue {
                      text
                      field {
                        ... on ProjectV2Field {
                          name
                        }
                      }
                    }
                    ... on ProjectV2ItemFieldNumberValue {
                      number
                      field {
                        ... on ProjectV2Field {
                          name
                        }
                      }
                    }
                    ... on ProjectV2ItemFieldDateValue {
                      date
                      field {
                        ... on ProjectV2Field {
                          name
                        }
                      }
                    }
                    ... on ProjectV2ItemFieldSingleSelectValue {
                      name
                      field {
                        ... on ProjectV2SingleSelectField {
                          name
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    """
    
    val requestBody = Json.obj(
      "query" -> Json.fromString(query)
    )
    
    val request = basicRequest
      .post(uri"$graphqlUrl")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .body(requestBody.noSpaces)
    
    try {
      val response = request.send(backend)
      
      response.body match {
        case Right(body) =>
          parseProjectItemsResponse(body, ownerType)
        case Left(error) =>
          Left(s"HTTP error: $error")
      }
    } catch {
      case e: Exception =>
        Left(s"Request failed: ${e.getMessage}")
    }
  }
  
  /**
   * Parses the GraphQL response to extract project items
   * 
   * @param body The response body
   * @param ownerType Either "organization" or "user"
   */
  private def parseProjectItemsResponse(body: String, ownerType: String): Either[String, List[ProjectV2Item]] = {
    parse(body) match {
      case Right(json) =>
        val cursor = json.hcursor
        
        // Check for GraphQL errors
        cursor.downField("errors").focus match {
          case Some(errors) =>
            return Left(s"GraphQL error: ${errors.noSpaces}")
          case None => // No errors, continue
        }
        
        // Extract items (using the correct owner type field)
        val itemsResult = cursor
          .downField("data")
          .downField(ownerType)
          .downField("projectV2")
          .downField("items")
          .downField("nodes")
          .as[List[Json]]
        
        itemsResult match {
          case Right(itemsJson) =>
            val items = itemsJson.map(parseProjectItem)
            Right(items)
          case Left(error) =>
            Left(s"Failed to parse project items: ${error.getMessage}")
        }
        
      case Left(error) =>
        Left(s"Failed to parse JSON response: ${error.getMessage}")
    }
  }
  
  /**
   * Parses a single project item from JSON
   */
  private def parseProjectItem(json: Json): ProjectV2Item = {
    val cursor = json.hcursor
    
    val id = cursor.get[String]("id").getOrElse("")
    
    val content = cursor.downField("content").focus.flatMap { contentJson =>
      val contentCursor = contentJson.hcursor
      
      for {
        typename <- contentCursor.get[String]("__typename").toOption
        number <- contentCursor.get[Int]("number").toOption
        title <- contentCursor.get[String]("title").toOption
        url <- contentCursor.get[String]("url").toOption
        state <- contentCursor.get[String]("state").toOption
        body = contentCursor.get[String]("body").toOption
        repoName <- contentCursor.downField("repository").get[String]("name").toOption
        ownerLogin <- contentCursor.downField("repository").downField("owner").get[String]("login").toOption
      } yield ProjectV2ItemContent(
        typename = typename,
        number = number,
        title = title,
        url = url,
        state = state,
        body = body,
        repository = ProjectV2Repository(
          name = repoName,
          owner = ProjectV2RepositoryOwner(login = ownerLogin)
        )
      )
    }
    
    val fieldValues = cursor.downField("fieldValues").downField("nodes").as[List[Json]]
      .getOrElse(List.empty)
      .map(parseFieldValue)
    
    ProjectV2Item(
      id = id,
      content = content,
      fieldValues = fieldValues
    )
  }
  
  /**
   * Parses a field value from JSON
   */
  private def parseFieldValue(json: Json): ProjectV2FieldValue = {
    val cursor = json.hcursor
    
    val typename = cursor.get[String]("__typename").getOrElse("")
    val fieldName = cursor.downField("field").get[String]("name").toOption
    val name = cursor.get[String]("name").toOption
    val text = cursor.get[String]("text").toOption
    val number = cursor.get[Double]("number").toOption
    val date = cursor.get[String]("date").toOption
    
    ProjectV2FieldValue(
      typename = typename,
      name = name,
      field = ProjectV2Field(name = fieldName.getOrElse("unknown")),
      text = text,
      number = number,
      date = date
    )
  }
  
  /**
   * Gets the default repository from project items
   * 
   * @param ownerLogin Organization or user login
   * @param projectNumber Project number
   * @param repoNameHint Optional repository name hint from environment
   * @return Either an error message or a tuple of (owner, repo)
   */
  def getProjectRepository(
    ownerLogin: String,
    projectNumber: Int,
    repoNameHint: Option[String] = None
  ): Either[String, (String, String)] = {
    
    // Strategy 1: Use explicit repo name if provided
    repoNameHint match {
      case Some(repoName) =>
        System.err.println(s"Using explicit repository: $ownerLogin/$repoName")
        return Right((ownerLogin, repoName))
      case None =>
        System.err.println("No explicit repository name, trying to detect from project items...")
    }
    
    // Strategy 2: Auto-detect from project items
    fetchProjectItems(ownerLogin, projectNumber) match {
      case Right(items) =>
        // Find first issue with a repository
        items.flatMap(_.content).find(_.typename == "Issue") match {
          case Some(issue) =>
            System.err.println(s"Detected repository from project: ${issue.repository.owner.login}/${issue.repository.name}")
            Right((issue.repository.owner.login, issue.repository.name))
          case None =>
            Left(
              "No repository found. Please either:\n" +
              "1. Add GITHUB_REPO_NAME to your MCP config (recommended), or\n" +
              "2. Add at least one issue to your project manually first"
            )
        }
      case Left(error) =>
        Left(s"Failed to fetch project items: $error")
    }
  }
  
  /**
   * Creates a GitHub issue
   * 
   * @param owner Repository owner (organization or user)
   * @param repo Repository name
   * @param title Issue title
   * @param body Optional issue body
   * @return Either an error message or the created issue details
   */
  def createIssue(
    owner: String,
    repo: String,
    title: String,
    body: Option[String]
  ): Either[String, CreateIssueResult] = {
    
    // Get repository ID first
    getRepositoryId(owner, repo) match {
      case Left(error) => Left(error)
      case Right(repositoryId) =>
        
        val bodyValue = body.getOrElse("")
        val query = s"""
          mutation {
            createIssue(input: {
              repositoryId: "$repositoryId",
              title: ${escapeGraphQLString(title)},
              body: ${escapeGraphQLString(bodyValue)}
            }) {
              issue {
                id
                number
                url
              }
            }
          }
        """
        
        val requestBody = Json.obj(
          "query" -> Json.fromString(query)
        )
        
        val request = basicRequest
          .post(uri"$graphqlUrl")
          .header("Authorization", s"Bearer $token")
          .header("Content-Type", "application/json")
          .body(requestBody.noSpaces)
        
        try {
          val response = request.send(backend)
          
          response.body match {
            case Right(responseBody) =>
              parseCreateIssueResponse(responseBody)
            case Left(error) =>
              Left(s"HTTP error: $error")
          }
        } catch {
          case e: Exception =>
            Left(s"Request failed: ${e.getMessage}")
        }
    }
  }
  
  /**
   * Gets the repository node ID
   */
  private def getRepositoryId(owner: String, repo: String): Either[String, String] = {
    val query = s"""
      query {
        repository(owner: "$owner", name: "$repo") {
          id
        }
      }
    """
    
    val requestBody = Json.obj(
      "query" -> Json.fromString(query)
    )
    
    val request = basicRequest
      .post(uri"$graphqlUrl")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .body(requestBody.noSpaces)
    
    try {
      val response = request.send(backend)
      
      response.body match {
        case Right(body) =>
          parse(body) match {
            case Right(json) =>
              val cursor = json.hcursor
              
              // Check for GraphQL errors
              cursor.downField("errors").focus match {
                case Some(errors) =>
                  return Left(s"GraphQL error: ${errors.noSpaces}")
                case None => // No errors, continue
              }
              
              cursor.downField("data").downField("repository").get[String]("id") match {
                case Right(id) => Right(id)
                case Left(_) => Left(s"Repository not found: $owner/$repo")
              }
              
            case Left(error) =>
              Left(s"Failed to parse JSON response: ${error.getMessage}")
          }
        case Left(error) =>
          Left(s"HTTP error: $error")
      }
    } catch {
      case e: Exception =>
        Left(s"Request failed: ${e.getMessage}")
    }
  }
  
  /**
   * Parses the createIssue mutation response
   */
  private def parseCreateIssueResponse(body: String): Either[String, CreateIssueResult] = {
    parse(body) match {
      case Right(json) =>
        val cursor = json.hcursor
        
        // Check for GraphQL errors
        cursor.downField("errors").focus match {
          case Some(errors) =>
            return Left(s"GraphQL error: ${errors.noSpaces}")
          case None => // No errors, continue
        }
        
        // Extract issue data
        val issueCursor = cursor
          .downField("data")
          .downField("createIssue")
          .downField("issue")
        
        val result = for {
          id <- issueCursor.get[String]("id")
          number <- issueCursor.get[Int]("number")
          url <- issueCursor.get[String]("url")
        } yield CreateIssueResult(id, number, url)
        
        result.left.map(err => s"Failed to parse issue data: ${err.getMessage}")
        
      case Left(error) =>
        Left(s"Failed to parse JSON response: ${error.getMessage}")
    }
  }
  
  /**
   * Escapes a string for use in GraphQL
   */
  private def escapeGraphQLString(s: String): String = {
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    s""""$escaped""""
  }
  
  /**
   * Gets the project node ID
   * 
   * @param ownerLogin Organization or user login
   * @param projectNumber Project number
   * @return Either an error message or the project ID
   */
  def getProjectId(
    ownerLogin: String,
    projectNumber: Int
  ): Either[String, String] = {
    
    // Try organization first
    getProjectIdWithOwnerType(ownerLogin, projectNumber, "organization") match {
      case Right(id) => Right(id)
      case Left(error) if error.contains("NOT_FOUND") || error.contains("Could not resolve to an Organization") =>
        // Fallback to user
        System.err.println(s"Not an organization, trying as user for project ID...")
        getProjectIdWithOwnerType(ownerLogin, projectNumber, "user")
      case Left(error) => Left(error)
    }
  }
  
  /**
   * Gets the project node ID with a specific owner type
   */
  private def getProjectIdWithOwnerType(
    ownerLogin: String,
    projectNumber: Int,
    ownerType: String
  ): Either[String, String] = {
    
    val query = s"""
      query {
        $ownerType(login: "$ownerLogin") {
          projectV2(number: $projectNumber) {
            id
          }
        }
      }
    """
    
    val requestBody = Json.obj(
      "query" -> Json.fromString(query)
    )
    
    val request = basicRequest
      .post(uri"$graphqlUrl")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .body(requestBody.noSpaces)
    
    try {
      val response = request.send(backend)
      
      response.body match {
        case Right(body) =>
          parse(body) match {
            case Right(json) =>
              val cursor = json.hcursor
              
              // Check for GraphQL errors
              cursor.downField("errors").focus match {
                case Some(errors) =>
                  return Left(s"GraphQL error: ${errors.noSpaces}")
                case None => // No errors, continue
              }
              
              cursor.downField("data").downField(ownerType).downField("projectV2").get[String]("id") match {
                case Right(id) => Right(id)
                case Left(_) => Left(s"Project not found: $ownerLogin project #$projectNumber")
              }
              
            case Left(error) =>
              Left(s"Failed to parse JSON response: ${error.getMessage}")
          }
        case Left(error) =>
          Left(s"HTTP error: $error")
      }
    } catch {
      case e: Exception =>
        Left(s"Request failed: ${e.getMessage}")
    }
  }
  
  /**
   * Adds an issue to a project
   * 
   * @param projectId Project node ID
   * @param issueId Issue node ID
   * @return Either an error message or the project item ID
   */
  def addIssueToProject(
    projectId: String,
    issueId: String
  ): Either[String, String] = {
    
    val query = s"""
      mutation {
        addProjectV2ItemById(input: {
          projectId: "$projectId",
          contentId: "$issueId"
        }) {
          item {
            id
          }
        }
      }
    """
    
    val requestBody = Json.obj(
      "query" -> Json.fromString(query)
    )
    
    val request = basicRequest
      .post(uri"$graphqlUrl")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .body(requestBody.noSpaces)
    
    try {
      val response = request.send(backend)
      
      response.body match {
        case Right(body) =>
          parseAddToProjectResponse(body)
        case Left(error) =>
          Left(s"HTTP error: $error")
      }
    } catch {
      case e: Exception =>
        Left(s"Request failed: ${e.getMessage}")
    }
  }
  
  /**
   * Parses the addProjectV2ItemById mutation response
   */
  private def parseAddToProjectResponse(body: String): Either[String, String] = {
    parse(body) match {
      case Right(json) =>
        val cursor = json.hcursor
        
        // Check for GraphQL errors
        cursor.downField("errors").focus match {
          case Some(errors) =>
            return Left(s"GraphQL error: ${errors.noSpaces}")
          case None => // No errors, continue
        }
        
        // Extract project item ID
        cursor
          .downField("data")
          .downField("addProjectV2ItemById")
          .downField("item")
          .get[String]("id") match {
            case Right(itemId) => Right(itemId)
            case Left(error) => Left(s"Failed to parse project item ID: ${error.getMessage}")
          }
        
      case Left(error) =>
        Left(s"Failed to parse JSON response: ${error.getMessage}")
    }
  }
  
  /**
   * Gets custom field IDs from a project
   * 
   * @param ownerLogin Organization or user login
   * @param projectNumber Project number
   * @param fieldNames List of field names to retrieve (e.g., ["Size", "Priority"])
   * @return Either an error or a map of field name to field ID
   */
  def getProjectFieldIds(
    ownerLogin: String,
    projectNumber: Int,
    fieldNames: List[String]
  ): Either[String, Map[String, String]] = {
    
    // Try organization first
    getProjectFieldIdsWithOwnerType(ownerLogin, projectNumber, fieldNames, "organization") match {
      case Right(fields) => Right(fields)
      case Left(error) if error.contains("NOT_FOUND") || error.contains("Could not resolve to an Organization") =>
        System.err.println(s"Not an organization, trying as user for project fields...")
        getProjectFieldIdsWithOwnerType(ownerLogin, projectNumber, fieldNames, "user")
      case Left(error) => Left(error)
    }
  }
  
  /**
   * Gets custom field IDs with a specific owner type
   */
  private def getProjectFieldIdsWithOwnerType(
    ownerLogin: String,
    projectNumber: Int,
    fieldNames: List[String],
    ownerType: String
  ): Either[String, Map[String, String]] = {
    
    val query = s"""
      query {
        $ownerType(login: "$ownerLogin") {
          projectV2(number: $projectNumber) {
            fields(first: 20) {
              nodes {
                __typename
                ... on ProjectV2SingleSelectField {
                  id
                  name
                }
              }
            }
          }
        }
      }
    """
    
    val requestBody = Json.obj(
      "query" -> Json.fromString(query)
    )
    
    val request = basicRequest
      .post(uri"$graphqlUrl")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .body(requestBody.noSpaces)
    
    try {
      val response = request.send(backend)
      
      response.body match {
        case Right(body) =>
          parseProjectFieldsResponse(body, fieldNames, ownerType)
        case Left(error) =>
          Left(s"HTTP error: $error")
      }
    } catch {
      case e: Exception =>
        Left(s"Request failed: ${e.getMessage}")
    }
  }
  
  /**
   * Parses the project fields response
   */
  private def parseProjectFieldsResponse(
    body: String,
    fieldNames: List[String],
    ownerType: String
  ): Either[String, Map[String, String]] = {
    parse(body) match {
      case Right(json) =>
        val cursor = json.hcursor
        
        // Check for GraphQL errors
        cursor.downField("errors").focus match {
          case Some(errors) =>
            return Left(s"GraphQL error: ${errors.noSpaces}")
          case None => // No errors, continue
        }
        
        // Extract fields
        val fieldsResult = cursor
          .downField("data")
          .downField(ownerType)
          .downField("projectV2")
          .downField("fields")
          .downField("nodes")
          .as[List[Json]]
        
        fieldsResult match {
          case Right(fieldsJson) =>
            val fieldMap = fieldsJson.flatMap { fieldJson =>
              val fieldCursor = fieldJson.hcursor
              for {
                id <- fieldCursor.get[String]("id").toOption
                name <- fieldCursor.get[String]("name").toOption
                if fieldNames.exists(_.equalsIgnoreCase(name))
              } yield name -> id
            }.toMap
            
            System.err.println(s"Found custom fields: ${fieldMap.keys.mkString(", ")}")
            Right(fieldMap)
            
          case Left(error) =>
            Left(s"Failed to parse project fields: ${error.getMessage}")
        }
        
      case Left(error) =>
        Left(s"Failed to parse JSON response: ${error.getMessage}")
    }
  }
  
  /**
   * Updates a single-select field on a project item
   * 
   * @param projectId Project node ID
   * @param itemId Project item ID
   * @param fieldId Field ID
   * @param optionName Option name (e.g., "M", "P2")
   * @return Either an error message or success
   */
  def updateProjectItemField(
    projectId: String,
    itemId: String,
    fieldId: String,
    optionName: String
  ): Either[String, Unit] = {
    
    // First, get the option ID for this field value
    getFieldOptionId(projectId, fieldId, optionName) match {
      case Left(error) => Left(error)
      case Right(optionId) =>
        
        val query = s"""
          mutation {
            updateProjectV2ItemFieldValue(input: {
              projectId: "$projectId",
              itemId: "$itemId",
              fieldId: "$fieldId",
              value: {
                singleSelectOptionId: "$optionId"
              }
            }) {
              projectV2Item {
                id
              }
            }
          }
        """
        
        val requestBody = Json.obj(
          "query" -> Json.fromString(query)
        )
        
        val request = basicRequest
          .post(uri"$graphqlUrl")
          .header("Authorization", s"Bearer $token")
          .header("Content-Type", "application/json")
          .body(requestBody.noSpaces)
        
        try {
          val response = request.send(backend)
          
          response.body match {
            case Right(body) =>
              parse(body) match {
                case Right(json) =>
                  val cursor = json.hcursor
                  
                  // Check for GraphQL errors
                  cursor.downField("errors").focus match {
                    case Some(errors) =>
                      Left(s"GraphQL error: ${errors.noSpaces}")
                    case None =>
                      System.err.println(s"Updated field value to: $optionName")
                      Right(())
                  }
                  
                case Left(error) =>
                  Left(s"Failed to parse JSON response: ${error.getMessage}")
              }
            case Left(error) =>
              Left(s"HTTP error: $error")
          }
        } catch {
          case e: Exception =>
            Left(s"Request failed: ${e.getMessage}")
        }
    }
  }
  
  /**
   * Gets the option ID for a field value
   */
  private def getFieldOptionId(
    projectId: String,
    fieldId: String,
    optionName: String
  ): Either[String, String] = {
    
    val query = s"""
      query {
        node(id: "$fieldId") {
          ... on ProjectV2SingleSelectField {
            options {
              id
              name
            }
          }
        }
      }
    """
    
    val requestBody = Json.obj(
      "query" -> Json.fromString(query)
    )
    
    val request = basicRequest
      .post(uri"$graphqlUrl")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .body(requestBody.noSpaces)
    
    try {
      val response = request.send(backend)
      
      response.body match {
        case Right(body) =>
          parse(body) match {
            case Right(json) =>
              val cursor = json.hcursor
              
              // Check for GraphQL errors
              cursor.downField("errors").focus match {
                case Some(errors) =>
                  return Left(s"GraphQL error: ${errors.noSpaces}")
                case None => // No errors, continue
              }
              
              // Extract options
              val optionsResult = cursor
                .downField("data")
                .downField("node")
                .downField("options")
                .as[List[Json]]
              
              optionsResult match {
                case Right(optionsJson) =>
                  val option = optionsJson.find { optionJson =>
                    optionJson.hcursor.get[String]("name").toOption.exists(_.equalsIgnoreCase(optionName))
                  }
                  
                  option match {
                    case Some(opt) =>
                      opt.hcursor.get[String]("id") match {
                        case Right(id) => Right(id)
                        case Left(_) => Left(s"Option '$optionName' found but has no ID")
                      }
                    case None =>
                      Left(s"Option '$optionName' not found in field")
                  }
                  
                case Left(error) =>
                  Left(s"Failed to parse field options: ${error.getMessage}")
              }
              
            case Left(error) =>
              Left(s"Failed to parse JSON response: ${error.getMessage}")
          }
        case Left(error) =>
          Left(s"HTTP error: $error")
      }
    } catch {
      case e: Exception =>
        Left(s"Request failed: ${e.getMessage}")
    }
  }
}
