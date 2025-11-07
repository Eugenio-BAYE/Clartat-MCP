package com.clartat.mcp.domain.github

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

/**
 * GitHub API domain models
 * 
 * These models represent the GitHub API responses for issues and related entities.
 */

/**
 * Represents a GitHub user (author, assignee, etc.)
 * 
 * @param login GitHub username
 * @param id GitHub user ID
 * @param avatar_url URL to user's avatar
 * @param html_url URL to user's profile
 */
case class GithubUser(
  login: String,
  id: Long,
  avatar_url: String,
  html_url: String
)

object GithubUser {
  implicit val decoder: Decoder[GithubUser] = deriveDecoder[GithubUser]
  implicit val encoder: Encoder[GithubUser] = deriveEncoder[GithubUser]
}

/**
 * Represents a GitHub label
 * 
 * @param id Label ID
 * @param name Label name
 * @param color Label color (hex without #)
 * @param description Optional label description
 */
case class GithubLabel(
  id: Long,
  name: String,
  color: String,
  description: Option[String]
)

object GithubLabel {
  implicit val decoder: Decoder[GithubLabel] = deriveDecoder[GithubLabel]
  implicit val encoder: Encoder[GithubLabel] = deriveEncoder[GithubLabel]
}

/**
 * Represents a GitHub milestone
 * 
 * @param id Milestone ID
 * @param number Milestone number
 * @param title Milestone title
 * @param state Milestone state (open/closed)
 * @param description Optional milestone description
 */
case class GithubMilestone(
  id: Long,
  number: Int,
  title: String,
  state: String,
  description: Option[String]
)

object GithubMilestone {
  implicit val decoder: Decoder[GithubMilestone] = deriveDecoder[GithubMilestone]
  implicit val encoder: Encoder[GithubMilestone] = deriveEncoder[GithubMilestone]
}

/**
 * Represents a GitHub issue
 * 
 * @param id Issue ID
 * @param number Issue number
 * @param title Issue title
 * @param state Issue state (open/closed)
 * @param body Optional issue body/description
 * @param user User who created the issue
 * @param labels List of labels
 * @param assignees List of assigned users
 * @param milestone Optional milestone
 * @param comments Number of comments
 * @param created_at Creation timestamp
 * @param updated_at Last update timestamp
 * @param closed_at Optional closing timestamp
 * @param html_url URL to the issue on GitHub
 */
case class GithubIssue(
  id: Long,
  number: Int,
  title: String,
  state: String,
  body: Option[String],
  user: GithubUser,
  labels: List[GithubLabel],
  assignees: List[GithubUser],
  milestone: Option[GithubMilestone],
  comments: Int,
  created_at: String,
  updated_at: String,
  closed_at: Option[String],
  html_url: String
)

object GithubIssue {
  implicit val decoder: Decoder[GithubIssue] = deriveDecoder[GithubIssue]
  implicit val encoder: Encoder[GithubIssue] = deriveEncoder[GithubIssue]
}

/**
 * Summary of GitHub issues retrieval
 * 
 * @param total Total number of issues retrieved
 * @param open Number of open issues
 * @param closed Number of closed issues
 * @param labels Set of all unique labels found
 */
case class GithubIssuesSummary(
  total: Int,
  open: Int,
  closed: Int,
  labels: Set[String]
)

object GithubIssuesSummary {
  implicit val encoder: Encoder[GithubIssuesSummary] = deriveEncoder[GithubIssuesSummary]
  
  /**
   * Creates a summary from a list of issues
   * 
   * @param issues List of GitHub issues
   * @return Summary statistics
   */
  def fromIssues(issues: List[GithubIssue]): GithubIssuesSummary = {
    GithubIssuesSummary(
      total = issues.size,
      open = issues.count(_.state == "open"),
      closed = issues.count(_.state == "closed"),
      labels = issues.flatMap(_.labels.map(_.name)).toSet
    )
  }
}

/**
 * Complete result of a GitHub issues query
 * 
 * @param issues List of issues
 * @param summary Summary statistics
 */
case class GithubIssuesResult(
  issues: List[GithubIssue],
  summary: GithubIssuesSummary
)

object GithubIssuesResult {
  implicit val encoder: Encoder[GithubIssuesResult] = deriveEncoder[GithubIssuesResult]
}
