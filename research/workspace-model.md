# Workspace model research

> Compiled 2026-04-27. The JavaScript plugin is proprietary and shipped only with WebStorm/Ultimate; its source is **not** in `JetBrains/intellij-community`. The class names and signatures below are confirmed from open-source consumers (Prettier plugin, Qodana JS) in `JetBrains/intellij-plugins`, which compile against the binary JavaScript plugin and use it the way we will.

## PackageJsonFileManager — public API surface

The fully-qualified class name is **`com.intellij.javascript.nodejs.packageJson.PackageJsonFileManager`**. Confirmed from two independent open-source usages:

- [PrettierConfiguratorService.kt](https://github.com/JetBrains/intellij-plugins/blob/master/prettierJS/src/com/intellij/prettierjs/PrettierConfiguratorService.kt) (JetBrains' own Prettier plugin).
- [JsProjectConfigurator.kt](https://github.com/JetBrains/intellij-plugins/blob/master/qodana/js/src/org/jetbrains/qodana/js/JsProjectConfigurator.kt) (Qodana JS).

### What's stable (used by JetBrains' own open-source plugins)

```kotlin
import com.intellij.javascript.nodejs.packageJson.PackageJsonFileManager
import com.intellij.javascript.nodejs.PackageJsonData

// Project-level service
val mgr = PackageJsonFileManager.getInstance(project)

// Returns every resolvable package.json in the project (already validated).
// Java getter: getValidPackageJsonFiles(): Collection<VirtualFile>
// Kotlin property accessor: validPackageJsonFiles
val files: Collection<VirtualFile> = mgr.validPackageJsonFiles

// Per-file data is lazily computed and cached
val data: PackageJsonData = PackageJsonData.getOrCreate(file)

// Methods used in the Prettier plugin:
data.containsOneOfDependencyOfAnyType(packageName: String): Boolean
data.hasConfigSection(): Boolean
data.packageJsonFile: VirtualFile
```

The Prettier plugin's pattern (verbatim from `PrettierConfiguratorService.kt`):

```kotlin
val packageJsonFiles = PackageJsonFileManager.getInstance(project)
    .getValidPackageJsonFiles()
    .map { PackageJsonData.getOrCreate(it) }
    .filter { it.containsOneOfDependencyOfAnyType(PrettierUtil.PACKAGE_NAME) }
```

That's the canonical "find every package.json that depends on X" idiom and we'll use a near-identical shape to find every package that defines a script `<task>` for our `dependsOn` resolver.

The Qodana plugin's pattern (verbatim from `JsProjectConfigurator.kt`):

```kotlin
val packageJson = PackageJsonFileManager.getInstance(project)
    .validPackageJsonFiles
    .minByOrNull { it.path }
```

That's the "find the workspace-root package.json" idiom — pick the shallowest one. For Turbo Assistant v0.1, the workspace root is the directory containing the topmost `turbo.json`, not necessarily the shallowest package.json, so we'll need slightly different logic (see "Workspace root resolution" below).

### What's internal / off-limits

Anything in `com.intellij.lang.javascript.buildTools.npm.rc.*` that I saw used in Qodana is borderline. Examples:

```kotlin
// Used in Qodana JsProjectConfigurator — these compile but are likely tagged
// @ApiStatus.Internal in the binary. Do NOT depend on them in v0.1.
import com.intellij.lang.javascript.buildTools.npm.rc.NpmCommand
import com.intellij.lang.javascript.buildTools.npm.rc.NpmRunProfileState
```

These are part of the Run Configuration machinery for npm tasks. We'll need them in v0.2 (run integration) but not before. When v0.2 lands, the first thing to do is grep for `@ApiStatus.Internal` in the actual JAR (open the JS plugin's JAR in IDEA and look at the source attached) and pick the stable wrapper rather than the internal class.

**Rule of thumb**: classes documented in the [Plugin SDK docs](https://plugins.jetbrains.com/docs/intellij/welcome.html) or used in the open-source intellij-plugins repo are stable. Classes you can only find by source-decompiling Ultimate are not. When in doubt, prefer the documented surface.

## Resolving workspace packages

A "workspace" in monorepo land is the set of packages whose locations are listed (typically as glob patterns) in some root config:

| Package manager | Workspace declaration | Where it lives |
|---|---|---|
| npm | `"workspaces": [...]` | root `package.json` |
| yarn classic | `"workspaces": [...]` | root `package.json` (same shape as npm) |
| yarn berry | `"workspaces": [...]` | root `package.json`; `.yarnrc.yml` for resolver config |
| pnpm | `packages: [...]` (YAML) | `pnpm-workspace.yaml` at root |
| bun | `"workspaces": [...]` | root `package.json` (same shape as npm) |

### npm / yarn / bun: the easy case

`PackageJsonData.getOrCreate(file)` parses the `workspaces` field of a `package.json`. The exact accessor varies — possibilities include `data.topLevelProperty("workspaces")` or a dedicated `workspaces` field — verify against the JS plugin's actual API surface in IDE auto-complete. Either way, **no separate file format**: it's all in `package.json`, which the platform already parses for us.

The matching of glob patterns in `workspaces` against directories on disk is, ironically, the part where there's **no** platform helper. We need a glob matcher (`com.intellij.openapi.util.io.FileUtil` has limited utilities; `org.intellij.lang.regexp` is overkill; the cleanest path is to depend on a small lib like `glob` semantics implemented locally). Or skip globbing entirely and just walk the project for `package.json` files via `PackageJsonFileManager.validPackageJsonFiles`, then compute "is this in the workspace?" by checking that some `workspaces` glob in some root would match it.

For v0.1 we likely don't need to expand the globs at all — `PackageJsonFileManager.validPackageJsonFiles` already gives us every package.json the IDE has indexed, and that's the universe of valid workspace package names. **Reuse it; don't re-derive it**.

### pnpm: needs manual YAML parsing

`pnpm-workspace.yaml` is a YAML file that the JavaScript plugin does **not** parse for us. There is no `PnpmWorkspaceManager` in the platform. Confirmed by:

- No results in `JetBrains/intellij-plugins` repo when searching for `pnpm-workspace`.
- Multiple third-party plugins ([`Lutonite/intellij-pnpm-catalog`](https://github.com/Lutonite/intellij-pnpm-catalog), [`skoch13/intellij-pnpm-catalog-lens`](https://github.com/skoch13/intellij-pnpm-catalog-lens)) parse it themselves using snakeyaml.

The `intellij-pnpm-catalog` pattern (from [`PnpmWorkspaceService.kt`](https://github.com/Lutonite/intellij-pnpm-catalog/blob/master/src/main/kotlin/dev/lutonite/pnpmcatalog/PnpmWorkspaceService.kt)):

```kotlin
import org.yaml.snakeyaml.Yaml
import org.jetbrains.yaml.psi.YAMLKeyValue

@Service(Service.Level.PROJECT)
class PnpmWorkspaceService(private val project: Project) {

    companion object {
        const val WORKSPACE_FILE_NAME: String = "pnpm-workspace.yaml"

        @JvmStatic
        fun getInstance(project: Project): PnpmWorkspaceService = project.service()
    }

    private var workspaceFile: VirtualFile? = null
    private var defaultCatalog: Map<String, String>? = null
    private val lock = Any()

    fun getWorkspaceFile(): VirtualFile? {
        synchronized(lock) {
            return findWorkspaceFile()
        }
    }
    // ... parsePnpmWorkspace() reads with snakeyaml ...
}
```

Two viable approaches for Turbo Assistant:

1. **Use the YAML PSI** (`org.jetbrains.yaml.psi.YAMLKeyValue`, `YAMLSequence`, etc.). This is what the JetBrains YAML plugin gives us. Pro: integrates with PSI invalidation automatically. Con: more boilerplate to read sequence values.
2. **Use snakeyaml directly** (already on the IDE classpath). Pro: less boilerplate. Con: you're parsing the file as text, so you have to manage caching + invalidation manually.

For v0.1: pnpm-workspace handling is **out of scope** — the spec only requires inspections that work against `turbo.json` and `package.json`. But inspection B (package-qualified task references like `web#build`) needs to know which package names exist in the workspace, which is exactly what pnpm-workspace.yaml describes for pnpm users.

**Pragmatic compromise**: for v0.1, derive the package name set from `PackageJsonFileManager.validPackageJsonFiles` (read each file's `name` property). That captures every actually-present package.json regardless of whether the user is on npm/yarn/pnpm/bun. We don't need to parse `pnpm-workspace.yaml` to enumerate the package names — we get them for free. Move pnpm-workspace.yaml parsing to v0.4 (boundaries) when we actually need to know the *intended* membership rather than just the *actual* set.

This is a meaningful design decision that should be explicit in v0.1's design doc. Calling it out in the "should change docs/v0.1-spec.md" section at the bottom of this research session.

### Yarn berry — `.yarnrc.yml` and PnP

Yarn berry's plug-and-play resolver is a separate concern from workspace topology. Workspace topology is still in `package.json` (`workspaces` field). The `.yarnrc.yml` file controls *how packages are resolved at runtime*, not what packages exist. For our purposes (enumerating package names), berry behaves the same as classic. Ignore `.yarnrc.yml` for v0.1.

### bun

Bun reads `workspaces` from `package.json` exactly like npm. No separate file. Treat as "npm with a different lockfile."

## CachedValuesManager + ModificationTracker — the canonical pattern

The platform's general caching pattern is `CachedValuesManager.getCachedValue(holder, computation)`, where the `computation` returns a `CachedValueProvider.Result.create(value, dependencies...)`. The dependencies are `ModificationTracker` instances — when any of their `getModificationCount()` changes, the cache invalidates.

Source: [CachedValue.java](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/util/CachedValue.java) and [CachedValuesManager.java](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/util/CachedValuesManager.java).

### Pattern for a project-wide cache invalidated on any PSI change

```kotlin
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.project.Project

private val WORKSPACE_PACKAGES_KEY: Key<CachedValue<Map<String, VirtualFile>>> =
    Key.create("turbo.workspacePackages")

fun getWorkspacePackages(project: Project): Map<String, VirtualFile> {
    return CachedValuesManager.getManager(project).getCachedValue(
        project,                          // user data holder
        WORKSPACE_PACKAGES_KEY,           // unique key
        {                                 // CachedValueProvider
            val packages = PackageJsonFileManager.getInstance(project)
                .validPackageJsonFiles
                .mapNotNull { vf ->
                    val data = PackageJsonData.getOrCreate(vf)
                    data.name?.let { it to vf }
                }
                .toMap()
            CachedValueProvider.Result.create(
                packages,
                PsiModificationTracker.MODIFICATION_COUNT
            )
        },
        false                             // trackValue: false is correct here
    )
}
```

`PsiModificationTracker.MODIFICATION_COUNT` ticks on **any** PSI change anywhere in the project. That's the safe default — it invalidates whenever a `package.json` or `turbo.json` (or anything else) changes. For v0.1 this is fine; "any change anywhere" is overkill but cheap.

If profiling later shows this re-computation is hot, the optimization is to derive a finer-grained tracker:

```kotlin
PsiModificationTracker.SERVICE.getInstance(project)
    .forLanguages { it === JsonLanguage.INSTANCE }
```

— but **don't optimize prematurely**. The Prettier plugin doesn't bother; neither should v0.1.

### Key gotcha

The `CachedValueProvider.Result.create` lambda runs under a read action. **Don't access PSI of a different file from inside it via random traversal**, because that breaks the dependency-tracking contract. Stick to:

1. Read PSI of the file the cache is keyed to (`project` in the example above is the holder, so any PSI is fair game *as long as* you list every relevant tracker in the dependencies).
2. Always include `PsiModificationTracker.MODIFICATION_COUNT` (or a more specific tracker) as a dependency.

JetBrains' [community forum thread on this exact concern](https://intellij-support.jetbrains.com/hc/en-us/community/posts/9512627649554-Is-it-safe-to-cache-any-PSI-element-if-I-use-PsiModificationTracker-MODIFICATION-COUNT) is the authoritative discussion. The TL;DR: `PsiModificationTracker.MODIFICATION_COUNT` is safe for caching anything PSI-derived as long as you scope the holder correctly.

### Using `getProjectPsiDependentCache` as a shortcut

For per-PSI-element caching (e.g. "for a given JsonStringLiteral, what is its resolved task target?"), `CachedValuesManager.getProjectPsiDependentCache(element) { ... }` is a convenience that handles the modification tracker automatically. Pattern:

```kotlin
val target = CachedValuesManager.getProjectPsiDependentCache(literal) {
    resolveTaskName(it)
}
```

This is the cleanest pattern for our PsiReference resolvers (where each individual `JsonStringLiteral` has its own cached resolution result).

## Internal APIs we'd be tempted to use, and alternatives

| Tempting (likely internal) | Stable alternative |
|---|---|
| `JSLibraryUtil.getNodeModulesDir(...)` | Iterate `PackageJsonFileManager.validPackageJsonFiles` and check parent directory yourself. |
| `NpmUtil.PACKAGE_LOCK_JSON_FILENAME` constants for lockfile names | Hardcode the lockfile names ourselves — they're stable strings, not API. |
| `NpmManager.getInstance(project).packageRef` (used by Qodana for actual `npm` invocation) | We don't shell out in v0.1. Defer to v0.2. |
| `JSJsonSchemaProviderBase` (used by Prettier for in-JS schema) | We don't ship a schema in v0.1. The base class is fine for v0.4 if needed. |
| Anything under `com.intellij.lang.javascript.buildTools.npm.rc` | These are run-config internals. v0.2 problem. |

When unsure, run `gh search code "<class name>" repo:JetBrains/intellij-plugins` — if it appears in JetBrains' own open-source plugins (Prettier, Vue, Angular, Astro, prisma), it's de facto stable. If it doesn't, treat it as internal.

## Open questions

1. ~~The exact accessor on `PackageJsonData` for the `name` field and the `workspaces` field — I cited `data.name` and `data.containsOneOfDependencyOfAnyType(...)` but did not see the full method list.~~ **Resolved 2026-04-27** — see "Corrections" below.
2. Whether `PackageJsonFileManager.validPackageJsonFiles` honors `.gitignore` / excluded folders. If it doesn't, an `node_modules/foo/package.json` will pollute the workspace package set. Test with a fixture before relying on it.
3. Whether IDEA Ultimate and WebStorm ship the same `PackageJsonFileManager` or whether one is a subclass. Should be identical (both come from the JS plugin) but worth a sanity check.

## Corrections (verified 2026-04-27)

`PackageJsonData` (`com.intellij.javascript.nodejs.PackageJsonData`, in `plugins/javascript-plugin/lib/javascript-plugin.jar` of IDEA 2025.2.6.1) has **no** `getScripts()` accessor. Verified via `javap -public`. Public API exposes `getName`, `getWorkspaces`, `getAllDependencies`, `getAllDependencyEntries`, `getTopLevelProperties` (top-level key names — e.g. returns `"scripts"`, not the names inside it), `containsOneOfDependencyOfAnyType`, `getMain`, `getTypings`, `getVersion`, etc. So `data.name` (used in `getWorkspacePackages` above) is correct; reading scripts is not supported by `PackageJsonData`.

**To enumerate `package.json` script names**, traverse JSON PSI directly:

```kotlin
val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? JsonFile ?: return emptySet()
val rootObj = psiFile.topLevelValue as? JsonObject ?: return emptySet()
val scriptsObj = rootObj.findProperty("scripts")?.value as? JsonObject ?: return emptySet()
scriptsObj.propertyList.mapTo(mutableSetOf()) { it.name }
```

Cache per file via `CachedValuesManager.getProjectPsiDependentCache(psiFile) { compute(it) }` and fold into a project-wide cache keyed on `PsiModificationTracker.MODIFICATION_COUNT`. `PackageJsonFileManager.getInstance(project).validPackageJsonFiles` remains the source for the file set. Skip files where `PsiManager.findFile` returns null or yields a non-`JsonFile`.
