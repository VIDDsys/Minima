# Minima — Remote MCP

## Config (Settings → Remote MCP)

JSON (still supported) plus **per-server auth editors** (Bearer / token + custom headers):

```json
{
  "servers": [
    {
      "name": "demo",
      "url": "https://mcp.example.com/mcp",
      "enabled": true,
      "bearerToken": "YOUR_TOKEN",
      "headers": {
        "X-Custom": "value"
      }
    }
  ]
}
```

### Auth without browser OAuth (v2.13.0+)

- **`bearerToken`**: dedicated field → sends `Authorization: Bearer <token>` (if the value already starts with `Bearer `, it is not doubled).
- **`headers`**: arbitrary static headers. If you set `Authorization` here, it **wins** over `bearerToken`.
- Settings UI: under MCP, each parsed server shows a password **Bearer / token** field and a **Custom headers** editor (`Name: Value` per line; apply on blur).
- No browser OAuth / PKCE yet — use long-lived API tokens, or a local proxy that attaches rotating OAuth cookies.

Wire merge order for Streamable HTTP:

1. Optional session `Mcp-Session-Id` from memory (if user did not set it)
2. Effective user headers = bearer → then custom `headers` (custom wins on name collision)
3. User-static headers always beat the auto session id

- `enabled: false` skips that server (tools not registered).
- Streamable HTTP JSON-RPC: `initialize` → `tools/list` → `tools/call`.
- Minima retries **once** on transient timeouts / 502–504.
- Tools appear as `mcp__{server}__{tool}`.
- Health / error strings go through secret redaction (Bearer / sk- / api_key).

## Session id (v2.8.3+)

After a successful response that includes `Mcp-Session-Id` (any casing), Minima stores it
keyed by server `name` + normalized URL and sends `Mcp-Session-Id: <id>` on later requests
to that server.

- **User wins:** if effective headers already set `Mcp-Session-Id` / `mcp-session-id`, that value is used and the auto-store is not applied on the wire.
- **Clear:** re-running `initialize` clears the stored id first (fresh handshake). HTTP **401** / **404** also clear the stored session (typical expired/missing session).
- In-memory only for the process lifetime (no disk persistence of session ids).

## OAuth / interactive login

**Not implemented** as a browser flow. Prefer `bearerToken` or static `headers`.

Workarounds:
1. Paste a long-lived API token into Bearer / token (or JSON `bearerToken`).
2. Run a local proxy that attaches OAuth tokens; point Minima at the proxy URL.
3. Use servers that accept static bearer keys.

Future: optional OAuth PKCE + secure token store (out of scope until a clean minimal UX exists).

## Health check

**Refresh tools / health** runs initialize + tools/list per server and shows ●/○ lines. Disabled servers report `disabled`. Messages are secret-redacted.
