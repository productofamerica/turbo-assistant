# Roadmap

Order matters; time doesn't. Each scheduled item lists its dependencies, a rough T-shirt size, and the signal that would kill it.

## v0.1 (current): Cross-file validation in turbo.json

Catches mistakes the JSON schema can't: `dependsOn` references that resolve to nothing, package-qualified tasks pointing at packages that don't exist, and malformed `extends` in package-level turbo.json. Adds cmd-click navigation between `turbo.json` and `package.json`.

- Dependencies: none.
- Size: M.
- Kill criterion: JetBrains ships an official Turborepo plugin that covers the same surface before we do.

## v0.2: Env var leak detection

Inspection that cross-references `env` and `passThroughEnv` declarations in `turbo.json` against `process.env.*` reads inside the package's source. Flags reads of env vars not declared on the task. Turborepo's #1 caching footgun.

### Reordering rationale

Originally scheduled as v0.3 behind run integration. Promoted to v0.2 because env var detection prevents a concrete class of CI cache-poisoning bug, while run integration is convenience polish over a workflow users already have via the terminal. The plugin's first post-validation release should lead with bug prevention; that is the strongest pitch.

- Dependencies: v0.1 (turbo.json parse model).
- Size: L (requires walking JS/TS PSI inside each package and resolving its enclosing task).
- Kill criterion: false-positive rate on real repos exceeds ~10% after a calibration pass and can't be brought down without a project-wide model.

## v0.3: Run integration

Gutter icons next to tasks in `turbo.json` that launch a JetBrains Run Configuration invoking `turbo run <task>` (with the appropriate package filter when defined under a workspace). Auto-generated run configurations per task.

- Dependencies: v0.1 (consumes the parsed task model).
- Size: M.
- Kill criterion: usage telemetry from v0.1 shows users overwhelmingly run turbo from the terminal and ignore gutter run icons in adjacent ecosystems (e.g., npm scripts gutter usage <5%).

## v0.4: Boundaries inspections

Surfaces violations of Turborepo's `boundaries` config (cross-package imports that escape the declared graph). Quick fixes to add the missing dependency to `package.json` or mark the import as intentional.

- Dependencies: v0.2 (shares the cross-file PSI walker).
- Size: L.
- Kill criterion: Turborepo deprecates or significantly reshapes the boundaries feature before we ship.

## v1.0: Package graph tool window

Interactive view of the workspace package graph and per-task dependency graph, with click-through to the relevant `turbo.json` or `package.json`. Filtering by task, package, or affected-by-change set.

- Dependencies: v0.1–v0.4 (consumes their parsed models; does not duplicate them).
- Size: XL.
- Kill criterion: feature complexity outruns the value users get from `turbo ls` and `turbo run --graph` on the CLI.

## Backlog (unscheduled)

- **Run summary viewer** — pretty-prints `turbo run --summarize` JSON inside the IDE.
- **Codemod runner UI** — panel that lists Turborepo codemods and runs the selected one against the project.
- **Microfrontends proxy integration** — IDE-side routing and awareness for the Vercel microfrontends proxy.
- **Filter autocomplete in terminal** — completion for `--filter` arguments inside the IDE terminal.

## Explicitly not doing

- **JSON schema validation for turbo.json.** JetBrains already handles this via `$schema`; reimplementing it is duplicate, lower-quality work.
- **Project scaffolding wizard.** `create-turbo` is the supported path; an in-IDE wizard would lag the CLI and fragment the surface.
- **Reimplementing the turbo CLI in Kotlin.** We integrate with it; we don't replace it.
