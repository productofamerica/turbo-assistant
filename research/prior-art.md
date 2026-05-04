# Prior art

> Compiled 2026-04-27. This survey covers JetBrains' bundled JS plugins (where source is available), one third-party IntelliJ Turborepo plugin, and Vercel's official VS Code Turborepo extension. The goal is to harvest patterns we should reuse and identify which Turbo-specific features have already been built somewhere worth copying.

## WebStorm bundled npm plugin

**Source availability**: closed. The npm tool window, Run Configuration generation for `package.json` scripts, and the `PackageJsonFileManager` itself all live in the proprietary JavaScript plugin shipped with WebStorm and IDEA Ultimate. The `JetBrains/intellij-community` repo contains zero JavaScript-plugin source — confirmed by listing the repo's top-level directories (no `JS/`, no `javascript/`).

**What we can learn anyway**: the plugin's *API surface* is exposed to other plugins and we can read it through the consumers in [JetBrains/intellij-plugins](https://github.com/JetBrains/intellij-plugins). The patterns worth copying:

- **Workspace package enumeration via `PackageJsonFileManager`** — see [workspace-model.md](workspace-model.md). Don't roll our own walk for `package.json` files.
- **Dependency lookup via `PackageJsonData.containsOneOfDependencyOfAnyType(...)`** — same source. Use this for "does this package use Turborepo?" checks if v0.2 ever needs to gate features on Turborepo presence.
- **Run Configuration generation pattern**: the npm plugin's pattern of generating ephemeral run configs from `package.json` scripts is exactly what we'll do in v0.2 for `turbo run <task>`. The classes to mirror (when v0.2 lands) are `NpmRunConfiguration` and `NpmRunProfileState` — both visible in the Qodana JS code at [`JsProjectConfigurator.kt`](https://github.com/JetBrains/intellij-plugins/blob/master/qodana/js/src/org/jetbrains/qodana/js/JsProjectConfigurator.kt).

**Out of scope for v0.1**: nothing from the npm plugin needs to be wired in directly; we only consume `PackageJsonFileManager`.

## Prettier plugin (`JetBrains/intellij-plugins/prettierJS`)

**Source**: open ([`prettierJS/src/com/intellij/prettierjs/`](https://github.com/JetBrains/intellij-plugins/tree/master/prettierJS/src/com/intellij/prettierjs)).

This is a much closer analog to Turbo Assistant than the npm plugin — it's a JS-tooling plugin that:
- Discovers its target tool (`prettier`) via `package.json` deps.
- Coordinates with `package.json` scripts / config files.
- Provides a JSON-in-JS schema for `prettier.config.js`.
- Auto-detects when Prettier is used in a project and toggles configuration mode.

### Patterns worth copying

#### 1. Project configurator that runs on first open

[`PrettierConfiguratorService.kt`](https://github.com/JetBrains/intellij-plugins/blob/master/prettierJS/src/com/intellij/prettierjs/PrettierConfiguratorService.kt) — the canonical "auto-detect on project open" pattern. Verbatim shape:

```kotlin
@Service(Service.Level.PROJECT)
class PrettierConfiguratorService(private val project: Project, val cs: CoroutineScope) {

  fun configure(baseDir: VirtualFile) {
    cs.launch {
      constrainedReadAndWriteAction(ReadConstraint.inSmartMode(project)) {
        return@constrainedReadAndWriteAction detectAndSetUp(baseDir)
      }
    }
  }
  // ...
}
```

Notes:
- **`@Service(Service.Level.PROJECT)`** is the modern way to declare a project-level service. Don't use the older `<projectService>` extension point in `plugin.xml` for new code.
- **`constrainedReadAndWriteAction(ReadConstraint.inSmartMode(project))`** is the modern coroutines-aware pattern for a read-then-write action that needs the project to be out of dumb mode (i.e., indices are ready).
- Service uses an injected `CoroutineScope` — the platform provides one for `Service.Level.PROJECT` services automatically. Don't create your own `GlobalScope.launch` from a service.

For Turbo Assistant, we don't need a configurator in v0.1 (no auto-config to do). But when v0.2 adds Run Configuration auto-generation per task, mirror this exact pattern.

#### 2. Detection metrics + decision tree

The same file's `getDetectionInfo` shows how to score the workspace (does the root package.json depend on the tool? do subdirectory package.jsons? are there config files?) and turn that into a discrete categorical decision. Useful blueprint when v0.3 adds env-var leak detection — the "should this inspection even fire on this package?" question has the same shape.

#### 3. JSON-in-JS schema provider

[`PrettierConfigJsonSchemaInJsProvider.java`](https://github.com/JetBrains/intellij-plugins/blob/master/prettierJS/src/com/intellij/prettierjs/config/PrettierConfigJsonSchemaInJsProvider.java) — pattern for shipping a JSON Schema with the plugin and providing it for `.js` config files. Not relevant in v0.1 but documented in [json-psi.md](json-psi.md) for future reference.

### Patterns *not* worth copying

- The Java/Kotlin mix in `prettierJS/`. The plugin is a few years old and started in Java; new files are Kotlin. We start in Kotlin from day one — no need for the bilingual organization.
- The `PrettierLanguageService` machinery. Prettier ships a Node-side daemon and the plugin talks to it via stdio. We don't run a daemon in v0.1 (or ever, ideally).

## ESLint plugin

**Source availability**: closed. The bundled ESLint plugin is part of the proprietary JavaScript plugin (search across `JetBrains/intellij-plugins` for ESLint sources returns only `PrettierEslintRuleMappersFactory.kt`, which is Prettier-side glue, not the ESLint plugin itself).

**What's visible from outside**: ESLint integrates as a `LocalInspectionTool` (each ESLint rule maps to one IDE inspection), with an external annotator that runs ESLint as a subprocess. The cross-file model (knowing which files belong to which `eslint.config.js` scope) is the closest analog to what we need for `turbo.json`'s scope discovery.

**What we can copy from outside-the-source observations**:
- The "external annotator" pattern (running an external tool and converting results to `ProblemDescriptor`s) is what v0.3+ might want for env-var leak detection at scale. Not needed in v0.1.
- The "auto-fix from external suggestions" pattern: ESLint's quick fixes come from the linter itself, which is similar to how we'll want quick fixes derived from `@turbo/codemod` outputs eventually.

For v0.1, ignore ESLint. There's nothing in scope where it would inform our design.

## KartanHQ `intellij-turbo` (the existing Marketplace plugin)

**Source**: open ([`KartanHQ/intellij-turbo`](https://github.com/KartanHQ/intellij-turbo), Marketplace ID 22294).

This plugin already ships under the name "Turbo" on the JetBrains Marketplace. Its scope is **deliberately different** from ours — and that's good news, because it means there's no naming/feature collision.

From the [README](https://github.com/KartanHQ/intellij-turbo/blob/master/README.md):

> [Turborepo](https://turbo.build/repo) serves as a high-performance build system for `JavaScript` and `TypeScript` codebases.
>
> This plugin is designed to facilitate speedy creation of a new monorepo within seconds, ensuring every necessary setup is in place for you.

In other words: **it's a project scaffolder/wizard**, not a validator. Looking at its [`plugin.xml`](https://github.com/KartanHQ/intellij-turbo/blob/master/src/main/resources/META-INF/plugin.xml) confirms — the only extensions registered are:

```xml
<extensions defaultExtensionNs="com.intellij">
    <directoryProjectGenerator implementation="com.nekofar.milad.intellij.turbo.cli.TurboCliProjectGenerator" />
    <projectTemplatesFactory implementation="com.nekofar.milad.intellij.turbo.cli.TurboProjectTemplateFactory"/>
    <moduleBuilder builderClass="com.nekofar.milad.intellij.turbo.cli.TurboCliProjectModuleBuilder" />
</extensions>
```

`directoryProjectGenerator` + `moduleBuilder` are the New Project wizard hooks. There are no `localInspection`, `psi.referenceContributor`, or schema-provider extensions — confirming this plugin does zero validation, navigation, or PSI-level work.

### What we learn from intellij-turbo

- **Plugin id and naming**: existing ID is `com.nekofar.milad.intellij.turbo`, name is `Turbo`. We should pick a distinct, more specific name (e.g. `Turbo Assistant`) and a distinct id (`net.example.turbo-assistant` or similar) before publishing — see CLAUDE.md reminder about reserving the plugin ID early.
- **Plugin.xml dep declarations**: `<depends>com.intellij.modules.platform</depends>` + `<depends>JavaScript</depends>` — same as we'll need.
- **The `create-turbo` scaffolding niche is taken**. Good: matches our roadmap's "explicitly not doing — project scaffolding wizard" stance.

### What we don't learn

This plugin doesn't have any cross-file validation, navigation, run integration, or graph view — none of our v0.1+ features. There's nothing here to port.

## Vercel `turbo-vsc` (official VS Code Turborepo LSP)

**Source**: open ([`packages/turbo-vsc`](https://github.com/vercel/turborepo/tree/main/packages/turbo-vsc) inside the main `vercel/turborepo` repo).

This is the highest-signal prior art for Turbo Assistant. It's the only existing IDE integration that does what v0.1–v0.4 are aiming for. The architecture is a **Rust language server** ([turborepo-lsp](https://github.com/vercel/turborepo/tree/main/crates/turborepo-lsp)) communicating with a thin VS Code TypeScript client over LSP.

### What it ships (extension capabilities)

From the [README](https://github.com/vercel/turborepo/blob/main/packages/turbo-vsc/README.md) and [ARCHITECTURE.md](https://github.com/vercel/turborepo/blob/main/packages/turbo-vsc/ARCHITECTURE.md):

| Feature | Implemented via | Worth porting to JetBrains? |
|---|---|---|
| Find references for tasks (jump from `dependsOn` to script) | LSP `textDocument/references` | **Yes — this is v0.1 inspection PsiReferences.** |
| Run a pipeline with one click (gutter icon → terminal) | Client command + terminal API | **Yes — v0.2.** |
| Validate dependsOn fields ("are all sound") | LSP `textDocument/publishDiagnostics` from `handle_file_update` | **Yes — v0.1 inspection A.** |
| Validate package-qualified task references against actual workspace packages | Same as above | **Yes — v0.1 inspection B.** |
| Validate globs in pipeline `inputs`/`outputs` | Same | **Maybe — v0.4 boundaries territory; flag for spec update.** |
| Codemod warnings + one-click runner | Diagnostic + client command runs `npx @turbo/codemod` | **Backlog — already in roadmap.** |
| Daemon controls (start/stop/status) | Client commands | **No — we're not shelling out in v0.1.** |
| Auto-detect global vs local turbo (npm/yarn/pnpm/bun) | Client `findLocalTurbo()` | **Partial — v0.2 needs to find a turbo binary, but probably via `NpmManager.getInstance(project).packageRef`, not by calling the package manager CLI ourselves.** |
| Pipeline gradient (rainbow syntax highlighting) | `updateJSONDecorations` | **No — gimmicky, low value.** |
| Configuration: `turbo.path`, `turbo.useLocalTurbo` | VS Code settings | **No — JetBrains has its own JS interpreter / package manager settings; reuse those.** |

### Architecture notes worth borrowing

From `ARCHITECTURE.md`:

> The general rule is the LSP can know about packages, turbo jsons, and how to parse them, but shouldn't need to do any inference, package manager work, etc etc. Any heavy lifting should be kept on the daemon.

Translation for our (non-LSP) architecture: **the inspection layer parses turbo.json and knows about its structure; everything else (package enumeration, run invocation) goes through stable platform APIs (`PackageJsonFileManager`, run-configuration framework).** Same separation, different boundary.

> Note that we use the `jsonc_parser` crate rather than turbo's own TurboJSON parsing logic for maximum flexibility. we don't care if parts of it are malformed, as long as we can parse the parts we need to perform the client's request.

Same lesson for us: **read JSON via the platform's PSI** (which is forgiving — partial parses are still navigable); never require a fully-valid `turbo.json` to fire any inspection. If `dependsOn` has a typo elsewhere in the file, our inspection on the `extends` field should still fire.

### Specific diagnostics from `handle_file_update`

The ARCHITECTURE doc lists what the LSP validates on every file change:

- all globs are valid (global and pipeline-specific)
- all pipeline key names refer to valid tasks
- all `dependOn` fields are sound

The first item ("globs are valid") is **not** in our v0.1 spec. The schema-driven `$schema` validation does basic glob syntax, but the LSP does deeper checks (does the glob match any actual file?). That's a real gap and worth flagging for the spec update.

### What we *can't* port

- The Rust language server itself — we're writing platform-native PSI inspections, not reusing turbo's parser.
- LSP-driven diagnostic UX (pull-based, debounced via `useDebounce`). Platform PSI inspections are push-based via `LocalInspectionTool` — the platform handles debouncing and triggering for us.
- The codemod runner UI (terminal-driven). This will look quite different in JetBrains — closer to a Tool Window action than a terminal command. Roadmap item, not v0.1.

### Configuration surface — what to take, what to drop

The VS Code extension exposes only two settings:

```json
"turbo.path": "explicit path to turbo binary",
"turbo.useLocalTurbo": "silence the install-global-turbo prompt"
```

Both are about *running* turbo. v0.1 doesn't run turbo. So **v0.1 ships zero settings**. v0.2 will need an analog of `turbo.path`, but the right surface is probably JetBrains' existing Node.js Interpreter settings rather than a new top-level setting — we delegate to the platform.

## Summary — patterns to adopt for v0.1

1. **Service declaration**: `@Service(Service.Level.PROJECT)` with injected `CoroutineScope`, not the legacy `<projectService>` extension. (Source: Prettier plugin.)
2. **Workspace package enumeration**: `PackageJsonFileManager.getInstance(project).validPackageJsonFiles` mapped through `PackageJsonData.getOrCreate(...)`. (Source: Prettier and Qodana JS.)
3. **JSON-targeted inspection**: extend `LocalInspectionTool`, return `JsonElementVisitor` from `buildVisitor`, gate by `JsonProperty.name` + parent walking. (Source: solar-form-helper, dub plugin.)
4. **Quick fix that rewrites a string**: `ElementManipulators.handleContentChange(element, newText)` inside a `runWriteAction`. (Source: solar-form-helper.)
5. **Cross-file PsiReference**: extend `PsiPolyVariantReferenceBase<JsonStringLiteral>`, register via `PsiReferenceContributor` with a `PlatformPatterns.psiElement(...).withParent(...)` chain. (Source: Reference Contributor tutorial.)
6. **Caching workspace state**: `CachedValuesManager.getCachedValue` with `PsiModificationTracker.MODIFICATION_COUNT`. (Source: platform docs + Prettier.)
7. **Architecture stance from turbo-vsc**: parse the file forgivingly via PSI; let inspections fire on partially-valid documents.
