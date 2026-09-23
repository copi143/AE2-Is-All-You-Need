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

/** Bit position of a field within the packed LongArray of one record. */
data class Placement(val slot: Int, val bitOffset: Int)

/** Lays out fields into 64-bit slots in declaration order; a field never crosses a slot boundary. */
fun packedLayout(fields: List<FieldModel>): List<Placement> {
    var slot = 0
    var usedBits = 0
    return fields.map { f ->
        val width = f.column.bitWidth
        if (usedBits + width > 64) {
            slot++
            usedBits = 0
        }
        val placement = Placement(slot, usedBits)
        usedBits += width
        placement
    }
}

fun packedStride(fields: List<FieldModel>): Int = packedLayout(fields).last().slot + 1

data class FieldModel(val name: String, val typeName: String) {
    val column: PrimitiveColumn
        get() = PrimitiveColumn.ofKotlinName(typeName)
            ?: throw IllegalArgumentException("Unsupported field type '$typeName' for field '$name'; only primitives are supported")
}

data class TransformModel(val name: String, val params: String, val body: String)

data class SchemaModel(
    val packageName: String,
    val className: String,
    val fields: List<FieldModel>,
    val transforms: List<TransformModel> = emptyList(),
)
