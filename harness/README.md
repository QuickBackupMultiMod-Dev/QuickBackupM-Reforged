# Functional test harness

This module boots a **real** Minecraft server (and, headlessly, a client) for every release in the
current branch's declared support range and exercises the mod end to end — `/qb make`, `/qb list`,
`/qb restore` + `/qb confirm`, `/qb export`, `/qb delete` — plus regression checks for the world-nesting
(#56), full-backup-rotation (#55) and schedule-interference (#54) bugs.

It is deliberately dependency-light (only H2 + JUnit) and has **no** Loom / Architectury / Minecraft
dependency of its own: it drives an already-built, remapped mod jar out of process. Everything it needs
about the game — the server, the loader, libraries, assets — is downloaded and cached at run time.

## Two test tiers

| Task | What it runs | When |
|---|---|---|
| `test` | Fast, offline unit tests: version-range resolution, xfail-marking parser, report writer | Every push / PR (via `build.yml`) |
| `functionalTest` | Boots a real server/client per supported version | Manual dispatch, and as a release gate |

The split is by JUnit tag: functional scenarios are `@Tag("functional")`, and `test` excludes that tag
while `functionalTest` includes only it.

## Running locally

Replace `<mod_id>` with the value of `mod_id` in `gradle.properties` (currently
`quickbakcupmulti_reforged`).

```bash
# Fast unit tests only:
./gradlew :<mod_id>-harness:test

# See the resolved (mc, loader) matrix for this branch:
./gradlew :<mod_id>-harness:printMcMatrix
cat build/mc-matrix.json

# Full functional run for the whole support range (slow — downloads gigabytes on a cold cache):
./gradlew :<mod_id>-harness:functionalTest

# Narrow it down while iterating:
./gradlew :<mod_id>-harness:functionalTest \
  -Pqbm.versions=1.21,1.21.3 \
  -Pqbm.loaders=fabric \
  -Pqbm.sides=server \
  -Pqbm.scenarios=lifecycle
```

An absent filter means "everything"; `-Pqbm.versions=1.21` narrows to exactly that version. The four
filters are `qbm.versions`, `qbm.loaders`, `qbm.sides` (`server`/`client`), `qbm.scenarios`.

### JDK requirements

Minecraft pins a minimum JDK per version and refuses to boot below it. The harness uses its own JDK when
new enough; otherwise point it at a matching one:

```bash
# Either a system property:
./gradlew :<mod_id>-harness:functionalTest -Dqbm.java21=/path/to/jdk21/bin/java
# or an environment variable:
export JAVA21_HOME=/path/to/jdk21
```

If no suitable JDK is found for a version, that version is **skipped** (not failed) with a message naming
the JDK it needs.

### Clients need a display

Client scenarios need a real or virtual display. On Windows/macOS the harness assumes one is present. On
Linux, set `DISPLAY`, or run under `xvfb-run` and set `QBM_HEADLESS_DISPLAY=1` so the client tests attempt
to run instead of skipping:

```bash
xvfb-run -a ./gradlew :<mod_id>-harness:functionalTest -Pqbm.sides=client
```

The NeoForge **client** is intentionally out of scope — it needs an interactive launcher profile. The
mod's client code lives in `common` and loads identically under both loaders, so the Fabric client covers
it.

## Output

After a run, `build/compat-report/` holds:

- `compatibility.md` — a table of every (version, loader, side, scenario) and its verdict
- `compatibility.json` — the same, machine-readable, consumed by the CI gate
- `logs/<mc>-<loader>-<scenario>/` — the full game log for each run, for debugging a failure

## Marking known-broken versions (xfail)

When a version is genuinely incompatible and the fix is tracked elsewhere, record it in
`.github/known-issues.json` instead of letting it turn CI red:

```json
{
  "issues": [
    { "branch": "1.21", "mc": "1.21.2", "loader": "neoforge",
      "reason": "mixin X no longer applies", "issue": "#123" }
  ]
}
```

- `branch`, `reason` and `issue` are **required** — a marking must always say what is broken and where it
  is tracked. A malformed entry fails the run loudly.
- `mc`, `loader`, `side`, `scenario` are optional; an omitted one means `*` (the whole row).
- A **marked failure** is recorded as a known failure and does **not** fail the build.
- A **marked combination that passes** *fails* the build with a "stale marking, please remove" message,
  so a marking can never outlive the bug it describes.

## Propagating to the other release branches

All of this currently lives on branch `1.21` only. To propagate, cherry-pick the harness commit onto each
release branch and adjust for that branch's toolchain. The build wiring is already branch-aware:
`build.yml` resolves `mod_id` and skips the unit-test step on branches with no `harness/` directory, and
the functional workflow guards on `harness/` existing — so a branch without the harness simply does
nothing until the cherry-pick lands.

Per-branch checklist after cherry-picking:

1. **Nothing to change for 1.20.x / 1.21.x** — they all build on Java 21 and use `remapJar`, same as
   `1.21`. The harness's `HarnessConfig` resolves the game JDK per MC version automatically.
2. **For 26.1 / 26.2**, these branches differ (see `.github/release-branches.json`, `build_task: build`,
   `java: 25`):
   - The game needs **Java 25**; the functional workflow already installs `matrix.game_java`, but a local
     run needs `JAVA25_HOME` or `-Dqbm.java25=...`.
   - These branches use `com.gradleup.shadow` 9.x, architectury-plugin 3.5 and Loom 1.14 — the harness
     module has none of those dependencies, so its `build.gradle` should cherry-pick cleanly, but confirm
     the root `subprojects {}` skip block (name ends `-cli`/`-harness`) is present.
   - `fabric.mod.json` hardcodes `"java": ">=21"` while 26.x compiles at release 25 — flagged as a
     candidate finding; verify whether the Fabric client/server still boots.
3. Run `./gradlew :<mod_id>-harness:printMcMatrix` on the branch and confirm the resolved versions match
   the branch's `minecraft_supported_versions` before trusting a full run.

The matrix is always resolved from the branch's own `gradle.properties` against the live Mojang manifest,
so each branch tests exactly the versions it claims to support with no per-branch matrix to maintain.
