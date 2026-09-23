package io.github.copi143.valueschema.processor

import io.github.copi143.valueschema.generator.FieldModel
import io.github.copi143.valueschema.generator.PrimitiveColumn
import io.github.copi143.valueschema.generator.SchemaModel
import io.github.copi143.valueschema.generator.TransformModel
import io.github.copi143.valueschema.generator.ValueSchemaGenerator
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

class ValueSchemaProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ValueSchemaProcessor(environment.codeGenerator, environment.logger)
}

private class ValueSchemaProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(VALUE_SCHEMA_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { decl ->
                val model = extract(decl) ?: return@forEach
                write(decl, model)
            }
        return emptyList()
    }

    private fun extract(decl: KSClassDeclaration): SchemaModel? {
        val name = decl.simpleName.asString()
        if (decl.classKind != ClassKind.CLASS || Modifier.DATA !in decl.modifiers) {
            logger.error("@ValueSchema must be applied to a data class, but $name is not", decl)
            return null
        }
        if (decl.parentDeclaration != null) {
            logger.error("@ValueSchema class $name must be top-level", decl)
            return null
        }
        val ctor = decl.primaryConstructor
        if (ctor == null) {
            logger.error("@ValueSchema class $name must have a primary constructor", decl)
            return null
        }
        val fields = mutableListOf<FieldModel>()
        for (param in ctor.parameters) {
            val fieldName = param.name?.asString() ?: continue
            if (!param.isVal) {
                logger.error("@ValueSchema class $name field '$fieldName' must be an immutable 'val'", param)
                return null
            }
            val typeName = param.type.resolve().declaration.qualifiedName?.asString()
                ?.removePrefix("kotlin.")
            if (typeName == null || PrimitiveColumn.ofKotlinName(typeName) == null) {
                logger.error(
                    "@ValueSchema class $name field '$fieldName' has unsupported type '$typeName'; " +
                        "supported types: ${PrimitiveColumn.entries.joinToString(", ") { it.kotlinName }}",
                    param,
                )
                return null
            }
            fields += FieldModel(fieldName, typeName)
        }
        if (fields.isEmpty()) {
            logger.error("@ValueSchema class $name must declare at least one field", decl)
            return null
        }
        return SchemaModel(decl.packageName.asString(), name, fields, extractTransforms(decl))
    }

    private fun extractTransforms(decl: KSClassDeclaration): List<TransformModel> {
        val annotation = decl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == VALUE_SCHEMA_ANNOTATION
        } ?: return emptyList()
        val values = annotation.arguments.firstOrNull { it.name?.asString() == "transforms" }?.value
        return (values as? List<*>)?.filterIsInstance<KSAnnotation>()?.map { transform ->
            val args = transform.arguments.associate { it.name?.asString() to it.value }
            TransformModel(
                name = args["name"] as? String ?: "",
                params = args["params"] as? String ?: "",
                body = args["body"] as? String ?: "",
            )
        } ?: emptyList()
    }

    private fun write(decl: KSClassDeclaration, model: SchemaModel) {
        val file = ValueSchemaGenerator.generate(model)
        val dependencies = decl.containingFile?.let { Dependencies(aggregating = false, it) }
            ?: Dependencies(aggregating = false)
        codeGenerator.createNewFile(dependencies, model.packageName, "${model.className}Schema")
            .bufferedWriter()
            .use { file.writeTo(it) }
    }

    companion object {
        private const val VALUE_SCHEMA_ANNOTATION = "io.github.copi143.valueschema.ValueSchema"
    }
}
