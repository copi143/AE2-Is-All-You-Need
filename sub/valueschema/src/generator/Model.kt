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
}

/** Bit position of a leaf field within the packed LongArray of one record. */
data class Placement(val slot: Int, val bitOffset: Int)

sealed interface SchemaField {
    val name: String
}

/** A leaf field backed directly by a primitive column / bit-packed slot. */
data class FieldModel(override val name: String, val typeName: String) : SchemaField {
    val column: PrimitiveColumn
        get() = PrimitiveColumn.ofKotlinName(typeName)
            ?: throw IllegalArgumentException("Unsupported field type '$typeName' for field '$name'; only primitives are supported")
}

/** A nested @ValueSchema value type; flattened into the parent storage like a Valhalla value class field. */
data class NestedModel(override val name: String, val type: ClassName, val children: List<SchemaField>) : SchemaField

/** A leaf of the flattened field tree: its access path from the root plus its primitive column. */
data class LeafColumn(val path: List<String>, val column: PrimitiveColumn) {
    /** Column/slot-local name, e.g. `price_amount` for path [price, amount]. */
    val flatName: String get() = path.joinToString("_")

    /** Access expression on a value instance, e.g. `value.price.amount`. */
    fun accessExpr(root: String): String = (listOf(root) + path).joinToString(".")
}

fun List<SchemaField>.flatten(prefix: List<String> = emptyList()): List<LeafColumn> = flatMap { f ->
    when (f) {
        is FieldModel -> listOf(LeafColumn(prefix + f.name, f.column))
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
