# JSON PSI research

> Compiled 2026-04-27. The JSON plugin's API has been stable since well before 2025; the patterns below should hold across 2025.x and 2026.x platform versions, but verify class FQNs against your target build.

## Core PSI types

The JSON PSI lives under **`com.intellij.json.psi`**. The hierarchy you'll touch in v0.1:

| Class | Role |
|---|---|
| `JsonFile` | Top-level PSI file for `.json` (and the same hierarchy serves `.jsonc` / `.json5`, see below). |
| `JsonObject` | An object node `{ ... }`. `propertyList: List<JsonProperty>` and `findProperty(name): JsonProperty?`. |
| `JsonProperty` | A `"name": value` pair. `.name`, `.nameElement: JsonValue`, `.value: JsonValue?`. |
| `JsonValue` | Sealed-ish base for all values: `JsonStringLiteral`, `JsonNumberLiteral`, `JsonBooleanLiteral`, `JsonObject`, `JsonArray`, `JsonNullLiteral`. |
| `JsonStringLiteral` | A quoted string. `.value: String` returns the unescaped content (without the surrounding quotes); `.textRange` includes the quotes. |
| `JsonArray` | `[...]`. `.valueList: List<JsonValue>`. |
| `JsonElementVisitor` | Visitor base with `visitProperty`, `visitObject`, `visitStringLiteral`, etc. The standard entry point for `LocalInspectionTool.buildVisitor`. |

These classes are confirmed by their use in the [solar-form-helper inspections](https://github.com/GreenBudgie/solar-form-helper/blob/master/src/main/kotlin/com/solanteq/solar/plugin/inspection/form/InvalidFormNameDeclarationInspection.kt) and the [intellij-dlanguage `DubPackageDependencyVisitor`](https://github.com/intellij-dlanguage/intellij-dlanguage/blob/master/dub/src/main/kotlin/io/github/intellij/dub/packageConfig/inspections/DubPackageDependencyVisitor.kt) — both fetched 2026-04-27.

### Helpful ergonomics

- **Unquoted string content**: `JsonStringLiteral.value` (Kotlin property; Java getter `getValue()`). Use this rather than `.text.removeSurrounding("\"")` because it correctly handles escapes.
- **Range without quotes**: solar-form-helper uses an extension `JsonStringLiteral.textRangeWithoutQuotes` — that's not platform-provided, but it's a small utility worth replicating. Reports look better when the inspection underline excludes the quotes.
- **Quick fixes that change the string content**: use `ElementManipulators.handleContentChange(element, newText)`. This handles the quoting for you. Pattern from solar-form-helper:
  ```kotlin
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      runWriteAction {
          ElementManipulators.handleContentChange(element, formFileName)
      }
  }
  ```

## JsonSchemaService — coexisting with the existing $schema validation

The platform's JSON schema engine lives under **`com.jetbrains.jsonSchema.ide.JsonSchemaService`** (note the package: `com.jetbrains`, *not* `com.intellij`). Source: [JsonSchemaService.java](https://github.com/JetBrains/intellij-community/blob/master/json/backend/src/com/jetbrains/jsonSchema/ide/JsonSchemaService.java).

Public API surface (extracted from the source):

```java
public interface JsonSchemaService {
    static JsonSchemaService get(@NotNull Project project);

    boolean isApplicableToFile(@Nullable VirtualFile file);
    boolean isSchemaFile(@NotNull VirtualFile file);
    boolean isSchemaFile(@NotNull JsonSchemaObject schemaObject);

    @Nullable JsonSchemaObject getSchemaObject(@NotNull VirtualFile file);
    @Nullable JsonSchemaObject getSchemaObject(@NotNull PsiFile file);
    @Nullable JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile);
}
```

For Turbo Assistant, **we should not write our own JSON Schema validation for `turbo.json`**. The CLAUDE.md "explicitly not doing" list captures this. The platform already validates against `$schema` (which `turbo.json` uses by default). Our inspections target only what schema validation can't catch — cross-file references.

### Avoiding double-reporting

For each of our inspections (A, B, C in `docs/v0.1-spec.md`), the underlying problem is *not* a schema violation — schema knows the field is a string, but doesn't know whether the string resolves to anything. So overlap with `$schema` validation is naturally zero. No special "is schema valid?" gate is needed before reporting.

If we ever wanted to *suppress* our own inspection when a competing schema-driven message is present, the pattern is `JsonSchemaService.get(project).isApplicableToFile(file)` — `false` means schema validation is off for this file, `true` means it's on.

### Providing a schema (we won't, in v0.1)

For reference: the extension point is **`JavaScript.JsonSchema.ProviderFactory`** (note the lead-in is `JavaScript`, not `com.intellij`). Implementations extend `JsonSchemaProviderFactory`. Real-world example from the Prettier plugin:

```java
public class PrettierConfigJsonSchemaInJsProvider extends JSJsonSchemaProviderBase {
    @Override
    public boolean isAvailable(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = file == null ? null : CompletionUtil.getOriginalOrSelf(file).getVirtualFile();
        if (!PrettierUtil.isJSConfigFile(virtualFile)) return false;
        return isInTopLevelObject(element) &&
               isInSupportedArea(element, JSJsonSchemaProviderBase::computeDefaultModuleExportsAreas);
    }

    @Override
    public VirtualFile getSchemaFile() {
        return loadFile(PrettierConfigJsonSchemaInJsProvider.class, "/" + PrettierConfigJsonSchemaProviderFactory.SCHEMA_FILE_NAME);
    }
}
```

(Verbatim from [PrettierConfigJsonSchemaInJsProvider.java](https://github.com/JetBrains/intellij-plugins/blob/master/prettierJS/src/com/intellij/prettierjs/config/PrettierConfigJsonSchemaInJsProvider.java).)

We do **not** need this for v0.1. Document it here only so future-us knows where to look if v0.4 (boundaries) ever needs to ship a richer schema.

## PsiReferenceContributor — scoping references to JSON paths

Per the [Reference Contributor tutorial](https://plugins.jetbrains.com/docs/intellij/reference-contributor.html), the registration extension point is `com.intellij.psi.referenceContributor`:

```xml
<extensions defaultExtensionNs="com.intellij">
    <psi.referenceContributor language="JSON"
        implementation="net.example.turboassistant.references.TurboTaskReferenceContributor"/>
</extensions>
```

The contributor uses `PsiReferenceRegistrar.registerReferenceProvider(pattern, provider)`:

```java
final class SimpleReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiLiteralExpression.class),
            new PsiReferenceProvider() {
                @Override
                public PsiReference[] getReferencesByElement(
                    @NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                    // Implementation handles string literals
                }
            });
    }
}
```

(Verbatim from the [Reference Contributor tutorial](https://plugins.jetbrains.com/docs/intellij/reference-contributor.html).)

For Turbo Assistant we want the pattern to match string literals **inside `dependsOn`** specifically — not every JsonStringLiteral in the file. The pattern composes:

```kotlin
PlatformPatterns.psiElement(JsonStringLiteral::class.java)
    .withParent(PlatformPatterns.psiElement(JsonArray::class.java)
        .withParent(PlatformPatterns.psiElement(JsonProperty::class.java)
            .withName("dependsOn")))
```

This says: a string literal whose parent is a JSON array whose parent is a JSON property named `dependsOn`. The `withName(...)` filter lives on `PsiJsonElementPattern`-style classes (provided by the JSON plugin). The exact builder name varies — verify against `com.intellij.patterns` in IDE auto-complete.

The reference class itself: extend `PsiReferenceBase<JsonStringLiteral>` (single resolve) or `PsiPolyVariantReferenceBase<JsonStringLiteral>` (multi-resolve, e.g. when a task name could resolve to multiple definitions across packages). The tutorial's example:

```java
final class SimpleReference extends PsiPolyVariantReferenceBase<PsiElement> {
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        Project project = myElement.getProject();
        List<SimpleProperty> properties = SimpleUtil.findProperties(project, key);
        List<ResolveResult> results = new ArrayList<>();
        for (SimpleProperty property : properties) {
            results.add(new PsiElementResolveResult(property));
        }
        return results.toArray(new ResolveResult[0]);
    }

    @Override
    public Object[] getVariants() { /* completion variants */ }
}
```

For our **`dependsOn` task references**, multi-resolve is the right fit: a bare task name like `"build"` could match every package's `build` script. Returning multiple `ResolveResult`s lets the user pick.

For our **`package.json script → turbo.json task`** reference, single-resolve is right — a script name resolves to exactly one task definition (or none, in which case `resolve()` returns null and the click is silently a no-op, which matches the v0.1 spec).

### Where to register it

The `psi.referenceContributor language="JSON"` registration applies to all JSON files. To ALSO cover `package.json` (which uses the same JSON parser), the same registration suffices — the pattern's `withName("scripts")` (or wherever the script names live) is what gates it.

## LocalInspectionTool — the worked v0.1 pattern

The clearest end-to-end real-world example I found is [InvalidFormNameDeclarationInspection.kt from solar-form-helper](https://github.com/GreenBudgie/solar-form-helper/blob/master/src/main/kotlin/com/solanteq/solar/plugin/inspection/form/InvalidFormNameDeclarationInspection.kt) — a JSON-targeted inspection with a quick fix that uses `ElementManipulators` to rewrite a string. Verbatim:

```kotlin
package com.solanteq.solar.plugin.inspection.form

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonElementVisitor
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators

class InvalidFormNameDeclarationInspection : FormInspection() {

    override fun buildVisitor(holder: ProblemsHolder) = Visitor(holder)

    class Visitor(private val holder: ProblemsHolder) : JsonElementVisitor() {

        override fun visitProperty(property: JsonProperty) {
            if (!FormPsiUtils.isAtTopLevelObject(property)) return
            if (property.name != "name") return

            val propertyValue = property.value as? JsonStringLiteral ?: return
            val formFileName = property.containingFile?.originalFile?.virtualFile?.nameWithoutExtension ?: return
            val declaredFormName = propertyValue.value

            if (formFileName == declaredFormName) return

            holder.registerProblem(
                propertyValue,
                SolarBundle.message("inspection.message.invalid.form.name.declaration"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                propertyValue.textRangeWithoutQuotes,
                RenameFormNameDeclarationFix(formFileName)
            )
        }
    }

    class RenameFormNameDeclarationFix(private val formFileName: String) : LocalQuickFix {

        override fun getFamilyName() = SolarBundle.message("intention.family.name.rename.form.name")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            runWriteAction {
                ElementManipulators.handleContentChange(element, formFileName)
            }
        }
    }
}
```

This is essentially the template for all three of our v0.1 inspections. Notes:

- **`buildVisitor` returns a `JsonElementVisitor`** subclass — that's the JSON-aware visitor; the platform invokes it on every PSI element while traversing.
- **`registerProblem` overload with `TextRange`** — the third positional arg `propertyValue.textRangeWithoutQuotes` makes the squiggly underline skip the quotes. (The extension `textRangeWithoutQuotes` is from solar-form-helper's own `util` package; we'll need our own equivalent.)
- **`ElementManipulators.handleContentChange`** is the supported way to rewrite a `JsonStringLiteral`'s content. Don't `replace()` the PSI directly.

For a **cross-property visitor** (which we need for inspection A — walking up from a `dependsOn` array entry to the surrounding task name), the [DubPackageDependencyVisitor](https://github.com/intellij-dlanguage/intellij-dlanguage/blob/master/dub/src/main/kotlin/io/github/intellij/dub/packageConfig/inspections/DubPackageDependencyVisitor.kt) shows the pattern of climbing the PSI tree to gate by parent-property name:

```kotlin
abstract class DubJsonDependencyVisitor(val holder: ProblemsHolder) : JsonElementVisitor() {

    override fun visitProperty(o: JsonProperty) {
        val currentLibParentObject = o.parent as? JsonObject ?: return
        val parentBlockProperty = currentLibParentObject.parent as? JsonProperty ?: return
        if (parentBlockProperty.name != "dependencies")
            return
        val topLevelObject = parentBlockProperty.parent as? JsonObject ?: return
        if (topLevelObject.parent !is PsiFile)
            return

        val dependency = JsonPackageDependency(o.name, o, collectElements(o))
        visitPackage(dependency)
    }
}
```

For our use, the same parent-walking pattern gates inspection A: starting from a string literal, climb up `JsonArray → JsonProperty(name="dependsOn") → JsonObject (the task definition) → JsonProperty (the task name)` to know which task we're inside. Don't re-implement this with `instanceof` chains — use `PsiTreeUtil.getParentOfType(element, JsonProperty::class.java)` for safer traversal.

### Inspection registration in plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">
    <localInspection
        language="JSON"
        shortName="TurboUnresolvedDependsOn"
        displayName="Unresolved task in dependsOn"
        groupName="Turborepo"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="net.example.turboassistant.inspections.UnresolvedDependsOnInspection"/>
</extensions>
```

(Adapted from the [Code Inspections](https://plugins.jetbrains.com/docs/intellij/code-inspections.html) docs.)

### Fixture test pattern

The platform docs ([Code Inspections](https://plugins.jetbrains.com/docs/intellij/code-inspections.html)) give the canonical `BasePlatformTestCase` pattern:

```java
public class ComparingStringReferencesInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(ComparingStringReferencesInspection.class);
    }

    public void testEqualsComparison() {
        myFixture.configureByFile("Eq.java");
        myFixture.launchAction(myFixture.findSingleIntention("Replace with equals()"));
        myFixture.checkResultByFile("Eq.after.java");
    }
}
```

Adapt for our use: input fixture `UnresolvedDependsOn.json` → quick fix → expected output `UnresolvedDependsOn.after.json`. Files live in `src/test/testData/inspections/UnresolvedDependsOn/`.

To assert the inspection fires (without applying a quick fix), use `myFixture.testHighlighting(...)` and embed `<warning>...</warning>` markers in the fixture file. See the platform docs for the marker syntax.

## JSONC (`.jsonc`) support

This is where the docs were thinnest — I could not find a definitive page on JSON5/JSONC handling in the JetBrains docs site (the `json.html` page returned 404 in my fetches; not sure whether it was relocated or removed). Best understanding from cross-referencing other plugins:

- The platform's JSON plugin parses `.json`, `.jsonc`, and `.json5` into the **same `JsonFile` PSI hierarchy**. JSONC permits comments (`//` and `/* */`); JSON5 additionally permits trailing commas, single quotes, etc. The PSI types (`JsonObject`, `JsonProperty`, `JsonStringLiteral`) are shared.
- Practical implication: inspections registered with `language="JSON"` apply to all three. The vercel turbo-vsc extension confirms by treating `turbo.json` and `turbo.jsonc` interchangeably (it uses the [`jsonc-parser`](https://github.com/microsoft/node-jsonc-parser) NPM package which does the same).
- **What I could not verify**: whether JSON5-specific syntax (trailing commas, unquoted keys) keeps PSI parseability or whether it falls into a separate `Json5File`. If `turbo.jsonc` allows trailing commas in practice, write a fixture test for it before declaring v0.1 "done." This is a real risk — the spec promises JSONC parity.

**Action item for v0.1 builder session**: add a minimal `.jsonc` fixture (with `//` comments) for inspection A and confirm the visitor still fires correctly. If it doesn't, JSONC support needs the inspection registered with `language="JSON5"` *as well*, or with `language=""` plus a file-type guard. Don't ship without verifying.

## Open questions

1. The exact Pattern DSL builder for `JsonProperty` — the JSON plugin ships a class like `PsiJsonElementPattern` but its public DSL surface isn't documented online; figure it out from auto-complete in `com.intellij.patterns.PlatformPatterns` once the build is set up.
2. Whether `JsonStringLiteral` exposes a stable "range without quotes" API (`textRangeWithoutQuotes` is solar-form-helper's own extension; the platform may have something equivalent that's just not surfaced in the docs).
3. JSON5 vs JSONC PSI: see the JSONC section above.
