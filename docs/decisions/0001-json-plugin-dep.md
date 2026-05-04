# 0001 — Depend on bundled JSON and JavaScript plugins

**Status:** Accepted
**Date:** 2026-04-27

Inspection A and the workspace-model service need (a) JSON PSI APIs (`JsonFile`, `JsonObject`, `JsonProperty`, `JsonStringLiteral`, `JsonElementGenerator` from `com.intellij.json.psi`) and (b) the JavaScript plugin's `PackageJsonFileManager` and `PackageJsonData` (`com.intellij.javascript.nodejs`) to enumerate `package.json` files. Both ship as bundled plugins on every supported IDE — WebStorm, IntelliJ Ultimate, PhpStorm with JS, RubyMine with JS — which matches the v0.1 target list in `CLAUDE.md`. We declare hard dependencies in `plugin.xml`: `<depends>com.intellij.modules.json</depends>` and `<depends>JavaScript</depends>`. Both were added when `TodoInJsonInspection` was scaffolded and remain authoritative.

Alternatives considered: optional `<depends optional="true">` so the plugin loads on Community editions — rejected because Inspection B and the planned package-graph tool window have no useful behavior without `PackageJsonFileManager`, and a half-loaded plugin is worse than a hard dependency error. Bundling our own JSON parser was rejected outright by the CLAUDE.md hard rule "use the platform's existing JSON PSI APIs." Re-implementing `PackageJsonFileManager`'s scope filtering and `.gitignore`/`node_modules` exclusion logic was rejected as scope creep against `docs/v0.1-spec.md` and likely to drift from JetBrains' canonical behavior.
