# Requirements

What must hold — of the deliverable's behaviour, not of the environment it runs in. One entry
per requirement. A requirement *states*; it never argues: the fork behind it, if there is one,
is a `D_` entry it cites.

- Cite by slug — `R_<slug>`. `grep '^## R_' design/requirements.md` is the index.
- Slug only what is referenced from elsewhere.
- Shape: `## R_<slug> — <the requirement, one sentence>`, then **Status** (Active, or
  Retired <date> — see `D_<slug>`), **Why** (one paragraph), **Enforced by** (a test, a script
  step, a tripwire cited as `T_<slug>` — this line is that slug's home — or "review only"), **See** (the `D_` entries behind it).
- A retired requirement stays as a tombstone. A requirement that wants a *Rejected:* section is a
  decision — move it to `decisions.md`.
- **The first entry is the ruler**: later entries are trimmed to its length, never the other way
  round.

---

## R_small_footprint — The daemon runs in a 20 MB heap on a Raspberry Pi Zero, and every runtime dependency is weighed by its transitive tree

**Status:** Active.
**Why.** The deployment target is a 512 MB single-board computer that also runs PostgreSQL and
Grafana; `applicationDefaultJvmArgs` pins `-Xmx20m -Xss200k`. A dependency that looks free at
the API level can cost more in classloading than the feature is worth.
**Enforced by.** Review only: the runtime `implementation` block in `build.gradle.kts` is short
on purpose, and the InfluxDB2 client is marked `testImplementation` with the reason inline.
**See.** `D_tiny_influx_client`.

## R_poll_never_blocks — A sample is never delayed by a data logger

**Status:** Active.
**Why.** The sampling interval is the product: a wedged database or a slow HTTP write must cost
the logged data point, not the cadence of every point after it. Loggers that talk over a socket
therefore run off the poll thread, under a deadline, and are cancelled rather than queued.
**Enforced by.** `Args.newDataLogger` wraps each socket-backed logger as
`RetryableDataLogger(TimeoutDataLogger(…))`; `TimeoutDataLoggerTest` and
`RetryableDataLoggerTest` cover the timeout and retry behaviour.
**See.** `D_async_logging`.

## R_survives_failures — An unattended run survives device, serial-port and database failures indefinitely

**Status:** Active.
**Why.** The daemon runs for months without anyone watching it. An unplugged USB adapter, a
restarted PostgreSQL or a temporary DNS failure must degrade to a warning and a retry; the
process exiting means silent data loss until someone notices.
**Enforced by.** The poll loop in `Main.kt` catches every exception per iteration;
`RetryOnTimeoutClient` reopens the serial port on timeout; `RetryableDataLogger` retries
recoverable logger failures with backoff.
**See.** `D_async_logging` for the logger half; for the serial port, {RetryOnTimeoutClient}'s
KDoc and `research.md`.

## R_decorators_not_subclasses — Behaviour is added to `RenogyClient` and `DataLogger` by decoration, never by subclassing

**Status:** Active.
**Why.** Retry, timeout, fan-out and the daily-stats correction are independent, optional, and
order-sensitive; a base class makes the combination unorderable and each behaviour untestable
alone. Kotlin's `by` delegation keeps each decorator to the methods it actually changes.
**Enforced by.** Review only: the chains are assembled in `main` and `Args.newDataLogger`, and
each decorator is tested against a fake delegate.
**See.** `D_decorator_chain`.
