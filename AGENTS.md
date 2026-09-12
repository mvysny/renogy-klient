# renogy-klient — AGENTS.md

An index, not a manual: each entry is the one-line invariant ("what you must not break") plus
pointers to where the truth and its rationale live — a doc comment (`See {Class}`), a decision
(`See D_<slug>`), a requirement (`See R_<slug>`). Never the explanation itself. **Size cap:
34 KB.** Over it, garbage-collect by moving, never summarising: a rationale to its `D_`, a per-symbol rule to
its KDoc, a cross-symbol flow to `design/architecture.md`, a module's own invariants and class
map to that module's `AGENTS.md` (`ls */AGENTS.md` for the set; ≤ 10 KB each, loaded only when
work touches the directory). `CLAUDE.md` is exactly `@AGENTS.md`, here and beside every nested
`AGENTS.md`.

## What this is

A Kotlin/JVM command-line daemon that polls a Renogy Rover MPPT solar charge controller over an
RS232/USB serial line and appends each sample to stdout, a CSV file, PostgreSQL or InfluxDB2.
jSerialComm owns the serial port and the JVM owns scheduling; this project owns the Modbus
request framing and register decoding, the correction of Renogy's untrustworthy daily stats, and
the data-logger fan-out. Its deployment target is a Raspberry Pi Zero, which sets its budget.

## Design docs

Rationale and reference live under `design/`; this file holds only what its header says it may.
Each file has one audience and *what it is allowed to own*; every file's preamble states its
entry shape and how to cite it. This section is the whole contract — nothing outside the repo
is needed to follow it.

| File | Owns | Loaded? |
|---|---|---|
| `README.md` | the operator running this on a Raspberry Pi: wiring, install, how to run, Grafana / PostgreSQL / InfluxDB setup, the register and fault tables | — |
| `AGENTS.md` (this) | what you must not break from a distance; the module map; this table — its rules are in its header | **every turn** |
| `design/requirements.md` | what must hold — `R_` entries, stated not argued | lazy |
| `design/architecture.md` | **the map** of the code as it is — boundaries, wiring, flows; **the code is the truth** | lazy |
| `design/decisions.md` | why this and not that — `D_` entries, roads not taken | lazy |
| `design/research.md` | verified facts about the Renogy Rover device and its Modbus protocol, each claim `[docs]` / `[src]` / `[verified]` / `[unverified]` | lazy |
| `design/ideas/` | not yet decided — one file per idea, `ls` is the index, deleted on graduation | transient |
| KDoc | per-symbol truth and its local rationale | source of truth |

Rules that keep the split from drifting:

- **One home per fact; the others link.** A one-line restatement that saves a jump is fine —
  repeat the *fact*, defer the *explanation*. Compressing a `D_` entry into a bullet here is a
  third copy, not a summary.
- **`decisions.md` argues, `requirements.md` states, `research.md` is about *them* not us,
  `architecture.md` composes and never argues.** A paragraph explaining *why* in any file but
  `decisions.md` has drifted; move it and cite the `D_`.
- **No `D_` entry without a real fork; only decisions already taken.** Ideas, TODOs and open
  questions go to `design/ideas/`. A shipped decision that is reversed keeps its entry as a
  tombstone.
- **Slugs:** `D_` decisions, `R_` requirements, `T_` tripwires (cited from the requirement's
  *Enforced by*, defined by the check), `Q_` open questions inside `design/ideas/` only — a
  durable doc never cites a `Q_`. Underscores throughout, backticked in prose, cited by slug
  never by position; `grep '^## D_' design/decisions.md` is the index.
- **`design/verify_design_tripwires.sh`** (also `./gradlew designTripwires`, wired into `check`)
  fails on any cited `D_` / `R_` without a heading, a `T_` without a check, an oversized
  `AGENTS.md`, or a `CLAUDE.md` that isn't the shim. Run it before committing a doc change.

### Ideas & their graduation

An idea graduates the moment it is acted on, and graduation is not done until its file (and any
sidecar folder `design/ideas/<name>/`) is gone. Where the lasting nuggets land:

- the choice made + the alternatives rejected → a `D_` entry in `design/decisions.md`
- something that must hold from now on → an `R_` entry in `design/requirements.md`
- a new module, or a changed responsibility → one line in the module map (this file)
- how the pieces work together — wiring, a flow crossing several of them → `design/architecture.md`
- verified behaviour of the Renogy device or of PostgreSQL / InfluxDB → `design/research.md`, with a provenance marker
- how one class works and why it is shaped so → its KDoc
- what a user or operator must know → `README.md`
- a cross-cutting invariant ("never …") → this file
- work deferred *as a consequence of a logged decision* → that entry's *Consequences*

*Layout seeded from the `design-docs` skill (mvysny, `~/.claude/skills`); this project needs
nothing from it.*

## Invariants

- **A new runtime dependency must be weighed by its transitive tree, not its API.** The app runs under `-Xmx20m -Xss200k` on a Raspberry Pi Zero; the official InfluxDB2 client was rejected for its dependency mass and hand-rolled instead. See `R_small_footprint`, `D_tiny_influx_client`, {InfluxDBTinyClient}.
- **Nothing on the poll thread may block on a data logger.** A wedged database would stall device sampling and silently drop samples; every logger that can block is wrapped in `TimeoutDataLogger` + `RetryableDataLogger` and runs on `Main.backgroundTasks`. See `R_poll_never_blocks`, `D_async_logging`.
- **The poll loop swallows every exception and keeps polling.** The daemon is unattended for months; an unplugged USB adapter or a restarted database must be survived, not fatal. See `R_survives_failures`.
- **`RenogyClient` and `DataLogger` gain behaviour by decoration, never by subclassing.** Retry, timeout, daily-stats correction and fan-out are each one decorator composed in `Args.newDataLogger` / `main`; a base class here would make the chain unorderable and untestable. See `R_decorators_not_subclasses`, `D_decorator_chain`.

## Module map

Under `src/main/kotlin/`:

- root — `Main.kt` (the poll loop) and `Args.kt` (picocli CLI; builds the object graph).
- `clients/` — `RenogyClient` and its decorators: Modbus framing, retry, daily-stats fix, dummy.
- `datalogger/` — `DataLogger` and its implementations: stdout, CSV, PostgreSQL, InfluxDB2.
- `datalogger/influxdb/` — the hand-rolled InfluxDB2 line-protocol HTTP client.
- `utils/` — serial `IO`, CRC16/Modbus, background tasks, logging, CSV writing.
- `docs/` (repo root) — the Rover Modbus specification PDF and a Grafana screenshot for the README.

## Conventions

- **Kotlin/JVM, target 17, no coroutines.** Plain threads, `ScheduledExecutorService` and `Closeable`; a single Gradle Kotlin DSL module.
- **Unsigned types for anything read off the wire.** `UShort` / `UByte` throughout the register decoding, via `kotlin-unsigned-jvm`.
- **Logging goes through `utils.Log`,** a thin wrapper over slf4j-simple; `--verbose` flips `Log.isDebugEnabled`.
- **Tests: JUnit 5 with `kotlin.test` asserts,** no mocking framework — hand-written fakes (`DummyRenogyClient`, `DummyDataLogger`) and Testcontainers for PostgreSQL and InfluxDB.
- **Errors are loud at the edges, quiet in the loop.** A malformed Modbus response throws `RenogyException`; only the poll loop and the retry decorators may catch and continue.

## Commands

- `./gradlew` — the default tasks: `clean build`, which includes `test` and `check`.
- `./gradlew test --tests '*RenogyModbusClientTest*'` — one test class.
- `./gradlew run --args='dummy'` — run against the dummy device.
- `design/verify_design_tripwires.sh` — the doc-layer tripwires; also `./gradlew designTripwires`, which `check` depends on.
- CI: `.github/workflows/gradle.yml` runs `./gradlew clean build` on JDK 17 and 22.

## Skills this project follows

- **KDoc carries the per-symbol what *and* why, complete standalone;** the `writing-kdoc` skill has the rules.
- **Gradle:** the wrapper is bootstrapped from the official distribution only, never hand-edited; the `gradle` skill has the rules.

## Working on this codebase

- **`Main.backgroundTasks` is a `lateinit` global assigned by `mainLoop`.** `TimeoutDataLogger.append` reaches for it, so a test that exercises that decorator must assign and kill one itself — see `TimeoutDataLoggerTest`.
- **The background executor must stay a cached thread pool, not a single thread.** The poll loop submits a logging task which itself submits a nested task; one thread deadlocks both. See {BackgroundTaskExecutor}, `D_async_logging`.
- **Serial responses carry no framing beyond their declared byte count,** so one partial read leaves the pipe offset and every later response decodes as garbage. See `design/research.md`.
- **Daily stats from the device are wrong for part of every day** and must be read through `FixDailyStatsClient`, never from `RenogyModbusClient` directly. See `D_fix_daily_stats`.
