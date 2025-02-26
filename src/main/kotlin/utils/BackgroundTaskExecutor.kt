package utils

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * A simple long-running task executor, may run multiple tasks at once.
 * Call [cleanup] periodically, to cancel old tasks.
 */
class BackgroundTaskExecutor : Closeable {
    private data class Task(val id: Int, val name: String, val future: Future<*>, val submittedAt: Instant, val cancelTaskAfter: Duration) {
        val isHogged: Boolean get() = Instant.now() - submittedAt >= cancelTaskAfter
        fun cancelIfHogged() {
            if (isHogged) {
                cancel()
            }
        }
        fun cancel() {
            future.cancel(true)
        }
    }

    /**
     * Don't use single thread executor, since main loop posts log request here, and individual log requests
     * use TimeoutDataLogger which also posts requests here!!! If this would be single thread only, the sub-requests would never finish!
     */
    private val executor = Executors.newCachedThreadPool()
    private val pendingTasks = CopyOnWriteArrayList<Task>()
    private val idGenerator = AtomicInteger()

    override fun close() {
        executor.shutdownGracefully()
    }

    fun kill() {
        executor.shutdownNow()
    }

    /**
     * @property cancelTaskAfter if a task didn't finish within this duration, it will be canceled in [cleanup].
     */
    fun submit(taskName: String, cancelTaskAfter: Duration, taskBody: () -> Unit) {
        submitInternal(taskName, cancelTaskAfter) {
            try {
                taskBody()
            } catch (t: Throwable) {
                log.error("Task '$taskName' failed to execute", t)
            }
        }
    }

    private fun submitInternal(taskName: String, cancelTaskAfter: Duration, taskBody: () -> Unit): Task {
        val submittedAt = Instant.now()
        val future = executor.submit { taskBody() }
        val t = Task(idGenerator.incrementAndGet(), taskName, future, submittedAt, cancelTaskAfter)
        pendingTasks.add(t)
        return t
    }

    /**
     * Runs given task, canceling it after [timeoutAfter]. Blocks until the task completes, is canceled by someone or times out.
     * Throws [CancellationException] if it is canceled, [TimeoutException] if the wait times out.
     */
    fun run(taskName: String, timeoutAfter: Duration, taskBody: () -> Unit) {
        val task = submitInternal(taskName, timeoutAfter, taskBody)
        try {
            task.future.get(timeoutAfter)
        } catch (e: TimeoutException) {
            task.cancel()
            throw e
        }
    }

    /**
     * Cleans up all tasks that didn't finish.
     */
    fun cleanup() {
        // cleanup all finished tasks; warn if there are some still ongoing
        pendingTasks.removeIf { it.future.isDone }
        if (pendingTasks.isNotEmpty()) {
            log.warn("${pendingTasks.size} hogged tasks: ${pendingTasks.map { "${it.id}:${it.name}" }}")
        }
        pendingTasks.forEach { task -> task.cancelIfHogged() }
    }

    companion object {
        @JvmStatic
        private val log = LoggerFactory.getLogger(BackgroundTaskExecutor::class.java)
    }
}

fun ExecutorService.shutdownGracefully() {
    shutdown()
    awaitTermination(1, TimeUnit.SECONDS)
    shutdownNow()
}
