# GitHub App — remediation PRs in service repos

After AI analysis, LogSpectra can open a PR in the **microservice** repo registered via the starter (`logspectra.code-source.git-url`), e.g. `gateway-service`, `payment-service`.

```
Analyze (UI / API)
   → AI result
   → Redis code-registry (gitUrl + defaultBranch)
   → GitHub App installation token
   → create branch logspectra/fix-<service>-<fp>-<ts>
   → commit .logspectra/remediations/<fp>.md
   → open PR → GitHub Actions CI runs
   → reviewer Approves → you get email → merge
```

**No MCP.** Runtime uses the GitHub REST API with a GitHub App. MCP/`gh` is only for Cursor locally.

## 1. Create the GitHub App

1. Org (or user) → **Settings** → **Developer settings** → **GitHub Apps** → **New GitHub App**
2. Name: e.g. `LogSpectra Remediation`
3. Homepage: your LogSpectra URL
4. Uncheck webhook (not required for this flow) **or** leave inactive
5. **Permissions → Repository permissions:**
   - **Contents**: Read & write (create branch + commit)
   - **Pull requests**: Read & write
   - **Metadata**: Read-only (default)
6. **Where can this GitHub App be installed?** → Only on this account / specific repos
7. Create App → note **App ID**
8. **Generate a private key** → download `.pem`
9. Optional: convert to PKCS#8  
   `openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in app.pem -out app-pkcs8.pem`

## 2. Install the App on service repos

1. App settings → **Install App**
2. Choose org → select **gateway-service**, **payment-service**, **inventory-service**, …
3. After install, open the installation URL; note **installation id** (numeric) from the URL:  
   `https://github.com/settings/installations/<INSTALLATION_ID>`  
   or `GET /app/installations` with an App JWT

## 3. Configure LogSpectra backend

```yaml
github:
  app:
    enabled: true
    app-id: "123456"
    installation-id: "987654321"
    private-key-path: /secrets/logspectra-github-app.pem
  pr:
    enabled: true
    auto-create-after-analysis: false   # prefer explicit flag from UI/API first
    reviewers: alice,bob
```

Env vars:

| Variable | Purpose |
|----------|---------|
| `GITHUB_APP_ENABLED=true` | Turn on |
| `GITHUB_APP_ID` | App ID |
| `GITHUB_APP_INSTALLATION_ID` | Installation id |
| `GITHUB_APP_PRIVATE_KEY_PATH` | Path to PEM |
| `GITHUB_APP_PRIVATE_KEY` | PEM body (use `\n` for newlines) |
| `GITHUB_PR_AUTO_CREATE=true` | Open PR on every analysis |
| `GITHUB_PR_REVIEWERS` | Comma-separated GitHub usernames |

## 4. Service registration (already via starter)

```yaml
logspectra:
  service-name: gateway-service
  project-id: <uuid>
  code-source:
    git-url: https://github.com/<org>/gateway-service.git
    default-branch: main
  registry:
    enabled: true
    backend-url: http://localhost:8081
```

## 5. Trigger a PR

**Explicit (recommended):**

```http
POST /api/v1/logs/analyze
{
  "traceId": "...",
  "createRemediationPr": true
}
```

Response includes `remediationPrUrl`, `remediationBranch`, `remediationPrNumber`.

**Automatic:** set `github.pr.auto-create-after-analysis: true`.

## 6. GitHub side (merge / LGTM email)

On each **service** repo:

1. **Settings → Actions** — allow Actions so CI runs on the new PR  
2. **Settings → Branches → Protect `main`/`develop`**  
   - Require PR + 1 approval  
   - Require status checks (your CI job)  
3. Reviewer: **Notifications → Email** for PR reviews  

Author gets email when someone Approves; then merge (or enable **auto-merge** on the PR).

## 7. Iterative GitHub agent (tool loop) + PR

No `git clone` on the server. With **OpenAI** + `github.app.enabled` + registered `gitUrl`:

```
list_repo_files  →  get_file_content(path)  →  (repeat if needed)  →  JSON analysis + newContent
                                                                              ↓
                                                              create branch + commit + PR
```

Config (`application.yml`):

```yaml
github:
  agent:
    tool-calling-enabled: true
    max-iterations: 8
```

Tools (OpenAI function calling):

| Tool | Purpose |
|------|---------|
| `list_repo_files` | Recursive tree (optional `pathPrefix` / `query`) |
| `get_file_content` | Fetch exact path into RAM |

This is **iterative refinement**, not ML self-training — each analysis re-discovers files from the live repo.

PR contents:

- Source patches when the model returns `filePath` + `newContent`
- Markdown brief under `.logspectra/remediations/<fingerprint>.md`
- Dynamic branch: `logspectra/fix-<service>-<fp>-<timestamp>`

## 8. Verify

```bash
# After analysis with createRemediationPr=true
curl -s -H "Authorization: Bearer $TOKEN" ... | jq '.remediationPrUrl'
```

Check the service repo → Pull requests → CI should start automatically.
