package utils

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A simple long-running task executor, may run multiple tasks at once.
 * Call [cleanup] periodically, to cancel old tasks.
 * @property cancelTaskAfter if a task didn't finish within this duration, it will be canceled in [cleanup].
 */
class BackgroundTaskExecutor(val cancelTaskAfter: Duration = 15.seconds) : Closeable {
    private data class Task(val name: String, val future: Future<*>, val submittedAt: Instant)
    private val executor = Executors.newSingleThreadExecutor()
    private val pendingTasks = CopyOnWriteArrayList<Task>()

    override fun close() {
        executor.shutdownGracefully()
    }

    fun submit(taskName: String, task: () -> Unit) {
        val submittedAt = Instant.now()
        val future = executor.submit {
            try {
                task()
            } catch (t: Throwable) {
                log.error("Task '$taskName' failed to execute", t)
            }
        }
        pendingTasks.add(Task(taskName, future, submittedAt))
    }

    /**
     * Cleans up all tasks that didn't finish.
     */
    fun cleanup() {
        // cleanup all finished tasks; warn if there are some still ongoing
        pendingTasks.removeIf { it.future.isDone }
        if (pendingTasks.isNotEmpty()) {
            log.warn("${pendingTasks.size} pending tasks ongoing")
        }
        pendingTasks.forEach { task ->
            if (Instant.now() - task.submittedAt >= cancelTaskAfter) {
                task.future.cancel(true)
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = LoggerFactory.getLogger(BackgroundTaskExecutor::class.java)
    }
}

fun ExecutorService.shutdownGracefully() {
    shutdown()
    awaitTermination(10, TimeUnit.SECONDS)
    shutdownNow()
}
