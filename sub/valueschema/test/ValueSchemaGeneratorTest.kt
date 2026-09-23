package io.github.copi143.valueschema

import io.github.copi143.valueschema.generator.FieldModel
import io.github.copi143.valueschema.generator.SchemaModel
import io.github.copi143.valueschema.generator.ValueSchemaGenerator
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ValueSchemaGeneratorTest {

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
