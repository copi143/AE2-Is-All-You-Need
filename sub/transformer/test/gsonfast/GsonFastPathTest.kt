package gsonfast

import com.google.gson.Gson
import com.google.gson.InstanceCreator
import com.google.gson.TypeAdapter
import com.google.gson.internal.ConstructorConstructor
import com.google.gson.internal.ObjectConstructor
import com.google.gson.internal.bind.GsonFastPath
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import gsonfast.pojos.AnnotatedPojo
import gsonfast.pojos.FinalPojo
import gsonfast.pojos.GenericPojo
import gsonfast.pojos.InheritedPojo
import gsonfast.pojos.SimplePojo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter

/**
 * 生成适配器与 gson 反射适配器的 roundtrip 一致性。
 * 反射适配器（gson 自身缓存中的）作为 ground truth。
 */
class GsonFastPathTest {
    private val gson = Gson()
    private val constructors = ConstructorConstructor(emptyMap<java.lang.reflect.Type, InstanceCreator<*>>(), true, emptyList())

    private fun <T> fastAdapter(type: TypeToken<T>, raw: Class<T>): TypeAdapter<T> {
        val original = gson.getAdapter(type)
        val ctor: ObjectConstructor<T> = constructors.get(type)
        @Suppress("UNCHECKED_CAST")
        val fast = GsonFastPath.wrap(original, gson, raw, ctor) as TypeAdapter<T>
        assertNotSame(original, fast, "fast path should generate an adapter for ${raw.name}")
        return fast
    }

    private fun <T> writeToString(adapter: TypeAdapter<T>, value: T?): String {
        val sw = StringWriter()
        val jw = JsonWriter(sw)
        adapter.write(jw, value)
        jw.flush()
        return sw.toString()
    }

    private fun <T> readFrom(adapter: TypeAdapter<T>, json: String): T? =
        adapter.read(JsonReader(StringReader(json)))

    private fun <T> assertConsistent(type: TypeToken<T>, raw: Class<T>, samples: List<T>, jsons: List<String>) {
        val original = gson.getAdapter(type)
        val fast = fastAdapter(type, raw)
        for (sample in samples) {
            val truth = writeToString(original, sample)
            assertEquals(truth, writeToString(fast, sample), "write mismatch for ${raw.name}")
        }
        for (json in jsons) {
            val truth = writeToString(original, readFrom(original, json))
            val actual = writeToString(original, readFrom(fast, json))
            assertEquals(truth, actual, "read mismatch for ${raw.name} on $json")
        }
    }

    @Test
    fun simplePojo() {
        val type = TypeToken.get(SimplePojo::class.java)
        val full = SimplePojo().apply {
            i = 1; l = 2L; d = 3.5; z = true; s = "txt"; list = listOf("a", "b"); nil = null
        }
        assertConsistent(
            type, SimplePojo::class.java,
            listOf(full, SimplePojo()),
            listOf(
                """{"i":5,"l":6,"d":7.25,"z":false,"s":"q","list":["x"],"nil":null}""",
                """{}""",
                """{"unknown":42,"i":9}""",
                """{"i":null,"s":null,"list":null}""",
            ),
        )
    }

    @Test
    fun annotatedPojo() {
        val type = TypeToken.get(AnnotatedPojo::class.java)
        val sample = AnnotatedPojo().apply { value = 3; other = "o" }
        assertConsistent(
            type, AnnotatedPojo::class.java,
            listOf(sample, AnnotatedPojo()),
            listOf(
                """{"x":1,"renamed":"a"}""",
                """{"y":2}""",
                """{"z":3,"x":4}""",
                """{"skipped":100,"x":1}""",
                """{"x":null}""",
            ),
        )
    }

    @Test
    fun inheritedPojo() {
        val type = TypeToken.get(InheritedPojo::class.java)
        val sample = InheritedPojo().apply { own = 1; fill(2L, "bp") }
        assertConsistent(
            type, InheritedPojo::class.java,
            listOf(sample),
            listOf(
                """{"own":1,"base":2,"basePrivate":"x"}""",
                """{"basePrivate":"only"}""",
                """{"base":null}""",
            ),
        )
    }

    @Test
    fun finalPojo() {
        val type = TypeToken.get(FinalPojo::class.java)
        assertConsistent(
            type, FinalPojo::class.java,
            listOf(FinalPojo()),
            listOf(
                """{"a":5,"b":"s","c":9}""",
                """{"a":null}""",
                """{}""",
            ),
        )
    }

    @Test
    fun genericPojo() {
        val type = object : TypeToken<GenericPojo<String>>() {}
        @Suppress("UNCHECKED_CAST")
        val raw = GenericPojo::class.java as Class<GenericPojo<String>>
        val sample = GenericPojo<String>().apply { data = "d"; n = 1 }
        assertConsistent(
            type, raw,
            listOf(sample, GenericPojo()),
            listOf(
                """{"data":"v","n":2}""",
                """{"data":null,"n":null}""",
            ),
        )
    }

    @Test
    fun nullHandling() {
        val type = TypeToken.get(SimplePojo::class.java)
        val fast = fastAdapter(type, SimplePojo::class.java)
        val original = gson.getAdapter(type)
        assertEquals("null", writeToString(fast, null))
        assertEquals(writeToString(original, null), writeToString(fast, null))
        assertEquals(null, readFrom(fast, "null"))
    }

    @Test
    fun selfReferenceSkipped() {
        val type = TypeToken.get(SimplePojo::class.java)
        val fast = fastAdapter(type, SimplePojo::class.java)
        val original = gson.getAdapter(type)
        val loop = SimplePojo().apply { nil = this; i = 1 }
        assertEquals(writeToString(original, loop), writeToString(fast, loop))
    }

    @Test
    fun statsCountUp() {
        val before = GsonFastPath.generatedCount()
        fastAdapter(TypeToken.get(SimplePojo::class.java), SimplePojo::class.java)
        assertEquals(before + 1, GsonFastPath.generatedCount())
    }
}
