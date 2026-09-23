package io.github.copi143.valueschema.generator

import com.squareup.kotlinpoet.ClassName

enum class PrimitiveColumn(val kotlinName: String, val type: ClassName, val arrayType: ClassName) {
    LONG("Long", com.squareup.kotlinpoet.LONG, com.squareup.kotlinpoet.LONG_ARRAY),
    INT("Int", com.squareup.kotlinpoet.INT, com.squareup.kotlinpoet.INT_ARRAY),
    DOUBLE("Double", com.squareup.kotlinpoet.DOUBLE, com.squareup.kotlinpoet.DOUBLE_ARRAY),
    FLOAT("Float", com.squareup.kotlinpoet.FLOAT, com.squareup.kotlinpoet.FLOAT_ARRAY),
    SHORT("Short", com.squareup.kotlinpoet.SHORT, com.squareup.kotlinpoet.SHORT_ARRAY),
    BYTE("Byte", com.squareup.kotlinpoet.BYTE, com.squareup.kotlinpoet.BYTE_ARRAY),
    BOOLEAN("Boolean", com.squareup.kotlinpoet.BOOLEAN, com.squareup.kotlinpoet.BOOLEAN_ARRAY),
    CHAR("Char", com.squareup.kotlinpoet.CHAR, com.squareup.kotlinpoet.CHAR_ARRAY),
    ;

    companion object {
        fun ofKotlinName(kotlinName: String): PrimitiveColumn? = entries.firstOrNull { it.kotlinName == kotlinName }
    }
}

data class FieldModel(val name: String, val typeName: String) {
    val column: PrimitiveColumn
        get() = PrimitiveColumn.ofKotlinName(typeName)
            ?: throw IllegalArgumentException("Unsupported field type '$typeName' for field '$name'; only primitives are supported")
}

data class SchemaModel(val packageName: String, val className: String, val fields: List<FieldModel>)
