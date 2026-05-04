# Publishing turbo-assistant to JetBrains Marketplace

This document is the runbook for the manual setup needed before the first publish, and for the publish workflow itself. The Gradle and CI side is already wired up; the only work this document describes is the parts I cannot do for you (key generation, web forms, and GitHub Secrets).

## One-time setup

### 1. Generate a 4096-bit RSA signing certificate

The `signPlugin` task expects a multi-line certificate chain plus a multi-line private key. Generate both locally; the public chain (`chain.crt`) is committed to the repo, the private key is stored as a GitHub Secret and never written to disk in the repo.

Run these from the repo root:

```bash
# Encrypted 4096-bit private key. You will be prompted for a passphrase; remember it.
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096

# Decrypted form for use as the PRIVATE_KEY secret content. Keep it local; the .gitignore
# already excludes *.pem and private.* so this will not be staged.
openssl rsa -in private_encrypted.pem -out private.pem

# Self-signed certificate chain (public). Pick reasonable subject values when prompted.
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

You now have three files at the repo root: `private_encrypted.pem`, `private.pem`, `chain.crt`. Of those, only `chain.crt` is meant to be committed (it is public), but you have a choice:

- **Recommended**: keep `chain.crt` locally and feed its content into the `CERTIFICATE_CHAIN` GitHub Secret too. Symmetric handling; nothing on the public branch leaks any signing material at all.
- **Alternative**: commit `chain.crt` at the repo root and switch the `signing { }` block in `build.gradle.kts` to use `certificateChainFile = layout.projectDirectory.file("chain.crt")` instead of an env var. The build remains reproducible without a secret for the public half.

The current `build.gradle.kts` is configured for the recommended path: cert chain comes from the `CERTIFICATE_CHAIN` env var. If you prefer the file-based path, ask me to switch it.

### 2. Generate a Marketplace publishing token

1. Go to https://plugins.jetbrains.com/author/me/tokens
2. Click "Generate New Token"
3. Scope: select "publish" only (do not grant token-management or other scopes)
4. Lifetime: set whatever expiry you prefer; one year is reasonable
5. Copy the token. It is shown exactly once.

### 3. Set GitHub Secrets

Repository settings → Secrets and variables → Actions → New repository secret. Set the following four:

| Secret name | Content |
|-------------|---------|
| `CERTIFICATE_CHAIN` | Full content of `chain.crt`, including the `-----BEGIN CERTIFICATE-----` and `-----END CERTIFICATE-----` lines. Paste as multi-line. |
| `PRIVATE_KEY` | Full content of `private.pem`, including `-----BEGIN PRIVATE KEY-----` and `-----END PRIVATE KEY-----`. Paste as multi-line. |
| `PRIVATE_KEY_PASSWORD` | The passphrase you set when running `openssl genpkey`. |
| `PUBLISH_TOKEN` | The Marketplace token from step 2. |

GitHub Secrets handle multi-line content correctly; you can paste the entire PEM block including newlines.

### 4. Verify locally (optional but recommended before first push)

Set the same four env vars in your shell, then run:

```bash
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD="<your-passphrase>"
./gradlew signPlugin
```

If signing succeeds, the produced `build/distributions/turbo-assistant-<version>-signed.zip` is what `publishPlugin` will upload. Inspect the zip if you want to be certain.

## First-time-only: register the plugin manually on Marketplace

JetBrains Marketplace requires the first upload of a brand-new plugin to be done through the web UI so the publisher can fill in license, repository URL, tags, and description. The `publishPlugin` task cannot create a new listing; it can only push new versions onto an existing listing. If you skip this step and dispatch the workflow, you will get this error during the `Publish to Marketplace` job:

> Failed to upload plugin: Cannot find plugin. Note that you need to upload the plugin to the repository at least once manually (to specify options like the license, repository URL etc.).

To resolve, do this exactly once:

1. Build the plugin zip locally with `./gradlew buildPlugin`, or download the artifact from a successful `Build` job in GitHub Actions. The zip lives at `build/distributions/turbo-assistant-<version>.zip`.
2. Go to https://plugins.jetbrains.com/plugin/add and upload the zip.
3. Fill in:
   - License: matches the repo's `LICENSE` file
   - Repository URL: the GitHub repo URL
   - Tags / categories: pick what fits (Inspections, JSON, Build Tools, etc.)
   - Channel: `beta` for the first release
4. Submit. Review for new plugins takes 1 to 3 business days.

Once the listing is approved, every future publish (including the one you tried that hit this gate) goes through the workflow without needing to touch the web UI again.

## Triggering a publish

Channel selection lives in the workflow; tags are not coupled to publishing. You always pick the channel explicitly.

1. Go to GitHub → Actions → Build → "Run workflow"
2. Select branch (typically `main`)
3. Select **channel**:
   - `beta` (default): publishes to the beta channel. Users on the default channel do not see this version. This is the right choice for `0.1.0-beta.1`.
   - `stable`: publishes to the default channel. This is what end users will see by default. Use only when the build has soaked on beta and is ready for general availability.
4. Click "Run workflow"

The publish job runs only when manually dispatched (`if: github.event_name == 'workflow_dispatch'`), and only after `test` and `verify` succeed. Pushes to `main` and pull requests build, test, and verify but do not publish.

### Why workflow_dispatch and not tags?

The alternative is "tags trigger publish, channel inferred from tag pattern (e.g. `v*-beta*` → beta, `v*` → stable)". Tags are convenient but couple two unrelated decisions: marking a git point and shipping a binary. A stray tag push, a typo in the pattern, or a tag added retroactively can publish unintentionally. Workflow_dispatch keeps the publish gesture explicit and the tag history independent. If you later want both, we can add tag triggers without removing workflow_dispatch; the channel logic just needs a small `if` cascade.

## After a publish

- Watch the Marketplace page for your plugin. The listing shows pending → in-review → approved status. Beta-channel uploads still pass the same review.
- For `0.1.0-beta.1`, soak for one week minimum before considering a stable promote. Watch the Marketplace ratings and "report a problem" inbox for issues.
- A stable promote is just another `Run workflow` with channel = `stable`. The same artifact is republished on the new channel.

## Troubleshooting

- **`signPlugin` fails with "could not parse private key"**: the `PRIVATE_KEY` secret is missing newlines, or you pasted the encrypted form. Use `private.pem` (decrypted) content, not `private_encrypted.pem`.
- **`publishPlugin` fails with 401**: the `PUBLISH_TOKEN` secret is wrong or revoked. Generate a new one and update the secret.
- **`verifyPlugin` fails on a deprecated-API hit in plugin code**: address it. Hits in platform code that the plugin does not call directly are out of our scope; the verifier reports them but we do not have to fix them.

## Files referenced by this runbook

- `build.gradle.kts`: `signing { }`, `publishing { }`, `pluginVerification { }` blocks
- `.github/workflows/build.yml`: `publish` job, gated on `workflow_dispatch`
- `.gitignore`: excludes `*.pem`, `private.*`, `*.p12`, `*.pfx`, `*.key` so signing material does not get committed accidentally
