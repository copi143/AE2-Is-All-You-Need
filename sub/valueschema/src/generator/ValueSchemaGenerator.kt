package io.github.copi143.valueschema.generator

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT

object ValueSchemaGenerator {

    fun columnName(fieldName: String): String = fieldName + "s"

    fun generate(model: SchemaModel): FileSpec {
        require(model.fields.isNotEmpty()) { "${model.className} must have at least one field" }
        model.fields.forEach { it.column }

        val cls = ClassName(model.packageName, model.className)
        val colsName = "${model.className}Columns"
        val viewName = "${model.className}View"
        val cols = ClassName(model.packageName, colsName)
        val view = ClassName(model.packageName, viewName)
        val firstColumn = columnName(model.fields.first().name)

        val columnsType = TypeSpec.classBuilder(colsName)
            .addKdoc(
                "Struct-of-arrays storage for [%T]. Disposable performance layer; keep business logic on [%T] semantics.",
                cls, cls,
            )
            .superclass(ClassName("kotlin.collections", "AbstractList").parameterizedBy(cls))
            .addSuperclassConstructorParameter("%L", "")
            .addSuperinterface(ClassName("java.util", "RandomAccess"))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("initialCapacity", INT)
                            .defaultValue("16")
                            .build(),
                    )
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("size", INT, KModifier.OVERRIDE)
                    .mutable()
                    .initializer("0")
                    .setter(FunSpec.setterBuilder().addModifiers(KModifier.PRIVATE).build())
                    .build(),
            )
            .addProperties(model.fields.map { f ->
                PropertySpec.builder(columnName(f.name), f.column.arrayType)
                    .mutable()
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(PublishedApi::class)
                    .initializer("%T(initialCapacity.coerceAtLeast(1))", f.column.arrayType)
                    .build()
            })
            .addProperty(
                PropertySpec.builder("capacity", INT)
                    .getter(FunSpec.getterBuilder().addStatement("return %N.size", firstColumn).build())
                    .build(),
            )
            .addFunction(
                FunSpec.builder("add")
                    .addParameter("value", cls)
                    .apply {
                        addStatement("ensureCapacity(size + 1)")
                        model.fields.forEach { f ->
                            addStatement("%N[size] = value.%N", columnName(f.name), f.name)
                        }
                        addStatement("size++")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("add")
                    .addKdoc("Zero-allocation overload: appends a row without constructing a [%T].", cls)
                    .addParameters(model.fields.map { ParameterSpec.builder(it.name, it.column.type).build() })
                    .apply {
                        addStatement("ensureCapacity(size + 1)")
                        model.fields.forEach { f ->
                            addStatement("%N[size] = %N", columnName(f.name), f.name)
                        }
                        addStatement("size++")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("addAll")
                    .addParameter("values", ClassName("kotlin.collections", "Iterable").parameterizedBy(cls))
                    .beginControlFlow("for (value in values)")
                    .addStatement("add(value)")
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OPERATOR, KModifier.OVERRIDE)
                    .addParameter("index", INT)
                    .returns(cls)
                    .addStatement("checkIndex(index)")
                    .addStatement(
                        "return %T(${model.fields.joinToString(", ") { "%N[index]" }})",
                        cls,
                        *model.fields.map { columnName(it.name) }.toTypedArray(),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("set")
                    .addKdoc("Zero-allocation overload: replaces a row without constructing a [%T].", cls)
                    .addParameter("index", INT)
                    .addParameters(model.fields.map { ParameterSpec.builder(it.name, it.column.type).build() })
                    .apply {
                        addStatement("checkIndex(index)")
                        model.fields.forEach { f ->
                            addStatement("%N[index] = %N", columnName(f.name), f.name)
                        }
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("set")
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", INT)
                    .addParameter("value", cls)
                    .apply {
                        addStatement("checkIndex(index)")
                        model.fields.forEach { f ->
                            addStatement("%N[index] = value.%N", columnName(f.name), f.name)
                        }
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("view")
                    .addParameter("index", INT)
                    .returns(view)
                    .addStatement("checkIndex(index)")
                    .addStatement("return %T(this, index)", view)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("forEachView")
                    .addKdoc(
                        "Iterates rows reusing a single cursor; do not store the view passed to [action]. " +
                            "Named forEachView on purpose: plain `forEach` would silently bind to Iterable.forEach and materialize every row.",
                    )
                    .addModifiers(KModifier.INLINE)
                    .addParameter("action", lambdaOf(view, UNIT))
                    .addStatement("val v = %T(this, 0)", view)
                    .addStatement("var i = 0")
                    .beginControlFlow("while (i < size)")
                    .addStatement("v.index = i")
                    .addStatement("action(v)")
                    .addStatement("i++")
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("updateAll")
                    .addKdoc("Replaces every row with `transform(row)`; written in value style so it also works on future value-type arrays.")
                    .addModifiers(KModifier.INLINE)
                    .addParameter("transform", lambdaOf(cls, cls))
                    .addStatement("var i = 0")
                    .beginControlFlow("while (i < size)")
                    .addStatement("set(i, transform(get(i)))")
                    .addStatement("i++")
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("updateAt")
                    .addKdoc(
                        "Replaces one row with `transform(row)`. The temporary [%T] instances are stack-local " +
                            "and scalar-replaced by C2 in hot code, so no heap allocation survives JIT compilation.",
                        cls,
                    )
                    .addModifiers(KModifier.INLINE)
                    .addParameter("index", INT)
                    .addParameter("transform", lambdaOf(cls, cls))
                    .addStatement("set(index, transform(get(index)))")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("filterTo")
                    .addModifiers(KModifier.INLINE)
                    .addParameter("destination", cols)
                    .addParameter("predicate", lambdaOf(view, BOOLEAN))
                    .returns(cols)
                    .beginControlFlow("forEachView { v ->")
                    .beginControlFlow("if (predicate(v))")
                    .addStatement("destination.add(v.toValue())")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return destination")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("filtered")
                    .addParameter("predicate", lambdaOf(view, BOOLEAN))
                    .returns(cols)
                    .addStatement("return filterTo(%T(size), predicate)", cols)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toList")
                    .returns(ClassName("kotlin.collections", "List").parameterizedBy(cls))
                    .addStatement("val result = %T<%T>(size)", ClassName("java.util", "ArrayList"), cls)
                    .beginControlFlow("forEachView {")
                    .addStatement("result.add(it.toValue())")
                    .endControlFlow()
                    .addStatement("return result")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("clear")
                    .addStatement("size = 0")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(STRING)
                    .addStatement("return \"$colsName(size=\$size)\"")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("checkIndex")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("index", INT)
                    .beginControlFlow("if (index < 0 || index >= size)")
                    .addStatement("throw %T(\"index \" + index + \", size \" + size)", IndexOutOfBoundsException::class)
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("ensureCapacity")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("minCapacity", INT)
                    .addStatement("var newCapacity = %N.size", firstColumn)
                    .beginControlFlow("if (minCapacity <= newCapacity)")
                    .addStatement("return")
                    .endControlFlow()
                    .beginControlFlow("while (newCapacity < minCapacity)")
                    .addStatement("newCapacity *= 2")
                    .endControlFlow()
                    .apply {
                        model.fields.forEach { f ->
                            addStatement("%N = %N.copyOf(newCapacity)", columnName(f.name), columnName(f.name))
                        }
                    }
                    .build(),
            )
            .addFunctions(model.transforms.map { transformFunction(model, it, cls, StorageKind.COLUMNS, null) })
            .build()

        val viewType = TypeSpec.classBuilder(viewName)
            .addKdoc("Zero-allocation cursor over one row of [%T]. Valid only while positioned; do not store it.", cols)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addAnnotation(PublishedApi::class)
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("columns", cols)
                    .addParameter("index", INT)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("columns", cols)
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(PublishedApi::class)
                    .initializer("columns")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("index", INT)
                    .mutable()
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(PublishedApi::class)
                    .initializer("index")
                    .build(),
            )
            .addProperties(model.fields.map { f ->
                PropertySpec.builder(f.name, f.column.type)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("return columns.%N[index]", columnName(f.name))
                            .build(),
                    )
                    .build()
            })
            .addFunction(
                FunSpec.builder("toValue")
                    .returns(cls)
                    .addStatement("return %T(${model.fields.joinToString(", ") { it.name }})", cls)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(STRING)
                    .addStatement(
                        "return \"$viewName(${model.fields.joinToString(", ") { "${it.name}=\$${it.name}" }})\"",
                    )
                    .build(),
            )
            .build()

        val packedName = "${model.className}Packed"
        val packedViewName = "${model.className}PackedView"
        val packed = ClassName(model.packageName, packedName)

        val packedType = packedType(model, cls, cols, packed, packedName)
        val packedViewType = packedViewType(model, cls, packed, packedViewName, packedName)

        return FileSpec.builder(model.packageName, "${model.className}Schema")
            .addFileComment("Generated by valueschema from @ValueSchema ${model.className}. Do not edit.")
            .addType(columnsType)
            .addType(viewType)
            .addType(packedType)
            .addType(packedViewType)
            .build()
    }

    private fun packedType(
        model: SchemaModel,
        cls: ClassName,
        cols: ClassName,
        packed: ClassName,
        packedName: String,
    ): TypeSpec {
        val viewName = "${model.className}PackedView"
        val view = ClassName(model.packageName, viewName)
        val layout = packedLayout(model.fields)
        return TypeSpec.classBuilder(packedName)
            .addKdoc(
                "Bit-packed flat-array storage for [%T]: a single LongArray, fields packed into 64-bit slots " +
                    "by their bit width (row stride = STRIDE slots). Prefer this for whole-row access; prefer [%T] for column scans.",
                cls, cols,
            )
            .superclass(ClassName("kotlin.collections", "AbstractList").parameterizedBy(cls))
            .addSuperclassConstructorParameter("%L", "")
            .addSuperinterface(ClassName("java.util", "RandomAccess"))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("initialCapacity", INT)
                            .defaultValue("16")
                            .build(),
                    )
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("size", INT, KModifier.OVERRIDE)
                    .mutable()
                    .initializer("0")
                    .setter(FunSpec.setterBuilder().addModifiers(KModifier.PRIVATE).build())
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("data", LONG_ARRAY)
                    .mutable()
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(PublishedApi::class)
                    .initializer("%T(initialCapacity.coerceAtLeast(1) * STRIDE)", LONG_ARRAY)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("capacity", INT)
                    .getter(FunSpec.getterBuilder().addStatement("return data.size / STRIDE").build())
                    .build(),
            )
            .addFunction(
                FunSpec.builder("add")
                    .addParameter("value", cls)
                    .apply {
                        addStatement("ensureCapacity(size + 1)")
                        addStatement("val base = size * STRIDE")
                        model.fields.forEachIndexed { i, f ->
                            addStatement("%L", slotWrite(f, layout[i], "data[base + ${layout[i].slot}]", "value.${f.name}"))
                        }
                        addStatement("size++")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("add")
                    .addKdoc("Zero-allocation overload: appends a row without constructing a [%T].", cls)
                    .addParameters(model.fields.map { ParameterSpec.builder(it.name, it.column.type).build() })
                    .apply {
                        addStatement("ensureCapacity(size + 1)")
                        addStatement("val base = size * STRIDE")
                        model.fields.forEachIndexed { i, f ->
                            addStatement("%L", slotWrite(f, layout[i], "data[base + ${layout[i].slot}]", f.name))
                        }
                        addStatement("size++")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("addAll")
                    .addParameter("values", ClassName("kotlin.collections", "Iterable").parameterizedBy(cls))
                    .beginControlFlow("for (value in values)")
                    .addStatement("add(value)")
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OPERATOR, KModifier.OVERRIDE)
                    .addParameter("index", INT)
                    .returns(cls)
                    .addStatement("checkIndex(index)")
                    .addStatement("val base = index * STRIDE")
                    .addStatement(
                        "return %T(${model.fields.mapIndexed { i, f -> slotRead(f, layout[i], "data[base + ${layout[i].slot}]") }.joinToString(", ")})",
                        cls,
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("set")
                    .addKdoc("Zero-allocation overload: replaces a row without constructing a [%T].", cls)
                    .addParameter("index", INT)
                    .addParameters(model.fields.map { ParameterSpec.builder(it.name, it.column.type).build() })
                    .apply {
                        addStatement("checkIndex(index)")
                        addStatement("val base = index * STRIDE")
                        model.fields.forEachIndexed { i, f ->
                            addStatement("%L", slotWrite(f, layout[i], "data[base + ${layout[i].slot}]", f.name))
                        }
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("set")
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", INT)
                    .addParameter("value", cls)
                    .apply {
                        addStatement("checkIndex(index)")
                        addStatement("val base = index * STRIDE")
                        model.fields.forEachIndexed { i, f ->
                            addStatement("%L", slotWrite(f, layout[i], "data[base + ${layout[i].slot}]", "value.${f.name}"))
                        }
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("view")
                    .addParameter("index", INT)
                    .returns(view)
                    .addStatement("checkIndex(index)")
                    .addStatement("return %T(this, index)", view)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("forEachView")
                    .addKdoc(
                        "Iterates rows reusing a single cursor; do not store the view passed to [action]. " +
                            "Named forEachView on purpose: plain `forEach` would silently bind to Iterable.forEach and materialize every row.",
                    )
                    .addModifiers(KModifier.INLINE)
                    .addParameter("action", lambdaOf(view, UNIT))
                    .addStatement("val v = %T(this, 0)", view)
                    .addStatement("var i = 0")
                    .beginControlFlow("while (i < size)")
                    .addStatement("v.index = i")
                    .addStatement("action(v)")
                    .addStatement("i++")
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("updateAll")
                    .addKdoc("Replaces every row with `transform(row)`; written in value style so it also works on future value-type arrays.")
                    .addModifiers(KModifier.INLINE)
                    .addParameter("transform", lambdaOf(cls, cls))
                    .addStatement("var i = 0")
                    .beginControlFlow("while (i < size)")
                    .addStatement("set(i, transform(get(i)))")
                    .addStatement("i++")
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("updateAt")
                    .addKdoc(
                        "Replaces one row with `transform(row)`. The temporary [%T] instances are stack-local " +
                            "and scalar-replaced by C2 in hot code, so no heap allocation survives JIT compilation.",
                        cls,
                    )
                    .addModifiers(KModifier.INLINE)
                    .addParameter("index", INT)
                    .addParameter("transform", lambdaOf(cls, cls))
                    .addStatement("set(index, transform(get(index)))")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("filterTo")
                    .addModifiers(KModifier.INLINE)
                    .addParameter("destination", packed)
                    .addParameter("predicate", lambdaOf(view, BOOLEAN))
                    .returns(packed)
                    .beginControlFlow("forEachView { v ->")
                    .beginControlFlow("if (predicate(v))")
                    .addStatement("destination.add(v.toValue())")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return destination")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("filtered")
                    .addParameter("predicate", lambdaOf(view, BOOLEAN))
                    .returns(packed)
                    .addStatement("return filterTo(%T(size), predicate)", packed)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toList")
                    .returns(ClassName("kotlin.collections", "List").parameterizedBy(cls))
                    .addStatement("val result = %T<%T>(size)", ClassName("java.util", "ArrayList"), cls)
                    .beginControlFlow("forEachView {")
                    .addStatement("result.add(it.toValue())")
                    .endControlFlow()
                    .addStatement("return result")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("clear")
                    .addStatement("size = 0")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(STRING)
                    .addStatement("return \"$packedName(size=\$size)\"")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("checkIndex")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("index", INT)
                    .beginControlFlow("if (index < 0 || index >= size)")
                    .addStatement("throw %T(\"index \" + index + \", size \" + size)", IndexOutOfBoundsException::class)
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("ensureCapacity")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("minCapacity", INT)
                    .addStatement("val minSlots = minCapacity * STRIDE")
                    .addStatement("var newCapacity = data.size")
                    .beginControlFlow("if (minSlots <= newCapacity)")
                    .addStatement("return")
                    .endControlFlow()
                    .beginControlFlow("while (newCapacity < minSlots)")
                    .addStatement("newCapacity *= 2")
                    .endControlFlow()
                    .addStatement("data = data.copyOf(newCapacity)")
                    .build(),
            )
            .addFunctions(model.transforms.map { transformFunction(model, it, cls, StorageKind.PACKED, layout) })
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder("STRIDE", INT)
                            .addModifiers(KModifier.CONST)
                            .initializer("${packedStride(model.fields)}")
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun packedViewType(
        model: SchemaModel,
        cls: ClassName,
        packed: ClassName,
        packedViewName: String,
        packedName: String,
    ): TypeSpec {
        val layout = packedLayout(model.fields)
        return TypeSpec.classBuilder(packedViewName)
            .addKdoc("Zero-allocation cursor over one row of [%T]. Valid only while positioned; do not store it.", packed)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addAnnotation(PublishedApi::class)
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("packed", packed)
                    .addParameter("index", INT)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("packed", packed)
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(PublishedApi::class)
                    .initializer("packed")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("index", INT)
                    .mutable()
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(PublishedApi::class)
                    .initializer("index")
                    .build(),
            )
            .addProperties(model.fields.mapIndexed { i, f ->
                PropertySpec.builder(f.name, f.column.type)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement(
                                "return %L",
                                slotRead(f, layout[i], "packed.data[index * $packedName.STRIDE + ${layout[i].slot}]"),
                            )
                            .build(),
                    )
                    .build()
            })
            .addFunction(
                FunSpec.builder("toValue")
                    .returns(cls)
                    .addStatement("return %T(${model.fields.joinToString(", ") { it.name }})", cls)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(STRING)
                    .addStatement(
                        "return \"$packedViewName(${model.fields.joinToString(", ") { "${it.name}=\$${it.name}" }})\"",
                    )
                    .build(),
            )
            .build()
    }

    private fun lambdaOf(parameter: ClassName, returnType: TypeName): LambdaTypeName =
        LambdaTypeName.get(null, listOf(ParameterSpec.unnamed(parameter)), returnType)

    private enum class StorageKind { COLUMNS, PACKED }

    private fun transformFunction(
        model: SchemaModel,
        transform: TransformModel,
        cls: ClassName,
        kind: StorageKind,
        layout: List<Placement>?,
    ): FunSpec {
        val builder = FunSpec.builder(transform.name)
            .addKdoc(
                "Field-wise transform declared via @ValueTransform; unconditionally allocation-free (no [%T] is constructed).",
                cls,
            )
            .addParameter("index", INT)
        parseParams(transform.params).forEach { builder.addParameter(it) }
        builder.addStatement("checkIndex(index)")
        if (kind == StorageKind.PACKED) builder.addStatement("val base = index * STRIDE")
        model.fields.forEachIndexed { i, f ->
            when (kind) {
                StorageKind.COLUMNS -> builder.addStatement("var %N = %N[index]", f.name, columnName(f.name))
                StorageKind.PACKED ->
                    builder.addStatement("var %N = %L", f.name, slotRead(f, layout!![i], "data[base + ${layout[i].slot}]"))
            }
        }
        builder.addCode(CodeBlock.of("%L", transform.body.trim().let { if (it.isEmpty()) "" else it + "\n" }))
        model.fields.forEachIndexed { i, f ->
            when (kind) {
                StorageKind.COLUMNS -> builder.addStatement("%N[index] = %N", columnName(f.name), f.name)
                StorageKind.PACKED -> builder.addStatement("%L", slotWrite(f, layout!![i], "data[base + ${layout[i].slot}]", f.name))
            }
        }
        return builder.build()
    }

    private fun parseParams(params: String): List<ParameterSpec> {
        if (params.isBlank()) return emptyList()
        return params.split(",").map { raw ->
            val parts = raw.trim().split(":")
            require(parts.size == 2) { "Invalid @ValueTransform param '$raw'; expected 'name: Type'" }
            ParameterSpec.builder(parts[0].trim(), typeNameOf(parts[1].trim())).build()
        }
    }

    private fun typeNameOf(name: String): TypeName = when (name) {
        "Long" -> LONG
        "Int" -> INT
        "Double" -> DOUBLE
        "Float" -> FLOAT
        "Short" -> SHORT
        "Byte" -> BYTE
        "Boolean" -> BOOLEAN
        "Char" -> CHAR
        "String" -> STRING
        else -> ClassName.bestGuess(name)
    }

    private fun slotRead(f: FieldModel, p: Placement, slotRef: String): String {
        val raw = if (p.bitOffset == 0) slotRef else "($slotRef ushr ${p.bitOffset})"
        return when (f.column) {
            PrimitiveColumn.LONG -> slotRef
            PrimitiveColumn.DOUBLE -> "Double.fromBits($slotRef)"
            PrimitiveColumn.INT -> "$raw.toInt()"
            PrimitiveColumn.FLOAT -> "Float.fromBits($raw.toInt())"
            PrimitiveColumn.SHORT -> "$raw.toShort()"
            PrimitiveColumn.BYTE -> "$raw.toByte()"
            PrimitiveColumn.BOOLEAN -> "($raw and 1L) != 0L"
            PrimitiveColumn.CHAR -> "$raw.toInt().toChar()"
        }
    }

    private fun valueBits(f: FieldModel, valueExpr: String): String = when (f.column) {
        PrimitiveColumn.LONG -> valueExpr
        PrimitiveColumn.DOUBLE -> "$valueExpr.toRawBits()"
        PrimitiveColumn.INT -> "($valueExpr.toLong() and 0xFFFFFFFFL)"
        PrimitiveColumn.FLOAT -> "($valueExpr.toRawBits().toLong() and 0xFFFFFFFFL)"
        PrimitiveColumn.SHORT -> "($valueExpr.toLong() and 0xFFFFL)"
        PrimitiveColumn.BYTE -> "($valueExpr.toLong() and 0xFFL)"
        PrimitiveColumn.BOOLEAN -> "(if ($valueExpr) 1L else 0L)"
        PrimitiveColumn.CHAR -> "$valueExpr.code.toLong()"
    }

    private fun slotWrite(f: FieldModel, p: Placement, slotRef: String, valueExpr: String): String {
        val bits = valueBits(f, valueExpr)
        if (f.column.bitWidth == 64) return "$slotRef = $bits"
        val mask = maskLiteral(f.column.bitWidth)
        val cleared = if (p.bitOffset == 0) {
            "($slotRef and $mask.inv())"
        } else {
            "($slotRef and ($mask shl ${p.bitOffset}).inv())"
        }
        val shifted = if (p.bitOffset == 0) bits else "($bits shl ${p.bitOffset})"
        return "$slotRef = $cleared or $shifted"
    }

    private fun maskLiteral(width: Int): String = when (width) {
        32 -> "0xFFFFFFFFL"
        16 -> "0xFFFFL"
        8 -> "0xFFL"
        1 -> "0x1L"
        else -> error("no mask for width $width")
    }
}
