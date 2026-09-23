package io.github.copi143.valueschema

import io.github.copi143.valueschema.generator.FieldModel
import io.github.copi143.valueschema.generator.NestedModel
import io.github.copi143.valueschema.generator.SchemaModel
import io.github.copi143.valueschema.generator.TransformModel
import io.github.copi143.valueschema.generator.ValueSchemaGenerator
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ValueSchemaGeneratorTest {

    private val order = SchemaModel(
        packageName = "demo",
        className = "Order",
        fields = listOf(
            FieldModel("id", "Long"),
            NestedModel(
                "price",
                ClassName("demo", "Money"),
                listOf(FieldModel("amount", "Long"), FieldModel("scale", "Int")),
            ),
            FieldModel("qty", "Int"),
        ),
        transforms = listOf(
            TransformModel(name = "discount", params = "bps: Int", body = "price_amount -= price_amount * bps / 10000"),
        ),
    )

    private val quote = SchemaModel(
        packageName = "demo",
        className = "Quote",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("price", "Int"),
            FieldModel("ts", "Long"),
        ),
    )

    private fun generate(model: SchemaModel = quote): String = ValueSchemaGenerator.generate(model).toString()

    @Test
    fun `generates columns with one primitive array per field`() {
        val code = generate()
        assertContains(code, "class QuoteColumns(")
        assertContains(code, "internal var ids: LongArray")
        assertContains(code, "internal var prices: IntArray")
        assertContains(code, "internal var tss: LongArray")
    }

    @Test
    fun `generates value style accessors`() {
        val code = generate()
        assertContains(code, "return Quote(ids[index], prices[index], tss[index])")
        assertContains(code, "inline fun updateAll(transform: (Quote) -> Quote)")
        assertContains(code, "set(i, transform(get(i)))")
    }

    @Test
    fun `generates zero allocation cursor view`() {
        val code = generate()
        assertContains(code, "class QuoteView @PublishedApi internal constructor(")
        assertContains(code, "get() = columns.ids[index]")
        assertContains(code, "get() = columns.prices[index]")
        assertContains(code, "fun toValue(): Quote = Quote(id, price, ts)")
    }

    @Test
    fun `generates zero allocation primitive overloads`() {
        val code = generate()
        assertContains(code, "Zero-allocation overload: appends a row without constructing a [Quote].")
        assertContains(code, "ids[size] = id")
        assertContains(code, "(price.toLong() and 0xFFFFFFFFL)")
    }

    @Test
    fun `generates packed flat array storage`() {
        val code = generate()
        assertContains(code, "class QuotePacked(")
        assertContains(code, "const val STRIDE: Int = 3")
        assertContains(code, "class QuotePackedView @PublishedApi internal constructor(")
        assertContains(code, "val base = size * STRIDE")
        assertContains(code, "(value.price.toLong() and 0xFFFFFFFFL)")
    }

    @Test
    fun `packs sub word fields into shared slots`() {
        val model = SchemaModel(
            packageName = "demo",
            className = "TwoPairs",
            fields = listOf(
                FieldModel("a", "Int"),
                FieldModel("b", "Int"),
                FieldModel("c", "Int"),
                FieldModel("d", "Int"),
            ),
        )
        val code = generate(model)
        // four 32-bit fields fit in two 64-bit slots
        assertContains(code, "const val STRIDE: Int = 2")
        // b is read from the high half of slot 0
        assertContains(code, "ushr 32")
    }

    @Test
    fun `packs boolean as a single bit`() {
        val model = SchemaModel(
            packageName = "demo",
            className = "Flags",
            fields = listOf(FieldModel("x", "Boolean"), FieldModel("y", "Boolean")),
        )
        val code = generate(model)
        assertContains(code, "const val STRIDE: Int = 1")
        assertContains(code, "(data[base + 0] and 1L) != 0L")
        assertContains(code, "data[base + 0] = (if (x) 1L else 0L) or ((if (y) 1L else 0L) shl 1)")
    }

    @Test
    fun `generates field wise transform from snippet`() {
        val model = quote.copy(
            transforms = listOf(
                TransformModel(name = "cappedAt", params = "limit: Int", body = "if (price > limit) price = limit"),
            ),
        )
        val code = generate(model)
        assertContains(code, "fun cappedAt(")
        assertContains(code, "var price = prices[index]")
        assertContains(code, "if (price > limit) price = limit")
        assertContains(code, "prices[index] = price")
        assertContains(code, "val base = index * STRIDE")
    }

    @Test
    fun `rejects malformed transform params`() {
        val model = quote.copy(
            transforms = listOf(TransformModel(name = "bad", params = "oops", body = "")),
        )
        assertFailsWith<IllegalArgumentException> { generate(model) }
    }

    @Test
    fun `flattens nested value type into leaf columns`() {
        val code = generate(order)
        assertContains(code, "internal var ids: LongArray")
        assertContains(code, "internal var price_amounts: LongArray")
        assertContains(code, "internal var price_scales: IntArray")
        assertContains(code, "internal var qtys: IntArray")
        assertContains(code, "return Order(ids[index], Money(price_amounts[index], price_scales[index]), qtys[index])")
        assertContains(code, "price_amounts[size] = value.price.amount")
        assertContains(code, "price_amount: Long")
        assertContains(code, "var price_amount = price_amounts[index]")
    }

    @Test
    fun `nested value type shares packed slots leaf by leaf`() {
        val code = generate(order)
        assertContains(code, "public const val STRIDE: Int = 3")
    }

    @Test
    fun `rejects colliding flattened leaf names`() {
        val model = SchemaModel(
            packageName = "demo",
            className = "Bad",
            fields = listOf(
                FieldModel("price_amount", "Long"),
                NestedModel("price", ClassName("demo", "Money"), listOf(FieldModel("amount", "Long"))),
            ),
        )
        assertFailsWith<IllegalArgumentException> { generate(model) }
    }

    @Test
    fun `generates package declaration`() {
        val code = generate()
        assertContains(code, "package demo")
    }

    @Test
    fun `rejects non primitive field types`() {
        val bad = quote.copy(fields = listOf(FieldModel("name", "String")))
        assertFailsWith<IllegalArgumentException> { generate(bad) }
    }

    @Test
    fun `rejects empty field list`() {
        assertFailsWith<IllegalArgumentException> {
            generate(quote.copy(fields = emptyList()))
        }
    }

    @Test
    fun `supports all primitive types`() {
        val model = SchemaModel(
            packageName = "",
            className = "AllPrimitives",
            fields = listOf(
                FieldModel("a", "Long"),
                FieldModel("b", "Int"),
                FieldModel("c", "Double"),
                FieldModel("d", "Float"),
                FieldModel("e", "Short"),
                FieldModel("f", "Byte"),
                FieldModel("g", "Boolean"),
                FieldModel("h", "Char"),
            ),
        )
        val code = generate(model)
        for (arrayType in listOf("LongArray", "IntArray", "DoubleArray", "FloatArray", "ShortArray", "ByteArray", "BooleanArray", "CharArray")) {
            assertContains(code, arrayType)
        }
    }
}
