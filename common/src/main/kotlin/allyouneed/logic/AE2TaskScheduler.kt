package allyouneed.logic

import allyouneed.logic.AE2TaskScheduler.submit
import appeng.core.AELog
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Global background task scheduler for AE2-related work (crafting calculation, and future jobs).
 *
 * Uses a dedicated fixed thread pool sized at half the available processors (minimum 1).
 * Do **not** run long blocking crafting work on [kotlinx.coroutines.Dispatchers.Default] —
 * Forge mod classloaders and the common ForkJoinPool have caused jobs to never complete
 * (UI stuck on "Calculating...").
 *
 * Callers **must** fully value-copy any live ME/grid state on the calling thread
 * before [submit]. Background tasks must not touch live grid storage or crafting services.
 *
 * @see docs/Crafting-Calculation.md
 */
object AE2TaskScheduler {

    val parallelism: Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    private val threadSeq = AtomicInteger(0)
    private val submitted = AtomicLong(0)
    private val completed = AtomicLong(0)
    private val failed = AtomicLong(0)

    private val inFlight = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    private val threadFactory = ThreadFactory { r ->
        Thread(r, "AE2AYN-Worker-${threadSeq.incrementAndGet()}").apply {
            isDaemon = true
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                AELog.error(e, "Uncaught exception in " + t.name)
            }
        }
    }

    private val pool: ExecutorService = Executors.newFixedThreadPool(parallelism, threadFactory)

    /** Java-friendly executor (same pool). */
    @JvmField
    val executor: Executor = pool

    /**
     * Submit a callable. Returns a [CompletableFuture] that completes with the result.
     * [CompletableFuture.cancel] with `mayInterruptIfRunning=true` interrupts the worker thread.
     */
    @JvmStatic
    fun <T> submit(task: Callable<T>): CompletableFuture<T> {
        val id = submitted.incrementAndGet()
        AELog.debug(
            "AE2TaskScheduler: submit #%d (active≈%d, pool=%d)",
            id,
            activeTaskCount(),
            parallelism,
        )

        val future = CompletableFuture.supplyAsync({
            try {
                val result = task.call()
                completed.incrementAndGet()
                AELog.debug("AE2TaskScheduler: complete #%d", id)
                result
            } catch (e: Throwable) {
                failed.incrementAndGet()
                AELog.info(e, "AE2TaskScheduler: task #$id failed")
                throw e
            }
        }, pool)

        inFlight.add(future)
        future.whenComplete { _, _ -> inFlight.remove(future) }
        return future
    }

    @JvmStatic
    fun submit(task: Runnable): CompletableFuture<Void> {
        val id = submitted.incrementAndGet()
        AELog.debug(
            "AE2TaskScheduler: submit #%d runnable (active≈%d, pool=%d)",
            id,
            activeTaskCount(),
            parallelism,
        )
        @Suppress("UNCHECKED_CAST")
        val future = CompletableFuture.supplyAsync({
            try {
                task.run()
                completed.incrementAndGet()
                AELog.debug("AE2TaskScheduler: complete #%d", id)
                null as Void?
            } catch (e: Throwable) {
                failed.incrementAndGet()
                AELog.info(e, "AE2TaskScheduler: task #$id failed")
                throw e
            }
        }, pool) as CompletableFuture<Void>
        inFlight.add(future)
        future.whenComplete { _, _ -> inFlight.remove(future) }
        return future
    }

    @JvmStatic
    fun <T> submit(task: () -> T): CompletableFuture<T> =
        submit(Callable { task() })

    /**
     * Approximate number of tasks that have been submitted but not yet finished
     * (completed or failed). Not exact under concurrent updates.
     */
    @JvmStatic
    fun activeTaskCount(): Int = inFlight.size

    @JvmStatic
    fun submittedCount(): Long = submitted.get()

    @JvmStatic
    fun completedCount(): Long = completed.get()

    /** Cancel all in-flight tasks (interrupts workers). Pool stays alive for new work. */
    @JvmStatic
    fun cancelAll() {
        for (f in inFlight.toTypedArray()) {
            f.cancel(true)
        }
    }
}
