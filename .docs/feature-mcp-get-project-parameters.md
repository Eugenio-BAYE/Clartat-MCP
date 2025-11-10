# Feature: Pass Parameters to MCP Get Project

## Objective

Ensure that we can properly pass parameters to the `get project` method of the MCP (Model Context Protocol).

## Status

✅ Completed - Parameters working on VSCode

## Tasks

- [x] Identify current implementation of `get project` in the code
- [x] Verify method signature and accepted parameters
- [x] Fix message format compatibility issue (Content-Length headers)
- [x] Fix JSON-RPC response format (remove null fields)
- [x] Fix notification handling (no response for requests without ID)
- [x] Fix tool schema format (use "parameters" for VSCode compatibility)
- [x] Test and validate parameter passing on VSCode
- [x] Document available parameters

## Technical Notes

### To Investigate
- What is the current format of `get project`?
- What parameters are supported?
- Are there any constraints or limitations?

### Findings

#### Message Format Support (2025-11-10)
**Problem**: MCP server worked in VSCode but not in Cursor.

**Root Cause**: Different clients use different message formats for JSON-RPC communication:
- **VSCode**: Uses simple newline-delimited JSON messages
- **Cursor**: Uses LSP-style Content-Length headers (like Language Server Protocol)

**Solution Implemented**: Modified `McpServerIO.scala` to support both formats:
1. Auto-detects message format by checking first line
2. If starts with `Content-Length:`, parses LSP-style headers
3. Otherwise, treats as newline-delimited JSON
4. **All responses now sent with Content-Length headers** for maximum compatibility

**Code Changes**:
- Added `BufferedReader` for more flexible input reading
- Implemented `readContentLengthMessage()` method for LSP-style parsing
- Modified `sendResponse()` to always use Content-Length headers
- Added debug logging to stderr for troubleshooting

**Testing**: Both formats verified working:
```bash
# Newline-delimited (VSCode style) ✅
echo '{"jsonrpc":"2.0","id":1,"method":"initialize",...}' | java -jar clartat-mcp.jar

# Content-Length (Cursor style) ✅
printf "Content-Length: 158\r\n\r\n{...}" | java -jar clartat-mcp.jar
```

#### Tool Parameters
The `github-project` tool accepts optional parameters:
- `state` (string): Filter by OPEN, CLOSED, or ALL
- `limit` (number): Maximum number of issues to return
- `search` (string): Search term to filter by title/body

**Status**: ✅ Parameters are working correctly on VSCode. Tested and validated.

#### Tool Schema Format
**Issue**: MCP spec uses `"inputSchema"` but VSCode uses `"parameters"`.

**Solution**: Currently using `"parameters"` for VSCode compatibility. Added TODO comment in code for future multi-client support.

**Known Limitation**: Cursor compatibility with tool parameters needs investigation (may require `"inputSchema"` instead).

## Summary

Successfully implemented and validated parameter passing for the MCP `github-project` tool:

1. **Protocol Support**: Auto-detects message format (newline-delimited vs Content-Length)
2. **JSON-RPC Compliance**: Fixed response format to omit null fields
3. **Notification Handling**: Correctly skips responses for notifications
4. **Tool Schema**: Uses `"parameters"` for VSCode compatibility
5. **Parameter Validation**: All three optional parameters (state, limit, search) working

The tool is now fully functional on VSCode with proper parameter support.

## References

- Code: `src/main/scala/com/clartat/mcp/`
- Tool implementation: `src/main/scala/com/clartat/mcp/tools/impl/GithubProjectV2Tool.scala`
- Protocol handler: `src/main/scala/com/clartat/mcp/protocol/McpProtocol.scala`
