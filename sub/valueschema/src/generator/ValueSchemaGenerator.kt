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
import com.squareup.kotlinpoet.joinToCode

object ValueSchemaGenerator {

    fun columnName(fieldName: String): String = fieldName + "s"

    fun generate(model: SchemaModel): FileSpec {
        require(model.fields.isNotEmpty()) { "${model.className} must have at least one field" }
        val leaves = model.leaves
        require(leaves.map { it.flatName }.distinct().size == leaves.size) {
            "${model.className} has colliding flattened leaf names: ${leaves.map { it.flatName }}"
        }

        val cls = ClassName(model.packageName, model.className)
        val colsName = "${model.className}Columns"
        val viewName = "${model.className}View"
        val cols = ClassName(model.packageName, colsName)
        val view = ClassName(model.packageName, viewName)
        val firstColumn = columnName(leaves.first().flatName)

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
            .addProperties(leaves.map { leaf ->
                PropertySpec.builder(columnName(leaf.flatName), leaf.column.arrayType)
                    .mutable()
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(PublishedApi::class)
                    .initializer("%T(initialCapacity.coerceAtLeast(1))", leaf.column.arrayType)
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
                        leaves.forEach { leaf ->
                            addStatement("%N[size] = %L", columnName(leaf.flatName), leaf.accessExpr("value"))
                        }
                        addStatement("size++")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("add")
                    .addKdoc("Zero-allocation overload: appends a row without constructing a [%T].", cls)
                    .addParameters(leaves.map { ParameterSpec.builder(it.flatName, it.column.type).build() })
                    .apply {
                        addStatement("ensureCapacity(size + 1)")
                        leaves.forEach { leaf ->
                            addStatement("%N[size] = %N", columnName(leaf.flatName), leaf.flatName)
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
                        "return %L",
                        constructorCall(model, cls) { leaf -> CodeBlock.of("%N[index]", columnName(leaf.flatName)) },
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("set")
                    .addKdoc("Zero-allocation overload: replaces a row without constructing a [%T].", cls)
                    .addParameter("index", INT)
                    .addParameters(leaves.map { ParameterSpec.builder(it.flatName, it.column.type).build() })
                    .apply {
                        addStatement("checkIndex(index)")
                        leaves.forEach { leaf ->
                            addStatement("%N[index] = %N", columnName(leaf.flatName), leaf.flatName)
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
                        leaves.forEach { leaf ->
                            addStatement("%N[index] = %L", columnName(leaf.flatName), leaf.accessExpr("value"))
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
                FunSpec.builder("resize")
                    .addKdoc(
                        "Grows to [newSize] rows; new rows are filled with @Default values (zero/false otherwise), " +
                            "like a freshly allocated primitive array. Shrinking only moves the size marker.",
                    )
                    .addParameter("newSize", INT)
                    .addStatement("require(newSize >= 0) { \"newSize must be >= 0: \" + newSize }")
                    .beginControlFlow("if (newSize > size)")
                    .addStatement("ensureCapacity(newSize)")
                    .apply {
                        leaves.forEach { leaf ->
                            addStatement(
                                "%N.fill(%L, size, newSize)",
                                columnName(leaf.flatName),
                                leaf.column.defaultLiteral(leaf.default),
                            )
                        }
                    }
                    .endControlFlow()
                    .addStatement("size = newSize")
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
                        leaves.forEach { leaf ->
                            addStatement("%N = %N.copyOf(newCapacity)", columnName(leaf.flatName), columnName(leaf.flatName))
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
            .addProperties(leaves.map { leaf ->
                PropertySpec.builder(leaf.flatName, leaf.column.type)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("return columns.%N[index]", columnName(leaf.flatName))
                            .build(),
                    )
                    .build()
            })
            .addFunction(
                FunSpec.builder("toValue")
                    .returns(cls)
                    .addStatement(
                        "return %L",
                        constructorCall(model, cls) { leaf -> CodeBlock.of("%N", leaf.flatName) },
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(STRING)
                    .addStatement(
                        "return \"$viewName(${leaves.joinToString(", ") { "${it.flatName}=\$${it.flatName}" }})\"",
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
        val leaves = model.leaves
        val layout = packedLayout(leaves)
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
                        emitPackedStores(leaves, layout) { it.accessExpr("value") }
                        addStatement("size++")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("add")
                    .addKdoc("Zero-allocation overload: appends a row without constructing a [%T].", cls)
                    .addParameters(leaves.map { ParameterSpec.builder(it.flatName, it.column.type).build() })
                    .apply {
                        addStatement("ensureCapacity(size + 1)")
                        addStatement("val base = size * STRIDE")
                        emitPackedStores(leaves, layout) { it.flatName }
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
                        "return %L",
                        constructorCall(model, cls) { leaf ->
                            val i = leaves.indexOfFirst { it.flatName == leaf.flatName }
                            CodeBlock.of("%L", slotRead(leaf.column, layout[i], "data[base + ${layout[i].slot}]"))
                        },
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("set")
                    .addKdoc("Zero-allocation overload: replaces a row without constructing a [%T].", cls)
                    .addParameter("index", INT)
                    .addParameters(leaves.map { ParameterSpec.builder(it.flatName, it.column.type).build() })
                    .apply {
                        addStatement("checkIndex(index)")
                        addStatement("val base = index * STRIDE")
                        emitPackedStores(leaves, layout) { it.flatName }
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
                        emitPackedStores(leaves, layout) { it.accessExpr("value") }
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
                FunSpec.builder("resize")
                    .addKdoc(
                        "Grows to [newSize] rows; new rows are filled with @Default values (zero/false otherwise), " +
                            "encoded as per-slot constant words. Shrinking only moves the size marker.",
                    )
                    .addParameter("newSize", INT)
                    .addStatement("require(newSize >= 0) { \"newSize must be >= 0: \" + newSize }")
                    .beginControlFlow("if (newSize > size)")
                    .addStatement("ensureCapacity(newSize)")
                    .apply {
                        val words = defaultSlotWords(leaves, layout)
                        if (words.all { it == 0L }) {
                            addStatement("data.fill(0L, size * STRIDE, newSize * STRIDE)")
                        } else {
                            words.forEachIndexed { slot, word ->
                                addStatement("run {")
                                addStatement("    var i = size * STRIDE + $slot")
                                addStatement("    val end = newSize * STRIDE")
                                addStatement("    while (i < end) {")
                                addStatement("        data[i] = ${word}L")
                                addStatement("        i += STRIDE")
                                addStatement("    }")
                                addStatement("}")
                            }
                        }
                    }
                    .endControlFlow()
                    .addStatement("size = newSize")
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
                            .initializer("${packedStride(leaves)}")
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
        val leaves = model.leaves
        val layout = packedLayout(leaves)
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
            .addProperties(leaves.mapIndexed { i, leaf ->
                PropertySpec.builder(leaf.flatName, leaf.column.type)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement(
                                "return %L",
                                slotRead(leaf.column, layout[i], "packed.data[index * $packedName.STRIDE + ${layout[i].slot}]"),
                            )
                            .build(),
                    )
                    .build()
            })
            .addFunction(
                FunSpec.builder("toValue")
                    .returns(cls)
                    .addStatement(
                        "return %L",
                        constructorCall(model, cls) { leaf -> CodeBlock.of("%N", leaf.flatName) },
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(STRING)
                    .addStatement(
                        "return \"$packedViewName(${leaves.joinToString(", ") { "${it.flatName}=\$${it.flatName}" }})\"",
                    )
                    .build(),
            )
            .build()
    }

    private fun lambdaOf(parameter: ClassName, returnType: TypeName): LambdaTypeName =
        LambdaTypeName.get(null, listOf(ParameterSpec.unnamed(parameter)), returnType)

    /** Rebuilds the (possibly nested) value constructor call from per-leaf read expressions. */
    private fun constructorCall(model: SchemaModel, cls: ClassName, leaf: (LeafColumn) -> CodeBlock): CodeBlock =
        CodeBlock.of(
            "%T(%L)",
            cls,
            model.fields.map { constructorExpr(it, emptyList(), leaf) }.joinToCode(", "),
        )

    private fun constructorExpr(field: SchemaField, prefix: List<String>, leaf: (LeafColumn) -> CodeBlock): CodeBlock =
        when (field) {
            is FieldModel -> leaf(LeafColumn(prefix + field.name, field.column))
            is NestedModel -> CodeBlock.of(
                "%T(%L)",
                field.type,
                field.children.map { constructorExpr(it, prefix + field.name, leaf) }.joinToCode(", "),
            )
        }

    private enum class StorageKind { COLUMNS, PACKED }

    private fun transformFunction(
        model: SchemaModel,
        transform: TransformModel,
        cls: ClassName,
        kind: StorageKind,
        layout: List<Placement>?,
    ): FunSpec {
        val leaves = model.leaves
        val builder = FunSpec.builder(transform.name)
            .addKdoc(
                "Field-wise transform declared via @ValueTransform; unconditionally allocation-free (no [%T] is constructed).",
                cls,
            )
            .addParameter("index", INT)
        parseParams(transform.params).forEach { builder.addParameter(it) }
        builder.addStatement("checkIndex(index)")
        if (kind == StorageKind.PACKED) builder.addStatement("val base = index * STRIDE")
        leaves.forEachIndexed { i, leaf ->
            when (kind) {
                StorageKind.COLUMNS -> builder.addStatement("var %N = %N[index]", leaf.flatName, columnName(leaf.flatName))
                StorageKind.PACKED ->
                    builder.addStatement("var %N = %L", leaf.flatName, slotRead(leaf.column, layout!![i], "data[base + ${layout[i].slot}]"))
            }
        }
        builder.addCode(CodeBlock.of("%L", transform.body.trim().let { if (it.isEmpty()) "" else it + "\n" }))
        when (kind) {
            StorageKind.COLUMNS -> leaves.forEach { leaf ->
                builder.addStatement("%N[index] = %N", columnName(leaf.flatName), leaf.flatName)
            }
            StorageKind.PACKED -> builder.emitPackedStores(leaves, layout!!) { it.flatName }
        }
        return builder.build()
    }

    /**
     * Emits one direct store per 64-bit slot. Every generated writer always writes ALL leaf
     * fields, so no slot ever contains foreign bits worth preserving: fields sharing a slot
     * are OR-folded into a single full-slot value instead of a read-modify-write per field.
     */
    private fun FunSpec.Builder.emitPackedStores(
        leaves: List<LeafColumn>,
        layout: List<Placement>,
        valueExpr: (LeafColumn) -> String,
    ) {
        leaves.indices.groupBy { layout[it].slot }.forEach { (slot, idxs) ->
            val expr = idxs.joinToString(" or ") { i ->
                val bits = valueBits(leaves[i].column, valueExpr(leaves[i]))
                if (layout[i].bitOffset == 0) bits else "($bits shl ${layout[i].bitOffset})"
            }
            addStatement("data[base + $slot] = $expr")
        }
    }

    /** Default row as per-slot constant words: each leaf's encoded default OR-ed into its slot. */
    private fun defaultSlotWords(leaves: List<LeafColumn>, layout: List<Placement>): LongArray {
        val words = LongArray(layout.last().slot + 1)
        leaves.forEachIndexed { i, leaf ->
            words[layout[i].slot] = words[layout[i].slot] or (leaf.column.defaultBits(leaf.default) shl layout[i].bitOffset)
        }
        return words
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

    private fun slotRead(col: PrimitiveColumn, p: Placement, slotRef: String): String {
        val raw = if (p.bitOffset == 0) slotRef else "($slotRef ushr ${p.bitOffset})"
        return when (col) {
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

    private fun valueBits(col: PrimitiveColumn, valueExpr: String): String = when (col) {
        PrimitiveColumn.LONG -> valueExpr
        PrimitiveColumn.DOUBLE -> "$valueExpr.toRawBits()"
        PrimitiveColumn.INT -> "($valueExpr.toLong() and 0xFFFFFFFFL)"
        PrimitiveColumn.FLOAT -> "($valueExpr.toRawBits().toLong() and 0xFFFFFFFFL)"
        PrimitiveColumn.SHORT -> "($valueExpr.toLong() and 0xFFFFL)"
        PrimitiveColumn.BYTE -> "($valueExpr.toLong() and 0xFFL)"
        PrimitiveColumn.BOOLEAN -> "(if ($valueExpr) 1L else 0L)"
        PrimitiveColumn.CHAR -> "$valueExpr.code.toLong()"
    }

}
