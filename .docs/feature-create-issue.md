# Feature: Create GitHub Issues via MCP

## Objective

Allow users to create GitHub issues directly through the MCP (Model Context Protocol) and automatically add them to the configured GitHub Project with custom fields (size and priority).

## Status

🚧 In Progress

## Use Case

Enable users to quickly create project tickets via Copilot/Cursor:
- "Create an issue: 'Implement authentication' with size M and priority P2"
- "Add a ticket: 'Fix login bug' priority P0"
- Issues are automatically added to the configured GitHub Project

## Requirements

### Mandatory
- **Title**: Issue title (required)

### Optional
- **Body**: Issue description
- **Size**: XS, S, M, L, XL (custom field in GitHub Project)
- **Priority**: P0, P1, P2, P3, P4, P5 (custom field in GitHub Project)

### Behavior
1. Create the issue in the repository found in the GitHub Project
2. Automatically add the issue to the configured Project
3. Set custom fields (size, priority) if provided
4. Return the issue URL

## Architecture

### New Components

#### Tool: `create-github-issue`
- **Location**: `src/main/scala/com/clartat/mcp/tools/impl/GithubCreateIssueTool.scala`
- **Parameters**:
  - `title` (string, required): Issue title
  - `body` (string, optional): Issue description
  - `size` (string, optional): Size value (XS|S|M|L|XL)
  - `priority` (string, optional): Priority value (P0|P1|P2|P3|P4|P5)

#### GraphQL Client Extensions
- **Location**: `src/main/scala/com/clartat/mcp/client/GithubGraphQLClient.scala`
- **New methods**:
  1. `getProjectRepository()`: Get the default repository from project items
  2. `createIssue(owner, repo, title, body)`: Create a GitHub issue
  3. `addIssueToProject(issueId, projectId)`: Add issue to project
  4. `getProjectFieldIds(projectId)`: Get custom field IDs (size, priority)
  5. `updateProjectItemField(itemId, fieldId, value)`: Update custom field

#### Domain Models
- **Location**: `src/main/scala/com/clartat/mcp/domain/github/`
- **New models**:
  - `CreateIssueInput`: Input for issue creation
  - `CreateIssueResult`: Result with issue ID and URL
  - `AddToProjectInput`: Input for adding to project
  - `UpdateFieldInput`: Input for updating custom fields

## Technical Flow

```
User request via MCP
    ↓
GithubCreateIssueTool
    ↓
1. Validate parameters (title required, size/priority values)
2. Get default repository from existing project items
3. Create issue via GitHub GraphQL mutation
4. Get project ID from environment (GITHUB_OWNER + GITHUB_REPO)
5. Add issue to project
6. If size provided: update size field
7. If priority provided: update priority field
    ↓
Return success with issue URL
```

## Environment Variables

**No new variables required!** Uses existing config:
- `GITHUB_TOKEN`: GitHub Personal Access Token
- `GITHUB_OWNER`: Organization name
- `GITHUB_REPO`: Project number

## Repository Selection Logic

**Simple approach**:
1. Fetch existing project items (via `GithubProjectV2Tool` logic)
2. Extract repository from the first issue found
3. Use that repository for creating new issues

**Fallback**: If no issues in project, return error asking user to manually add one issue first.

## Custom Fields Mapping

GitHub Project custom fields must exist with these exact names:
- **Size**: Single-select field with options: XS, S, M, L, XL
- **Priority**: Single-select field with options: P0, P1, P2, P3, P4, P5

The tool will:
1. Query project to get field IDs
2. Match field names (case-insensitive)
3. Set the selected option value

## Error Handling

- Missing title → Error: "Title is required"
- Invalid size value → Error: "Size must be one of: XS, S, M, L, XL"
- Invalid priority → Error: "Priority must be one of: P0, P1, P2, P3, P4, P5"
- No repository found → Error: "No repository found in project. Please add at least one issue manually first."
- Custom field not found → Warning + continue (field not set)

## Implementation Plan

### Phase 1: Core Issue Creation
1. Domain models for issue creation
2. GraphQL client method: `createIssue()`
3. GraphQL client method: `getProjectRepository()`
4. Basic tool implementation (title + body only)
5. **Test**: Create a simple issue

### Phase 2: Project Integration
6. GraphQL client method: `addIssueToProject()`
7. Update tool to add issue to project
8. **Test**: Verify issue appears in project

### Phase 3: Custom Fields
9. GraphQL client method: `getProjectFieldIds()`
10. GraphQL client method: `updateProjectItemField()`
11. Update tool to handle size and priority
12. **Test**: Create issue with size and priority

### Phase 4: Integration
13. Register tool in `DefaultTools.scala`
14. End-to-end testing via Cursor/VSCode
15. Update README with usage examples

## Testing Strategy

### Unit Tests
- Parameter validation (size/priority enums)
- Repository extraction logic
- Error handling

### Integration Tests
Each phase should be tested manually:

**Phase 1 Test**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "create-github-issue",
    "arguments": {
      "title": "Test issue from MCP"
    }
  }
}
```

**Phase 3 Test**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "create-github-issue",
    "arguments": {
      "title": "Feature: User authentication",
      "body": "Implement OAuth 2.0 flow",
      "size": "M",
      "priority": "P2"
    }
  }
}
```

### Validation
- Issue created in correct repository ✓
- Issue added to project ✓
- Custom fields set correctly ✓
- URL returned in response ✓

## References

- GitHub GraphQL API: https://docs.github.com/en/graphql
- CreateIssue mutation: https://docs.github.com/en/graphql/reference/mutations#createissue
- AddProjectV2ItemById mutation: https://docs.github.com/en/graphql/reference/mutations#addprojectv2itembyid
- UpdateProjectV2ItemFieldValue mutation: https://docs.github.com/en/graphql/reference/mutations#updateprojectv2itemfieldvalue

