package com.clartat.mcp.client

import com.clartat.mcp.domain.github._
import sttp.client3._
import sttp.client3.circe._
import io.circe.parser._

import scala.util.{Try, Success, Failure}

/**
 * GitHub API client
 * 
 * Provides methods to interact with the GitHub REST API.
 * Handles authentication, pagination, and rate limiting.
 */
class GithubClient(token: String) {
  
  private val backend = HttpURLConnectionBackend()
  private val baseUrl = "https://api.github.com"
  
  /**
   * Fetches all issues from a repository with pagination
   * 
   * @param owner Repository owner
   * @param repo Repository name
   * @param state Issue state filter: "open", "closed", or "all" (default: "open")
   * @param labels Optional labels to filter by
   * @param perPage Number of items per page (max 100, default: 100)
   * @return Either an error message or the list of issues
   */
  def fetchIssues(
    owner: String,
    repo: String,
    state: String = "open",
    labels: Option[List[String]] = None,
    perPage: Int = 100
  ): Either[String, List[GithubIssue]] = {
    
    def fetchAllPages(page: Int, accumulated: List[GithubIssue]): Either[String, List[GithubIssue]] = {
      // Safety: stop after 10 pages to avoid infinite loops
      if (page > 10) {
        Right(accumulated)
      } else {
        fetchIssuesPage(owner, repo, state, labels, perPage, page) match {
          case Right(issues) =>
            if (issues.isEmpty) {
              Right(accumulated)
            } else {
              fetchAllPages(page + 1, accumulated ++ issues)
            }
          case Left(error) =>
            Left(error)
        }
      }
    }
    
    fetchAllPages(1, List.empty)
  }
  
  /**
   * Fetches a single page of issues
   * 
   * @param owner Repository owner
   * @param repo Repository name
   * @param state Issue state filter
   * @param labels Optional labels to filter by
   * @param perPage Number of items per page
   * @param page Page number (1-indexed)
   * @return Either an error message or the list of issues for this page
   */
  private def fetchIssuesPage(
    owner: String,
    repo: String,
    state: String,
    labels: Option[List[String]],
    perPage: Int,
    page: Int
  ): Either[String, List[GithubIssue]] = {
    
    val url = s"$baseUrl/repos/$owner/$repo/issues"
    
    var request = basicRequest
      .get(uri"$url?state=$state&per_page=$perPage&page=$page")
      .header("Authorization", s"token $token")
      .header("Accept", "application/vnd.github.v3+json")
      .response(asJson[List[GithubIssue]])
    
    // Add labels filter if provided
    labels.foreach { labelList =>
      val labelsParam = labelList.mkString(",")
      request = basicRequest
        .get(uri"$url?state=$state&per_page=$perPage&page=$page&labels=$labelsParam")
        .header("Authorization", s"token $token")
        .header("Accept", "application/vnd.github.v3+json")
        .response(asJson[List[GithubIssue]])
    }
    
    val response = request.send(backend)
    
    response.body match {
      case Right(issues) => 
        Right(issues)
        
      case Left(error) =>
        // Handle HTTP errors
        response.code.code match {
          case 401 =>
            Left("Authentication failed. Please check your GITHUB_TOKEN.")
          case 403 =>
            val rateLimitRemaining = response.header("X-RateLimit-Remaining").getOrElse("unknown")
            val rateLimitReset = response.header("X-RateLimit-Reset").getOrElse("unknown")
            Left(s"Rate limit exceeded. Remaining: $rateLimitRemaining, Reset at: $rateLimitReset")
          case 404 =>
            Left(s"Repository not found: $owner/$repo")
          case code =>
            Left(s"GitHub API error (HTTP $code): ${error.getMessage}")
        }
    }
  }
  
  /**
   * Tests the connection to GitHub API
   * 
   * @return Either an error message or success confirmation
   */
  def testConnection(): Either[String, String] = {
    val url = s"$baseUrl/user"
    
    val request = basicRequest
      .get(uri"$url")
      .header("Authorization", s"token $token")
      .header("Accept", "application/vnd.github.v3+json")
    
    val response = request.send(backend)
    
    response.code.code match {
      case 200 => Right("GitHub connection successful")
      case 401 => Left("Invalid GitHub token")
      case code => Left(s"GitHub API error: HTTP $code")
    }
  }
}

/**
 * Companion object for creating GitHub clients
 */
object GithubClient {
  
  /**
   * Creates a GitHub client with the given token
   * 
   * @param token GitHub Personal Access Token
   * @return GitHub client instance
   */
  def apply(token: String): GithubClient = new GithubClient(token)
  
  /**
   * Creates a GitHub client from environment variable
   * 
   * @return Either an error message or the GitHub client
   */
  def fromEnv(): Either[String, GithubClient] = {
    sys.env.get("GITHUB_TOKEN") match {
      case Some(token) if token.nonEmpty => Right(new GithubClient(token))
      case _ => Left("GITHUB_TOKEN environment variable not set")
    }
  }
}
