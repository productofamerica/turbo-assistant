# 0002 — JSONC support via language="JSON"

**Status:** Accepted
**Date:** 2026-04-27

`docs/v0.1-spec.md` requires every inspection and quick fix to apply equally to `turbo.jsonc`. We needed to confirm whether registering an inspection with `language="JSON"` covers `.jsonc` transparently or whether a separate `language="JSON5"` registration would be required. Test `testJsoncUnknownTaskWarns` in `DependsOnUndefinedTaskInspectionTest` loads a `turbo.jsonc` containing a `// comment` plus a violating `dependsOn` entry, runs the inspection, and asserts the warning fires on the offending string literal — it passes under the single `language="JSON"` registration in `plugin.xml`. The bundled JSON plugin parses `.jsonc` files into the same `com.intellij.json` PSI hierarchy (`JsonFile`, `JsonObject`, `JsonStringLiteral`), so a visitor registered for the JSON language sees both extensions automatically. No separate registration, no file-type predicate, no `language="JSON5"` handler is required for v0.1.

Alternatives rejected: (a) registering the inspection additionally with `language="JSON5"` — unnecessary because the existing fixture proves single-registration coverage, and double-registering risks duplicate problem reports if `.jsonc` ever gets routed to both languages; (b) dropping the `language` attribute and adding a manual file-type predicate inside the visitor — strictly more code with no observable behavior difference; (c) scoping JSONC out of v0.1 — rejected because the spec lists JSONC parity as a hard requirement and the cost of compliance turned out to be zero.
