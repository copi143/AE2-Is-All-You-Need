package io.github.copi143.valueschema.bench

import io.github.copi143.valueschema.demo.Quote
import io.github.copi143.valueschema.demo.QuoteColumns
import io.github.copi143.valueschema.demo.QuotePacked
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

/**
 * Verifies the valueschema claims against an ArrayList<Quote> baseline:
 * - column scans skip object headers and pointers
 * - primitive overloads and @ValueTransform snippets are unconditionally allocation-free
 *   (watch gc.alloc.rate.norm: AoS writes ~24 B/row, snippet writes ~0)
 * - value-style updateAll relies on C2 scalar replacement and should match the snippet in hot code
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
open class QuoteSchemaBenchmark {

    @Param("100000")
    var n: Int = 0

    lateinit var rows: Array<Quote>
    lateinit var idx: IntArray
    lateinit var aos: ArrayList<Quote>
    lateinit var columns: QuoteColumns
    lateinit var packed: QuotePacked

    @Setup
    fun setup() {
        val rnd = Random(42)
        rows = Array(n) { Quote(rnd.nextLong(), rnd.nextInt(1000), rnd.nextLong()) }
        idx = IntArray(1024) { rnd.nextInt(n) }
        aos = ArrayList(rows.asList())
        columns = QuoteColumns(n)
        rows.forEach(columns::add)
        packed = QuotePacked(n)
        rows.forEach(packed::add)
    }

    @Benchmark
    fun appendAos(): ArrayList<Quote> {
        val list = ArrayList<Quote>(n)
        for (q in rows) list.add(q)
        return list
    }

    @Benchmark
    fun appendColumnsValue(): QuoteColumns {
        val c = QuoteColumns(n)
        for (q in rows) c.add(q)
        return c
    }

    @Benchmark
    fun appendColumnsPrimitive(): QuoteColumns {
        val c = QuoteColumns(n)
        for (q in rows) c.add(q.id, q.price, q.ts)
        return c
    }

    @Benchmark
    fun appendPackedPrimitive(): QuotePacked {
        val p = QuotePacked(n)
        for (q in rows) p.add(q.id, q.price, q.ts)
        return p
    }

    @Benchmark
    fun scanAos(bh: Blackhole) {
        var sum = 0L
        for (q in aos) sum += q.price
        bh.consume(sum)
    }

    @Benchmark
    fun scanColumns(bh: Blackhole) {
        var sum = 0L
        columns.forEachView { sum += it.price }
        bh.consume(sum)
    }

    @Benchmark
    fun scanPacked(bh: Blackhole) {
        var sum = 0L
        packed.forEachView { sum += it.price }
        bh.consume(sum)
    }

    @Benchmark
    fun updateAos() {
        val l = aos
        for (i in l.indices) {
            val q = l[i]
            l[i] = q.copy(price = q.price * 2 + 1, ts = q.ts + 1L)
        }
    }

    @Benchmark
    fun updateColumnsValueStyle() {
        columns.updateAll { it.copy(price = it.price * 2 + 1, ts = it.ts + 1L) }
    }

    @Benchmark
    fun updateColumnsSnippet() {
        val c = columns
        for (i in 0 until c.size) c.scale2(i)
    }

    @Benchmark
    fun updatePackedSnippet() {
        val p = packed
        for (i in 0 until p.size) p.scale2(i)
    }

    @Benchmark
    fun filterAos(bh: Blackhole) {
        bh.consume(aos.filter { it.price < 500 })
    }

    @Benchmark
    fun filterColumns(): QuoteColumns = columns.filtered { it.price < 500 }

    @Benchmark
    fun materializeAos(bh: Blackhole) {
        for (i in idx) bh.consume(aos[i])
    }

    @Benchmark
    fun materializeColumns(bh: Blackhole) {
        for (i in idx) bh.consume(columns[i])
    }
}
