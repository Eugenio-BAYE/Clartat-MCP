package com.clartat.mcp.domain.github

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

/**
 * GitHub Project v2 models
 * 
 * Represents data structures for GitHub Projects v2 API (GraphQL)
 */

/**
 * A GitHub Project v2
 * 
 * @param id The project's node ID
 * @param number The project number
 * @param title The project title
 * @param url The project URL
 */
case class ProjectV2(
  id: String,
  number: Int,
  title: String,
  url: String
)

object ProjectV2 {
  implicit val decoder: Decoder[ProjectV2] = deriveDecoder[ProjectV2]
  implicit val encoder: Encoder[ProjectV2] = deriveEncoder[ProjectV2]
}

/**
 * A GitHub Project v2 item (can be an issue, pull request, or draft issue)
 * 
 * @param id The item's node ID
 * @param content Optional content (issue or PR)
 * @param fieldValues Field values for this item
 */
case class ProjectV2Item(
  id: String,
  content: Option[ProjectV2ItemContent],
  fieldValues: List[ProjectV2FieldValue]
)

object ProjectV2Item {
  implicit val decoder: Decoder[ProjectV2Item] = deriveDecoder[ProjectV2Item]
  implicit val encoder: Encoder[ProjectV2Item] = deriveEncoder[ProjectV2Item]
}

/**
 * Content of a project item (issue or pull request)
 * 
 * @param typename The type ("Issue" or "PullRequest")
 * @param number The issue/PR number
 * @param title The title
 * @param url The URL
 * @param state The state (open, closed, etc.)
 * @param body Optional body text
 * @param repository The repository
 */
case class ProjectV2ItemContent(
  typename: String,
  number: Int,
  title: String,
  url: String,
  state: String,
  body: Option[String],
  repository: ProjectV2Repository
)

object ProjectV2ItemContent {
  implicit val decoder: Decoder[ProjectV2ItemContent] = deriveDecoder[ProjectV2ItemContent]
  implicit val encoder: Encoder[ProjectV2ItemContent] = deriveEncoder[ProjectV2ItemContent]
}

/**
 * Repository information for a project item
 * 
 * @param name Repository name
 * @param owner Owner login
 */
case class ProjectV2Repository(
  name: String,
  owner: ProjectV2RepositoryOwner
)

object ProjectV2Repository {
  implicit val decoder: Decoder[ProjectV2Repository] = deriveDecoder[ProjectV2Repository]
  implicit val encoder: Encoder[ProjectV2Repository] = deriveEncoder[ProjectV2Repository]
}

/**
 * Repository owner
 * 
 * @param login Owner login name
 */
case class ProjectV2RepositoryOwner(
  login: String
)

object ProjectV2RepositoryOwner {
  implicit val decoder: Decoder[ProjectV2RepositoryOwner] = deriveDecoder[ProjectV2RepositoryOwner]
  implicit val encoder: Encoder[ProjectV2RepositoryOwner] = deriveEncoder[ProjectV2RepositoryOwner]
}

/**
 * A field value in a project item
 * 
 * @param typename The field type
 * @param name Field name
 * @param field The field definition
 * @param text Optional text value
 * @param number Optional number value
 * @param date Optional date value
 */
case class ProjectV2FieldValue(
  typename: String,
  name: Option[String],
  field: ProjectV2Field,
  text: Option[String],
  number: Option[Double],
  date: Option[String]
)

object ProjectV2FieldValue {
  implicit val decoder: Decoder[ProjectV2FieldValue] = deriveDecoder[ProjectV2FieldValue]
  implicit val encoder: Encoder[ProjectV2FieldValue] = deriveEncoder[ProjectV2FieldValue]
}

/**
 * A field definition in a project
 * 
 * @param name Field name
 */
case class ProjectV2Field(
  name: String
)

object ProjectV2Field {
  implicit val decoder: Decoder[ProjectV2Field] = deriveDecoder[ProjectV2Field]
  implicit val encoder: Encoder[ProjectV2Field] = deriveEncoder[ProjectV2Field]
}
