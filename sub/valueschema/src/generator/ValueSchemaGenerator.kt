package io.github.copi143.valueschema.generator

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
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
                PropertySpec.builder("size", INT)
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
                FunSpec.builder("addAll")
                    .addParameter("values", ClassName("kotlin.collections", "Iterable").parameterizedBy(cls))
                    .beginControlFlow("for (value in values)")
                    .addStatement("add(value)")
                    .endControlFlow()
                    .build(),
            )
            .addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", INT)
                    .returns(cls)
                    .addStatement("checkIndex(index)")
                    .addStatement(
                        "return %T(${model.fields.joinToString(", ") { "${columnName(it.name)}[index]" }})",
                        cls,
                    )
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
                FunSpec.builder("forEach")
                    .addKdoc("Iterates rows reusing a single cursor; do not store the view passed to [action].")
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
                FunSpec.builder("filterTo")
                    .addModifiers(KModifier.INLINE)
                    .addParameter("destination", cols)
                    .addParameter("predicate", lambdaOf(view, BOOLEAN))
                    .returns(cols)
                    .beginControlFlow("forEach { v ->")
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
                    .beginControlFlow("forEach {")
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

        return FileSpec.builder(model.packageName, "${model.className}Schema")
            .addFileComment("Generated by valueschema from @ValueSchema ${model.className}. Do not edit.")
            .addType(columnsType)
            .addType(viewType)
            .build()
    }

    private fun lambdaOf(parameter: ClassName, returnType: TypeName): LambdaTypeName =
        LambdaTypeName.get(null, listOf(ParameterSpec.unnamed(parameter)), returnType)
}
