# Architecture

The bird's-eye map of the code as it is: the major pieces, where each lives, how they are wired,
and the flows that cross several of them. **The code and its KDoc are the authority; this file
describes them.** When they disagree, fix this file.

Keep it short. Anything true of one class or method belongs in that symbol's KDoc, not here.
This file holds only what no single symbol can: dependency direction, the lifecycle / threading /
data-flow story, and where a newcomer should start reading. Cite `R_` for what must hold and
`D_` for why; argue nothing here. **The first entry in each section is the ruler** — later
entries are trimmed to its length, never the other way round.

---

The **map** — which module lives where, one line each — is not here: it is in `AGENTS.md`,
because an agent needs it on every turn. This file starts where the map stops — how those pieces
work together.

## Wiring

- Two interfaces carry everything: `RenogyClient` (a device that can be asked for a sample) and `DataLogger` (somewhere a sample can be appended). Both are decorated, never subclassed (`R_decorators_not_subclasses`, `D_decorator_chain`).
- The client chain is built in `main`: `FixDailyStatsClient(RetryOnTimeoutClient(device, 3s, address))`, or a bare `DummyRenogyClient` when the device argument is `dummy`. `RetryOnTimeoutClient` owns the serial port and constructs a throwaway `RenogyModbusClient` per request over the currently-open `IO`.
- The logger chain is built in `Args.newDataLogger`: a `CompositeDataLogger` fans out to one logger per `--csv` / `--postgres` / `--influx` flag, each socket-backed one wrapped as `RetryableDataLogger(TimeoutDataLogger(…))`; with no flags it degrades to `StdoutCSVDataLogger`.
- Dependencies point one way: both sit on `utils/`, and `datalogger/` reads the `RenogyData` value types defined in `clients/RenogyClient.kt` while `clients/` never references `datalogger/`.
- The single exception, and the only global, is `Main.backgroundTasks`: a `lateinit` {BackgroundTaskExecutor} assigned by `mainLoop` and reached for by `TimeoutDataLogger` — the seam that keeps logging off the poll thread (`R_poll_never_blocks`).

## Flows

**One poll** (every `--pollinterval` seconds, on the scheduler's single thread):

1. `Main.mainLoop`'s `scheduleAtFixedRate` block fires and calls `Main.backgroundTasks.cleanup()`, cancelling any task that overran its deadline.
2. `client.getAllData(systemInfo)` under `dataGrabLock` → `FixDailyStatsClient` → `RetryOnTimeoutClient` → a fresh `RenogyModbusClient` over the open `SerialPortIO`.
3. `RenogyModbusClient` issues one ReadRegister call per register block, checks the CRC16 of each response, and decodes it into `RenogyData`.
4. `FixDailyStatsClient` replaces `dailyStats` with its own figures if the day is in the untrusted window (`D_fix_daily_stats`).
5. `status.json` (or `--statefile`) is overwritten with the sample as JSON, still on the poll thread.
6. The append is submitted to `Main.backgroundTasks` with a 60 s deadline; the poll thread returns immediately. Any exception in steps 2–6 is logged as a warning and the next tick proceeds (`R_survives_failures`).

**A serial timeout** (inside step 2 above):

1. `IO.read` exceeds the 3 s timeout and throws `IOTimeoutException`.
2. `RetryOnTimeoutClient.runAndMitigateExceptions` closes the `SerialPortIO` and drops it, then rethrows — it does not retry, because the next poll is the retry.
3. The poll loop logs the warning; the following tick calls `getIO()`, which opens and drains a fresh port.
4. A `RenogyException` (bad CRC, unexpected length) takes the other branch: the port is drained but kept, since the pipe is still usable.

**Pruning** (once at startup, then every midnight):

1. `mainLoop` prunes inline once before starting the scheduler; thereafter `scheduleAtTimeOfDay(MIDNIGHT)` submits a 45 s task to `Main.backgroundTasks`.
2. `CompositeDataLogger.deleteRecordsOlderThan(--prunelog)` fans out; PostgreSQL deletes by `DateTime` epoch-seconds, InfluxDB2 by a delete-predicate request. CSV files are never pruned.

## Where to start reading

`Main.kt` — a hundred lines that build both chains and run the loop, so the whole object graph
and the threading story are visible at once; then `clients/RenogyClient.kt` for the value types
every other file passes around.
