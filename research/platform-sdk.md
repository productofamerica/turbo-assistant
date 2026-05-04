# Platform SDK research

> Compiled 2026-04-27. Cite-and-verify before relying on any specific number — JetBrains rev's the Gradle plugin and platform versions frequently.

## IntelliJ Platform Gradle Plugin v2

The plugin id is **`org.jetbrains.intellij.platform`**. The official plugin development docs land [here](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html). Apply it from `build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}
```

The above is verbatim from the [JetBrains/intellij-platform-plugin-template `build.gradle.kts`](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/build.gradle.kts) (fetched 2026-04-27). The template is the canonical reference for new plugins; it pins the plugin versions in `settings.gradle.kts` (not shown here) and applies three plugins together: Kotlin JVM, the IntelliJ Platform Gradle plugin, and the changelog plugin.

For a multi-module plugin, submodules apply the dedicated module variant: `id("org.jetbrains.intellij.platform.module")`. ([docs](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html))

### IntelliJ Platform dependency types

The `intellijPlatform { }` block under `dependencies { }` declares which IDE the plugin builds against. From the plugin template:

```kotlin
dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2025.2.6.1")
        testFramework(TestFrameworkType.Platform)
    }
}
```

(Verbatim from [build.gradle.kts](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/build.gradle.kts).)

The dependency-extension docs ([reference](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html)) list these IDE accessors: `intellijIdea(version)`, `intellijIdeaCommunity(version)`, `intellijIdeaUltimate(version)`, plus product-specific accessors like `webstorm(version)`, `phpstorm(version)`, `goland(version)`. For a JS-tooling plugin we have a choice:

1. **Build against `intellijIdeaUltimate(...)`** — pulls in the proprietary JavaScript plugin and the JSON plugin transitively. This is what the Prettier plugin does (see [prior-art.md](prior-art.md)).
2. **Build against `intellijIdeaCommunity(...)` + explicit bundled-plugin deps** — smaller download, but the JavaScript module is *not* in Community, so this fails.

Verdict for v0.1: use `intellijIdeaUltimate(...)`. The plugin still ships universally — what the consumer's IDE has is determined at runtime by the `<depends>` declarations in `plugin.xml`.

### Declaring bundled-plugin deps in Gradle

Two helpers, per the [dependency extension docs](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html):

```kotlin
intellijPlatform {
    bundledPlugin("JavaScript")             // plugins shipped with the IDE
    plugin("org.intellij.scala", "2024.1.4") // Marketplace plugins
}
```

For Turbo Assistant the bundled deps are `JavaScript` (PSI for JS/TS, the `PackageJsonFileManager`, etc.) and the JSON plugin. **The JSON plugin's id needs verification** — recent IDE versions split JSON support into its own bundled plugin, but I could not confirm the exact bundled-plugin id from the docs in one pass. Check by running `./gradlew printBundledPlugins` (a task the platform plugin provides) once the build is set up.

### Plugin verification

The Plugin Verifier task validates that the built plugin runs against every IDE in a target range without binary-incompatible references. The Gradle plugin v2 wires this up under `intellijPlatform.pluginVerification { }`. Set the IDEs to verify against — for us, recent WebStorm + IDEA Ultimate. Reference: [Plugin Verifier docs](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html).

### Sandbox testing

`./gradlew runIde` launches a sandbox IDE pre-loaded with the plugin. The platform plugin provides this task automatically; no extra config is typical. The sandbox lives under `build/idea-sandbox/` and is wiped on `clean`.

### Marketplace publishing

Per [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html):

- The Gradle task is `publishPlugin`. The plugin-template wires `patchChangelog` as a dependency so the changelog renders into `change-notes` before upload.
- Authentication uses a Marketplace **Personal Access Token** passed via the `ORG_GRADLE_PROJECT_intellijPlatformPublishingToken` env var (or `-PintellijPlatformPublishingToken=...`).
- `signPlugin` runs automatically before `publishPlugin`. Plugin signing setup is documented at [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).
- Marketplace rejects re-uploads with the same version, so bump `version` in `gradle.properties` before each publish.
- Optional release channels (beta, alpha) via:
  ```kotlin
  intellijPlatform { publishing { channels = listOf("beta") } }
  ```

## Minimum recommended platform version (2026)

The plugin template currently builds against **IDEA 2025.2.6.1** (see [build.gradle.kts](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/build.gradle.kts)), which means the template authors consider 2025.2 a sensible "current" baseline. The CLAUDE.md for this project sets a minimum of **2025.1**, which is one cycle older — that's a reasonable trade-off (2025.1 has been out long enough to be widely upgraded to, and the JSON/JavaScript PSI APIs we depend on have been stable since well before 2025).

`gradle.properties` (verbatim — [source](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/gradle.properties)):

```properties
group = org.jetbrains.plugins.template
version = 2.5.0

pluginRepositoryUrl = https://github.com/JetBrains/intellij-platform-plugin-template

# Opt-out flag for bundling Kotlin standard library -> https://jb.gg/intellij-platform-kotlin-stdlib
kotlin.stdlib.default.dependency = false

# Enable Gradle Configuration Cache
org.gradle.configuration-cache = true

# Enable Gradle Build Cache
org.gradle.caching = true
```

Note: the current template at HEAD does **not** keep `pluginVersion`, `pluginSinceBuild`, `pluginUntilBuild`, `platformVersion`, or `platformPlugins` in `gradle.properties`. The template moved toward inlining these in `build.gradle.kts`. If you want to use `gradle.properties` for them (older template style), nothing stops you, but newer template uses literal values inside `intellijPlatform.intellijIdea("2025.2.6.1")`.

The `kotlin.stdlib.default.dependency = false` line is important: it opts out of bundling Kotlin's stdlib into the plugin JAR, since the platform already ships with the right stdlib version. Don't remove that line. ([reference](https://jb.gg/intellij-platform-kotlin-stdlib))

## plugin.xml — declaring deps for a JS-targeted plugin

Canonical structure (synthesized from the [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html) reference and [Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)):

```xml
<idea-plugin>
    <id>net.example.turbo-assistant</id>
    <name>Turbo Assistant</name>
    <vendor email="...">Your Name</vendor>
    <description><![CDATA[ ... ]]></description>

    <!-- Required base layer: every plugin declares this. -->
    <depends>com.intellij.modules.platform</depends>

    <!-- JavaScript plugin: required for PackageJsonFileManager and JS PSI. -->
    <depends>JavaScript</depends>

    <!-- JSON plugin: required for JsonFile, JsonProperty, JsonStringLiteral.
         The bundled-plugin id is "com.intellij.modules.json" in older docs;
         verify against your target build before merging. -->
    <depends>com.intellij.modules.json</depends>

    <idea-version since-build="251.*"/>
    <!-- 251.* corresponds to 2025.1; verify the exact build number range
         at https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html -->

    <extensions defaultExtensionNs="com.intellij">
        <!-- inspections and reference contributors registered here -->
    </extensions>
</idea-plugin>
```

The `JavaScript` dep is what the open-source [Prettier plugin's plugin.xml](https://github.com/JetBrains/intellij-plugins/tree/master/prettierJS) and the third-party [intellij-turbo plugin](https://github.com/KartanHQ/intellij-turbo/blob/master/src/main/resources/META-INF/plugin.xml) both use. Confirmed verbatim from intellij-turbo:

```xml
<depends>com.intellij.modules.platform</depends>
<depends>JavaScript</depends>
```

### Compatibility with WebStorm + IntelliJ Ultimate + other JS IDEs

Per the [Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html) docs, declaring `<depends>JavaScript</depends>` is what gates the plugin to "WebStorm, and other products if the JavaScript plugin is installed." That's WebStorm, IDEA Ultimate (JS plugin bundled), PhpStorm, RubyMine (with JS plugin), GoLand (with JS plugin), PyCharm Professional (with JS plugin). It auto-excludes IDEs that don't ship JavaScript: IDEA Community, PyCharm Community, RubyMine without the JS plugin, etc. The Marketplace surfaces this compatibility correctly to users — no extra `<product-descriptor>` work needed for the OSS path.

### Optional config-file pattern

If we ever want optional integration with another plugin (e.g. a future Boundaries inspection that only loads if the user has some Vercel SDK plugin installed), the pattern is:

```xml
<depends optional="true" config-file="turbo-assistant-with-vercel.xml">
    com.vercel.somePlugin
</depends>
```

Per the configuration-file reference: with `optional="true"`, the plugin still loads when the dependency is absent; only the contents of `config-file` are skipped. With `optional="false"` (the default), the plugin refuses to load. Citation: [plugin-configuration-file.html](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html).

### `until-build`: leave it off

The configuration-file docs are explicit: "It's highly recommended not to set `until-build`." The Marketplace will gate the plugin against newer builds via `since-build` only, leaving forward-compatibility to the verifier rather than a hard fence.

## Open questions / things I could not fully verify

1. The exact bundled-plugin id for the JSON plugin in 2025.1+. The legacy id `com.intellij.modules.json` is what every example in JetBrains' own docs uses, but newer IDE versions may have introduced a separate plugin id. Verify by running `./gradlew printBundledPlugins` (or by reading the IDE's `plugins/` directory) before merging the first plugin.xml.
2. Whether the IntelliJ Platform Gradle Plugin v2 has reached 2.x.x at a specific minor — one source page in this research suggested `2.15.0` but the value isn't pinned in the plugin template I fetched. Use `latest.release` in `settings.gradle.kts` or pin to the version the plugin template uses at the moment you fork it.
3. Whether `until-build` is auto-set by the Marketplace's verification step. The docs imply not, but worth confirming on first publish.
