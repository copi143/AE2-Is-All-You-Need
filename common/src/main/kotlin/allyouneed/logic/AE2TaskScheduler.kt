package allyouneed.logic

import allyouneed.logic.AE2TaskScheduler.submit
import allyouneed.util.MarkedLogger
import org.slf4j.MarkerFactory
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
    val logger = MarkedLogger(allyouneed.util.logger, MarkerFactory.getMarker("Task"))

    val parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    private val threadSeq = AtomicInteger(0)
    private val submitted = AtomicLong(0)
    private val completed = AtomicLong(0)
    private val failed = AtomicLong(0)

    private val inFlight = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    private val threadFactory = ThreadFactory { r ->
        Thread(r, "AE2AYN-Worker-${threadSeq.incrementAndGet()}").apply {
            isDaemon = true
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                logger.error("Uncaught exception in ${t.name}", e)
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
        logger.debug("submit #$id (active≈$activeTaskCount, pool=$parallelism)")

        val future = CompletableFuture.supplyAsync({
            try {
                val result = task.call()
                completed.incrementAndGet()
                logger.debug("complete #$id")
                result
            } catch (e: Throwable) {
                failed.incrementAndGet()
                logger.info("task #$id failed", e)
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
        logger.debug("submit #$id runnable (active≈$activeTaskCount, pool=${parallelism})")
        @Suppress("UNCHECKED_CAST") val future = CompletableFuture.supplyAsync({
            try {
                task.run()
                completed.incrementAndGet()
                logger.debug("complete #$id")
                null as Void?
            } catch (e: Throwable) {
                failed.incrementAndGet()
                logger.info("task #$id failed", e)
                throw e
            }
        }, pool) as CompletableFuture<Void>
        inFlight.add(future)
        future.whenComplete { _, _ -> inFlight.remove(future) }
        return future
    }

    @JvmStatic
    fun <T> submit(task: () -> T): CompletableFuture<T> = submit(Callable { task() })

    /**
     * Approximate number of tasks that have been submitted but not yet finished
     * (completed or failed). Not exact under concurrent updates.
     */
    @JvmStatic
    val activeTaskCount: Int get() = inFlight.size

    @JvmStatic
    val submittedCount: Long get() = submitted.get()

    @JvmStatic
    val completedCount: Long get() = completed.get()

    /** Cancel all in-flight tasks (interrupts workers). Pool stays alive for new work. */
    @JvmStatic
    fun cancelAll() {
        for (f in inFlight.toTypedArray()) {
            f.cancel(true)
        }
    }
}
