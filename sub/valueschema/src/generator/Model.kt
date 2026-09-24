package io.github.copi143.valueschema.generator

import com.squareup.kotlinpoet.ClassName

enum class PrimitiveColumn(val kotlinName: String, val type: ClassName, val arrayType: ClassName, val bitWidth: Int) {
    LONG("Long", com.squareup.kotlinpoet.LONG, com.squareup.kotlinpoet.LONG_ARRAY, 64),
    INT("Int", com.squareup.kotlinpoet.INT, com.squareup.kotlinpoet.INT_ARRAY, 32),
    DOUBLE("Double", com.squareup.kotlinpoet.DOUBLE, com.squareup.kotlinpoet.DOUBLE_ARRAY, 64),
    FLOAT("Float", com.squareup.kotlinpoet.FLOAT, com.squareup.kotlinpoet.FLOAT_ARRAY, 32),
    SHORT("Short", com.squareup.kotlinpoet.SHORT, com.squareup.kotlinpoet.SHORT_ARRAY, 16),
    BYTE("Byte", com.squareup.kotlinpoet.BYTE, com.squareup.kotlinpoet.BYTE_ARRAY, 8),
    BOOLEAN("Boolean", com.squareup.kotlinpoet.BOOLEAN, com.squareup.kotlinpoet.BOOLEAN_ARRAY, 1),
    CHAR("Char", com.squareup.kotlinpoet.CHAR, com.squareup.kotlinpoet.CHAR_ARRAY, 16),
    ;

    companion object {
        fun ofKotlinName(kotlinName: String): PrimitiveColumn? = entries.firstOrNull { it.kotlinName == kotlinName }
    }

    /** Parses a @Default literal (null = zero fill) into the typed constant. Throws on invalid input. */
    fun parseDefault(raw: String?): Any {
        if (raw == null) {
            return when (this) {
                LONG -> 0L
                INT -> 0
                DOUBLE -> 0.0
                FLOAT -> 0f
                SHORT -> 0.toShort()
                BYTE -> 0.toByte()
                BOOLEAN -> false
                CHAR -> '\u0000'
            }
        }
        return when (this) {
            LONG -> raw.toLong()
            INT -> raw.toInt()
            DOUBLE -> raw.toDouble().also { require(it.isFinite()) { "Double default must be finite: '$raw'" } }
            FLOAT -> raw.toFloat().also { require(it.isFinite()) { "Float default must be finite: '$raw'" } }
            SHORT -> raw.toInt().also { require(it in Short.MIN_VALUE..Short.MAX_VALUE) { "Short default out of range: '$raw'" } }.toShort()
            BYTE -> raw.toInt().also { require(it in Byte.MIN_VALUE..Byte.MAX_VALUE) { "Byte default out of range: '$raw'" } }.toByte()
            BOOLEAN -> raw.toBooleanStrict()
            CHAR -> {
                require(raw.length == 1) { "Char default must be a single character: '$raw'" }
                raw[0]
            }
        }
    }

    /** Kotlin source literal for the parsed default, for emission into generated code. */
    fun defaultLiteral(raw: String?): String = when (val v = parseDefault(raw)) {
        is Long -> "${v}L"
        is Double -> v.toString()
        is Float -> "${v}f"
        is Char -> "'\\u%04x'".format(v.code)
        else -> v.toString()
    }

    /** Raw bits of the parsed default as stored in a packed slot (not yet shifted). */
    fun defaultBits(raw: String?): Long = when (this) {
        LONG -> parseDefault(raw) as Long
        DOUBLE -> (parseDefault(raw) as Double).toRawBits()
        INT -> (parseDefault(raw) as Int).toLong() and 0xFFFFFFFFL
        FLOAT -> (parseDefault(raw) as Float).toRawBits().toLong() and 0xFFFFFFFFL
        SHORT -> (parseDefault(raw) as Short).toLong() and 0xFFFFL
        BYTE -> (parseDefault(raw) as Byte).toLong() and 0xFFL
        BOOLEAN -> if (parseDefault(raw) as Boolean) 1L else 0L
        CHAR -> (parseDefault(raw) as Char).code.toLong()
    }
}

/** Bit position of a leaf field within the packed LongArray of one record. */
data class Placement(val slot: Int, val bitOffset: Int)

sealed interface SchemaField {
    val name: String
}

/** A leaf field backed directly by a primitive column / bit-packed slot. */
data class FieldModel(override val name: String, val typeName: String, val default: String? = null) : SchemaField {
    val column: PrimitiveColumn
        get() = PrimitiveColumn.ofKotlinName(typeName)
            ?: throw IllegalArgumentException("Unsupported field type '$typeName' for field '$name'; only primitives are supported")
}

/** A nested @ValueSchema value type; flattened into the parent storage like a Valhalla value class field. */
data class NestedModel(override val name: String, val type: ClassName, val children: List<SchemaField>) : SchemaField

/** A leaf of the flattened field tree: its access path from the root plus its primitive column. */
data class LeafColumn(val path: List<String>, val column: PrimitiveColumn, val default: String? = null) {
    /** Column/slot-local name, e.g. `price_amount` for path [price, amount]. */
    val flatName: String get() = path.joinToString("_")

    /** Access expression on a value instance, e.g. `value.price.amount`. */
    fun accessExpr(root: String): String = (listOf(root) + path).joinToString(".")
}

fun List<SchemaField>.flatten(prefix: List<String> = emptyList()): List<LeafColumn> = flatMap { f ->
    when (f) {
        is FieldModel -> listOf(LeafColumn(prefix + f.name, f.column, f.default))
        is NestedModel -> f.children.flatten(prefix + f.name)
    }
}

/** Lays out leaf fields into 64-bit slots in declaration order; a field never crosses a slot boundary. */
fun packedLayout(leaves: List<LeafColumn>): List<Placement> {
    var slot = 0
    var usedBits = 0
    return leaves.map { leaf ->
        val width = leaf.column.bitWidth
        if (usedBits + width > 64) {
            slot++
            usedBits = 0
        }
        val placement = Placement(slot, usedBits)
        usedBits += width
        placement
    }
}

fun packedStride(leaves: List<LeafColumn>): Int = packedLayout(leaves).last().slot + 1

data class TransformModel(val name: String, val params: String, val body: String)

data class SchemaModel(
    val packageName: String,
    val className: String,
    val fields: List<SchemaField>,
    val transforms: List<TransformModel> = emptyList(),
) {
    val leaves: List<LeafColumn> get() = fields.flatten()
}
