package io.github.copi143.valueschema.processor

import io.github.copi143.valueschema.generator.FieldModel
import io.github.copi143.valueschema.generator.NestedModel
import io.github.copi143.valueschema.generator.PrimitiveColumn
import io.github.copi143.valueschema.generator.SchemaField
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
import com.squareup.kotlinpoet.ClassName

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
        val fields = extractFields(decl, setOfNotNull(decl.qualifiedName?.asString())) ?: return null
        if (fields.isEmpty()) {
            logger.error("@ValueSchema class $name must declare at least one field", decl)
            return null
        }
        return SchemaModel(decl.packageName.asString(), name, fields, extractTransforms(decl))
    }

    /**
     * Resolves constructor fields into the schema field tree. A field whose type is another
     * @ValueSchema class becomes a [NestedModel] and is flattened into the parent storage,
     * mirroring how Valhalla flattens nested value-class fields (no indirection, no allocation).
     */
    private fun extractFields(decl: KSClassDeclaration, path: Set<String>): List<SchemaField>? {
        val name = decl.simpleName.asString()
        val ctor = decl.primaryConstructor ?: return emptyList()
        val fields = mutableListOf<SchemaField>()
        for (param in ctor.parameters) {
            val fieldName = param.name?.asString() ?: continue
            if (!param.isVal) {
                logger.error("@ValueSchema class $name field '$fieldName' must be an immutable 'val'", param)
                return null
            }
            val type = param.type.resolve()
            val typeDecl = type.declaration
            val typeName = typeDecl.qualifiedName?.asString()?.removePrefix("kotlin.")
            if (typeName != null && PrimitiveColumn.ofKotlinName(typeName) != null) {
                fields += FieldModel(fieldName, typeName)
                continue
            }
            if (typeDecl is KSClassDeclaration && typeDecl.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == VALUE_SCHEMA_ANNOTATION
                }
            ) {
                if (type.isMarkedNullable) {
                    logger.error("@ValueSchema class $name field '$fieldName': nested value type must be non-nullable", param)
                    return null
                }
                if (type.arguments.isNotEmpty()) {
                    logger.error("@ValueSchema class $name field '$fieldName': generic nested value types are not supported", param)
                    return null
                }
                val qualified = typeDecl.qualifiedName?.asString() ?: continue
                if (qualified in path) {
                    logger.error("@ValueSchema class $name field '$fieldName': recursive value types are impossible (value types have no indirection)", param)
                    return null
                }
                val children = extractFields(typeDecl, path + qualified) ?: return null
                fields += NestedModel(fieldName, ClassName.bestGuess(qualified), children)
                continue
            }
            logger.error(
                "@ValueSchema class $name field '$fieldName' has unsupported type '$typeName'; " +
                    "supported: primitives (${PrimitiveColumn.entries.joinToString(", ") { it.kotlinName }}) " +
                    "or another @ValueSchema class",
                param,
            )
            return null
        }
        return fields
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
