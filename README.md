# Clartat MCP Server

🔍 **Analyze GitHub Projects v2 directly in VS Code with GitHub Copilot**

A Model Context Protocol (MCP) server that brings your GitHub Projects data into GitHub Copilot Chat.

---

## 🚀 Quick Start

### Step 1: Build the Server

```bash
sbt assembly
```

This creates `target/scala-3.7.3/clartat-mcp.jar`

### Step 2: Get a GitHub Token

1. Go to https://github.com/settings/tokens
2. Click **"Generate new token (classic)"**
3. Name it (e.g., "Clartat MCP")
4. Select these scopes:
   - ✅ `repo`
   - ✅ `read:org`
   - ✅ `read:project`
5. Click **"Generate token"** and **copy it immediately** (you won't see it again!)

### Step 3: Find Your Project Number

Your GitHub Project URL looks like this:
```
https://github.com/orgs/YOUR_ORG/projects/1
                             ^^^^^^^^         ^
                             your org         project number
```

### Step 4: Configure VS Code

Create `.vscode/mcp.json` in your workspace:

```json
{
  "servers": {
    "clartat": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/clartat-mcp.jar"],
      "env": {
        "GITHUB_TOKEN": "ghp_xxxxxxxxxxxxx",
        "GITHUB_OWNER": "YourOrganization",
        "GITHUB_REPO": "1"
      }
    }
  }
}
```

**Replace:**
- `/absolute/path/to/clartat-mcp.jar` → actual path to the JAR
- `ghp_xxxxxxxxxxxxx` → your GitHub token
- `YourOrganization` → your GitHub organization name
- `1` → your project number

### Step 5: Activate the Server

1. Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on Mac)
2. Type **"Developer: Reload Window"**
3. Press Enter

### Step 6: Use It!

Open GitHub Copilot Chat and ask:
- "Show me all issues from the project"
- "What are the open issues?"
- "Summarize the project status"

The `github-project` tool will fetch all data from your GitHub Project!

---

## 📋 What You Get

The tool returns:
- ✅ **All project items** (issues, draft issues, pull requests)
- ✅ **Complete issue details** (title, description, state, URL)
- ✅ **Repository information** for each item
- ✅ **Custom project fields** (Status, Priority, Size, etc.)
- ✅ **Full JSON data** for advanced analysis

Example output:
```markdown
# GitHub Project Items

**Organization**: MyOrg
**Project Number**: 1
**Total Items**: 32
**Issues**: 30

## Issues

### #4: Create User Authentication
**State**: OPEN
**URL**: https://github.com/MyOrg/my-repo/issues/4
**Repository**: MyOrg/my-repo

Implement OAuth 2.0 authentication flow...
```

---

## 🔒 Security

**⚠️ Important:**
- Never commit `.vscode/mcp.json` with your token
- Add it to `.gitignore`:
  ```bash
  echo ".vscode/mcp.json" >> .gitignore
  ```
- Rotate tokens regularly
- Use minimal scopes (only what's needed)

---

## 🐛 Troubleshooting

### Tool not showing in Copilot?
1. Check the JAR path is **absolute** (not relative)
2. Reload VS Code: `Ctrl+Shift+P` → "Developer: Reload Window"
3. Check VS Code Output: `View` → `Output` → select `MCP`

### Authentication error?
- Verify token has `repo`, `read:org`, `read:project` scopes
- Check token hasn't expired
- Make sure `GITHUB_TOKEN` is set correctly

### Project not found?
- `GITHUB_OWNER` must be the **exact** organization name
- `GITHUB_REPO` must be just the **number** (e.g., "1", not "My Project")
- Ensure your token has access to that organization

---

## 🛠️ Development

### Build & Test

```bash
# Compile
sbt compile

# Create JAR
sbt assembly

# Run tests
sbt test
```

### Test Manually

```bash
# List available tools
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | \
  java -jar target/scala-3.7.3/clartat-mcp.jar | jq '.'
```

---

## 📁 Project Structure

```
src/main/scala/com/clartat/mcp/
├── McpServerApp.scala                    # Entry point
├── client/
│   └── GithubGraphQLClient.scala        # GitHub GraphQL API
├── domain/
│   ├── JsonRpc.scala                    # JSON-RPC models
│   ├── McpProtocol.scala                # MCP models
│   ├── Tool.scala                       # Tool models
│   └── github/ProjectV2.scala           # GitHub models
├── protocol/
│   ├── JsonRpcProtocol.scala            # JSON-RPC protocol
│   └── McpProtocol.scala                # MCP protocol
├── service/
│   ├── McpRequestHandler.scala          # Request handling
│   └── McpServerIO.scala                # I/O (stdin/stdout)
└── tools/
    ├── Tool.scala                       # Tool base trait
    ├── ToolRegistry.scala               # Tool registry
    ├── DefaultTools.scala               # Tool configuration
    └── impl/GithubProjectV2Tool.scala   # GitHub tool
```

**Architecture:**
- **Clean separation** between domain, protocol, service, and tools
- **Extensible** tool system via registry pattern
- **Type-safe** with Scala 3 and Circe for JSON

---

## 📝 License

MIT License - See LICENSE file for details

---

## 🤝 Contributing

Contributions welcome! Feel free to:
- Report bugs
- Suggest features
- Submit pull requests

---

**Made with ❤️ using Scala 3 and the Model Context Protocol**
