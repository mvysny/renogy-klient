package utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.expect
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackgroundTaskExecutorTest {
    private val executor = BackgroundTaskExecutor()

    @AfterEach fun killExecutor() {
        executor.kill()
    }

    @Test fun `run blocks until the task is done`() {
        val ran = AtomicBoolean()
        executor.run("task", 10.seconds) {
            Thread.sleep(20)
            ran.set(true)
        }
        expect(true) { ran.get() }
    }

    /**
     * The main loop submits a logging task which itself submits a nested task; a single-threaded
     * executor would deadlock both. See `D_async_logging`.
     */
    @Test fun `a task may submit a nested task`() {
        val ran = AtomicBoolean()
        executor.run("outer", 10.seconds) {
            executor.run("inner", 10.seconds) {
                ran.set(true)
            }
        }
        expect(true) { ran.get() }
    }

    @Test fun `run rethrows the exception thrown by the task`() {
        try {
            executor.run("task", 10.seconds) { throw IllegalStateException("simulated") }
            fail("Expected to fail")
        } catch (e: java.util.concurrent.ExecutionException) {
            expect("simulated") { e.cause!!.message }
        }
    }

    @Test fun `run times out and interrupts a hogging task`() {
        val interrupted = CountDownLatch(1)
        try {
            executor.run("hog", 50.milliseconds) {
                try {
                    Thread.sleep(60_000)
                } catch (e: InterruptedException) {
                    interrupted.countDown()
                }
            }
            fail("Expected to time out")
        } catch (e: TimeoutException) {
            // expected
        }
        expect(true) { interrupted.await(10, TimeUnit.SECONDS) }
    }

    @Test fun `submit does not propagate the task's exception`() {
        val done = CountDownLatch(1)
        executor.submit("task", 10.seconds) {
            try {
                throw IllegalStateException("simulated")
            } finally {
                done.countDown()
            }
        }
        expect(true) { done.await(10, TimeUnit.SECONDS) }
    }

    @Test fun `cleanup cancels tasks which overstayed their welcome`() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        executor.submit("hog", Duration.ZERO) {
            started.countDown()
            try {
                Thread.sleep(60_000)
            } catch (e: InterruptedException) {
                interrupted.countDown()
            }
        }
        started.await(10, TimeUnit.SECONDS)
        executor.cleanup()
        expect(true) { interrupted.await(10, TimeUnit.SECONDS) }
    }

    @Test fun `cleanup leaves a task which still has time`() {
        val done = CountDownLatch(1)
        executor.submit("slowpoke", 60.seconds) {
            Thread.sleep(100)
            done.countDown()
        }
        executor.cleanup()
        expect(true) { done.await(10, TimeUnit.SECONDS) }
    }

    @Test fun `tasks run on daemon threads`() {
        val isDaemon = AtomicBoolean()
        executor.run("task", 10.seconds) { isDaemon.set(Thread.currentThread().isDaemon) }
        expect(true) { isDaemon.get() }
    }

    @Test fun `close awaits the running tasks`() {
        val counter = AtomicInteger()
        BackgroundTaskExecutor().use { e ->
            repeat(5) { e.submit("task", 10.seconds) { counter.incrementAndGet() } }
        }
        expect(5) { counter.get() }
    }
}
