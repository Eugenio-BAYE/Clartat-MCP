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
}
