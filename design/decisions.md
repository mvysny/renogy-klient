# Decisions

Why this project is the way it is: one entry per decision *already taken*, with the
roads not taken. Not what the code does (the code and its KDoc), not what
must hold (`requirements.md`), not what the Renogy device does (`research.md`).

- Cite an entry by slug — `D_<slug>` — never by position. `grep '^## D_' design/decisions.md`
  is the index; there is no table of contents.
- **No entry without a real fork.** Nothing seriously considered and rejected → not a decision.
- Entries are mutable: refine in place, newest first. A *shipped* decision that is reversed
  keeps its entry as a tombstone (`Status: Superseded by D_<slug>`); the replacement is written fresh.
- Shape: `## D_<slug> — <title> (<decided date>)`, then **Status**, **Context**, **Decision**,
  one **Rejected: …** paragraph per alternative, **Consequences**.

---

## D_design_docs — Adopt the `design/` doc layer, with `architecture.md` as the assembled picture (2026-09-12)

**Status:** Accepted; installed 2026-09-12.

**Context.** The project had a README and no other prose. It is a good operator manual — wiring,
install, the register and fault tables, Grafana setup — but it was also the only place any *why*
could go, and the whys never went there: why the InfluxDB2 client is hand-rolled, why the daily
stats are recomputed, why the serial port is reopened on timeout all lived inside KDoc blocks on
the classes that implement them, findable only by someone already at that class. There was no
`AGENTS.md`, so an agent editing one file could not learn what the rest of the code depends on.

**Decision.** Rationale and reference move to `design/` — `decisions.md` (`D_`),
`requirements.md` (`R_`), `architecture.md`, `research.md`, `ideas/` — and a new `AGENTS.md`
keeps only invariants, the module map and the doc map, under the cap its header states.

The assembled picture is **`architecture.md`, a description**: per-symbol truth lives in KDoc
and is complete there, so the file holds only the cross-symbol map — the decorator chains, the
threading story, where to start reading — and when it and the code disagree the file is fixed.

**Rejected: keeping the rationale in `AGENTS.md`.** It is loaded on every turn; a paragraph of
why-not-the-alternative there is paid for by every session and compresses into a summary that
is really a second copy of the entry it points at.

**Rejected: `solution.md`, a spec.** The Kotlin sources are the deliverable and carry per-symbol
truth in their KDoc; nothing here is written *against* a prose spec, so a file the code must be
fixed to match would be fiction within a release.

**Consequences.** Every `D_` / `R_` cited anywhere must resolve — `design/verify_design_tripwires.sh`
checks it, and `./gradlew check` runs it. Deferred: the README's Modbus register and fault
tables stay where they are for now — they are operator reference, not design, even though the
device facts behind them are `research.md`'s.

## D_tiny_influx_client — Hand-roll the InfluxDB2 client instead of using the official one (2023-10-17)

**Status:** Accepted; shipped.

**Context.** The target machine is a Raspberry Pi Zero 2W running the app under
`-Xmx20m -Xss200k`, alongside PostgreSQL and Grafana on the same 512 MB box. InfluxDB2 support
needed an HTTP client for two endpoints: `/api/v2/write` (line protocol, `text/plain`) and
`/api/v2/delete` (a three-field JSON object).

**Decision.** {InfluxDBTinyClient} — around ninety lines over `java.net.http.HttpClient`,
formatting the line protocol by hand and the delete request through kotlinx-serialization,
which the project already depends on for `status.json`. The official
`com.influxdb:influxdb-client-kotlin` stays, as a `testImplementation` dependency only, so the
tests exercise the real server through a real client.

**Rejected: `influxdb-client-kotlin` as a runtime dependency.** It pulls in a large transitive
tree — its own Retrofit/OkHttp stack, RxJava and coroutines — for two HTTP calls whose payloads
are a formatted string and a three-field object. On a 20 MB heap the classloading alone is the
larger cost, and the project uses no coroutines anywhere else.

**Consequences.** Any InfluxDB2 API change is ours to follow; the line-protocol formatting and
the error-response parsing ({InfluxDBFailure}) are hand-maintained.

## D_async_logging — Log samples off the poll thread, with a timeout and a retry (2025-02-26)

**Status:** Accepted; shipped.

**Context.** The poll loop wakes every `--pollinterval` seconds (default 10), reads the device
and hands the sample to the configured loggers. PostgreSQL and InfluxDB2 are over a socket: a
restarted database, a full disk or a wedged connection blocks the caller for as long as the
socket layer takes to notice. Blocking there stalls the *device* sampling, so a database problem
turns into missing solar data — the one thing the daemon exists to collect.

**Decision.** Every logger that can block over a socket is wrapped as
`RetryableDataLogger(TimeoutDataLogger(logger))` in `Args.newDataLogger`, and the poll loop
submits the append as a named task to `Main.backgroundTasks` ({BackgroundTaskExecutor}), a
cached thread pool that cancels tasks which overrun their deadline. The device read itself stays
synchronous on the poll thread under a lock.

**Rejected: appending synchronously and letting the interval slip.** Simple, and wrong for the
deliverable: the sample rate is the product, and a single slow write shifts every subsequent
sample.

**Rejected: a single-threaded executor for background tasks.** `TimeoutDataLogger` submits its
own task from inside a task the poll loop submitted; with one thread the nested submission waits
for its own parent and neither ever completes.

**Consequences.** `Main.backgroundTasks` is a `lateinit` global that `TimeoutDataLogger` reaches
for, so a test touching that decorator must construct one (see `TimeoutDataLoggerTest`). Tasks
are cancelled rather than queued without bound, so under a long outage samples are dropped, not
buffered — the daemon stays within its memory budget instead.

## D_decorator_chain — Compose client and logger behaviour as decorators, never as subclasses (2023-04-15)

**Status:** Accepted; shipped.

**Context.** Both the device side and the logging side accumulate cross-cutting behaviour:
reopen-on-timeout, daily-stats correction and a dummy implementation for the client; fan-out,
timeout and retry for the loggers. Each applies to some configurations and not others, and their
order matters — a retry outside a timeout is a different thing from a timeout outside a retry.

**Decision.** `RenogyClient` and `DataLogger` are interfaces; every added behaviour is a class
that takes a delegate and forwards the rest with Kotlin's `by` delegation
({FixDailyStatsClient}, {RetryOnTimeoutClient}, `RetryableDataLogger`, `TimeoutDataLogger`,
`CompositeDataLogger`). The chains are assembled in exactly two places: `main` for the client,
`Args.newDataLogger` for the loggers.

**Rejected: an abstract base class per side** (`AbstractRenogyClient`, `AbstractDataLogger`)
holding the retry and timeout logic. The behaviours are independent and optional, so a base
class either carries all of them behind flags or forces a combinatorial hierarchy, and neither
can be reordered or tested in isolation.

**Consequences.** Adding a behaviour means one class and one line at the assembly site, which
makes those two sites load-bearing — `design/architecture.md` starts there.

## D_fix_daily_stats — Recompute the daily statistics ourselves for part of each day (2023-04-15)

**Status:** Accepted; shipped.

**Context.** The Rover does not reset its daily counters at midnight but at an arbitrary
device-local moment — 9:17am on the author's unit (`design/research.md`). So between midnight
and that moment, every "daily" register the device reports still describes *yesterday*: the
day's max charging power, its amp-hours, its generated watt-hours. Logged straight through, each
morning carries the previous day's figures and the real morning generation disappears.

**Decision.** {FixDailyStatsClient} decorates the client and tracks which period it is in. It
detects local midnight by the date changing between samples, and the device's own reset by
`DailyStats.powerGenerationWh` falling. Between the two — the "Don't Trust Renogy" period — it
derives the min/max figures from the power-status samples it sees and reports generation as the
device's value minus the baseline recorded at midnight. After the device resets, it passes the
device's values through, adding back the generation accumulated during the untrusted period so
the day's total is whole.

**Rejected: logging the device's daily stats verbatim** and correcting in the Grafana query. It
pushes a device quirk into every consumer of the data, including the CSV files people inspect by
hand, and the raw values are not recoverable once they have been misattributed to a day.

**Rejected: computing the daily stats ourselves all day.** The device's own figures are sampled
continuously and ours only every `--pollinterval` seconds, so ours understate peaks; we use them
only for the window where the device's are outright wrong.

**Consequences.** The correction depends on the process observing midnight — a restart during
the untrusted window loses the baseline, and the code falls back to plain pass-through for that
morning. `chargingAh` is reported as zero during the untrusted period, since it cannot be
derived from the samples. `RenogyModbusClient` must never be used for logging without this
decorator.
