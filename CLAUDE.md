# CLAUDE.md

## Project overview

Unofficial JetBrains plugin that adds Turborepo support to WebStorm and IntelliJ Ultimate (and other JS-capable JetBrains IDEs). The current milestone (v0.1) focuses on cross-file validation in `turbo.json` and navigation between `turbo.json` task definitions and `package.json` scripts. Future milestones extend into run integration, env-var leak detection, boundaries inspections, and a package-graph tool window — see `docs/roadmap.md` for the order.

## Target platforms

- WebStorm and IntelliJ Ultimate are the primary targets; any JetBrains IDE that bundles the JavaScript/TypeScript stack (e.g. PhpStorm, or RubyMine with the JS plugin) is supported as a side effect.
- Minimum platform version: **2025.1**.
- Implementation language: **Kotlin**.

## Source-of-truth files

When in doubt, read these — in this order — before changing anything:

1. `docs/v0.1-spec.md` — what we're building right now.
2. `docs/roadmap.md` — what comes after, and why we are *not* doing certain things.
3. `build.gradle.kts` — platform version, dependencies, IntelliJ plugin Gradle plugin config.
4. `src/main/resources/META-INF/plugin.xml` — extension points, dependencies on bundled plugins (`com.intellij.modules.json`, `JavaScript`, etc.), declared inspections and references.
5. The Turborepo schema: `https://turbo.build/schema.json`.

## Hard rules

- Every inspection MUST have a fixture test (`BasePlatformTestCase` with files under `src/test/testData/`) before implementation is considered done. No exceptions.
- Use the platform's existing JSON PSI APIs and `PackageJsonFileManager` for everything that touches `turbo.json`, `turbo.jsonc`, or `package.json`. Never write a custom JSON parser; never read these files as plain text.
- If you encounter a deprecated IntelliJ Platform API, flag it and propose the current replacement. Do not silently adopt the deprecated path.
- Read `docs/roadmap.md` before suggesting any architectural change. Do not implement anything outside `docs/v0.1-spec.md` unless I explicitly approve it.
- If a v0.1 design choice would foreclose a roadmap item (v0.2 onward), flag it before committing. Otherwise, ignore future items — don't pre-build for them.

## Testing

All tests use `BasePlatformTestCase`. Fixture files live under `src/test/testData/`, organized by inspection or reference name. Each test loads a fixture, runs the inspection or resolves the reference, and asserts against an expected `ProblemDescriptor` (or expected target PSI element, for references) and an expected post-quick-fix output.

## Development loop

- `./gradlew runIde` — launches a sandbox IDE with the plugin installed for manual exploration.
- `./gradlew test` — runs the unit and fixture tests.
