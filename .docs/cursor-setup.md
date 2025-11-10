# Cursor MCP Setup Guide

## Overview

This guide explains how to connect your Clartat-MCP server to Cursor IDE.

## Changes Made

The MCP server has been updated to support **both message formats**:
- ✅ Newline-delimited JSON (VSCode style)
- ✅ LSP-style Content-Length headers (Cursor style)

All responses are now sent with Content-Length headers for maximum compatibility.

## Configuration

### 1. Build the Project

```bash
cd /home/ubuntu/Public/Clartat-MCP
sbt assembly
```

This creates: `/home/ubuntu/Public/Clartat-MCP/target/scala-3.7.3/clartat-mcp.jar`

### 2. Configure Cursor MCP Settings

Add this to your Cursor MCP settings file:
- **Location**: `~/.config/Cursor/User/globalStorage/mcp/settings.json`

```json
{
  "mcpServers": {
    "clartat": {
      "command": "java",
      "args": [
        "-jar",
        "/home/ubuntu/Public/Clartat-MCP/target/scala-3.7.3/clartat-mcp.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "your_github_token_here",
        "GITHUB_OWNER": "your_org_or_username",
        "GITHUB_REPO": "project_number"
      }
    }
  }
}
```

### 3. Environment Variables

The server requires these environment variables:

- **GITHUB_TOKEN**: Your GitHub Personal Access Token with project access
- **GITHUB_OWNER**: Organization login (e.g., "Shopifake")
- **GITHUB_REPO**: GitHub Project number (e.g., "1")

### 4. Restart Cursor

After saving the configuration, restart Cursor to load the MCP server.

## Available Tool: github-project

The server provides a `github-project` tool with these **optional parameters**:

| Parameter | Type   | Description                                    | Required |
|-----------|--------|------------------------------------------------|----------|
| `state`   | string | Filter issues by state (OPEN, CLOSED, or ALL) | No       |
| `limit`   | number | Maximum number of issues to return             | No       |
| `search`  | string | Search term to filter issues by title or body  | No       |

### Example Usage

```json
{
  "name": "github-project",
  "arguments": {
    "state": "OPEN",
    "limit": 10,
    "search": "bug"
  }
}
```

## Troubleshooting

### Check Server Logs

The server logs to stderr. You can test it manually:

```bash
# Test with initialize request
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | java -jar target/scala-3.7.3/clartat-mcp.jar
```

### Debug Output

The server now outputs debug information including:
- Message format detected (newline vs Content-Length)
- Requests received
- Responses sent

Check Cursor's MCP logs or run the server manually to see these messages.

## Protocol Details

### Supported Methods

1. **initialize**: Handshake with server capabilities
2. **tools/list**: List available tools
3. **tools/call**: Execute a tool with parameters
4. **notifications/initialized**: Post-initialization notification

### Protocol Version

The server implements MCP protocol version: `2024-11-05`

## Next Steps

After configuration:
1. Restart Cursor
2. The `github-project` tool should appear in Cursor's MCP tools
3. You can now pass parameters when calling the tool
4. The server will fetch and filter GitHub project items based on your parameters

