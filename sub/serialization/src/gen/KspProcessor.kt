package io.github.copi143.serialization.gen

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate

class SerdesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = SerdesProcessor(environment)
}

class SerdesProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {
    private val generated = HashSet<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver.getSymbolsWithAnnotation(SERIALIZE).toList()
        val deferred = annotated.filter { !it.validate() }
        val classes = LinkedHashMap<String, KSClassDeclaration>()
        for (sym in annotated) {
            when (sym) {
                is KSClassDeclaration -> classes[sym.qualifiedName?.asString() ?: continue] = sym
                is KSPropertyDeclaration -> {
                    val parent = sym.parentDeclaration as? KSClassDeclaration ?: continue
                    classes[parent.qualifiedName?.asString() ?: continue] = parent
                }
            }
        }
        val serialized = classes.values.map { it.simpleName.asString() }.toSet()
        for (cls in classes.values) {
            if (cls.classKind == ClassKind.ENUM_CLASS) continue
            val file = cls.containingFile ?: continue
            val model = toModel(cls, serialized) ?: continue
            if (!generated.add("${model.pkg}.${model.name}")) continue
            env.codeGenerator.createNewFile(
                Dependencies(false, file),
                model.pkg,
                "${model.name}Serdes",
            ).use { it.write(emit(model).toString().toByteArray()) }
        }
        return deferred
    }

    private fun toModel(cls: KSClassDeclaration, serialized: Set<String>): SerialClass? {
        val classMarked = cls.hasAnno("Serialize")
        val ctorNames = cls.primaryConstructor?.parameters?.mapNotNull { it.name?.asString() }?.toSet() ?: emptySet()
        val fields = cls.declarations.filterIsInstance<KSPropertyDeclaration>().mapNotNull { prop ->
            if (!prop.hasBackingField || Modifier.JAVA_STATIC in prop.modifiers) return@mapNotNull null
            if (prop.hasAnno("SerialIgnore")) return@mapNotNull null
            if (!classMarked && !prop.hasAnno("Serialize")) return@mapNotNull null
            val type = prop.type.resolve()
            val fieldOrdinal: Boolean? = when {
                prop.hasAnno("SerialOrdinal") -> true
                prop.hasAnno("SerialByName") -> false
                else -> null
            }
            val name = prop.simpleName.asString()
            SerialProp(
                name = name,
                getter = name,
                setter = if (prop.isMutable) name else null,
                wireName = prop.annoArg("SerialName") ?: name,
                type = ty(type, fieldOrdinal, serialized, prop.hasAnno("SerialVarLen")),
                nullable = type.isMarkedNullable || prop.hasAnno("SerialNullable"),
                varLen = prop.hasAnno("SerialVarLen"),
            )
        }.toList()
        if (fields.isEmpty()) return null
        val construct = fields.all { it.name in ctorNames }
        return SerialClass(cls.packageName.asString(), cls.simpleName.asString(), fields, construct)
    }

    private fun ty(type: KSType, fieldOrdinal: Boolean?, serialized: Set<String>, varLen: Boolean): SerialType {
        val qn = type.declaration.qualifiedName?.asString() ?: return SerialType.Str
        if (qn == "kotlin.collections.List" || qn == "kotlin.collections.MutableList" || qn == "java.util.List") {
            val arg = type.arguments.firstOrNull()?.type?.resolve() ?: return SerialType.ListOf(SerialType.Str)
            return SerialType.ListOf(ty(arg, fieldOrdinal, serialized, false))
        }
        if (qn == "kotlin.collections.Map" || qn == "kotlin.collections.MutableMap" || qn == "java.util.Map") {
            val k = type.arguments.getOrNull(0)?.type?.resolve() ?: return SerialType.MapOf(SerialType.Str, SerialType.Str)
            val v = type.arguments.getOrNull(1)?.type?.resolve() ?: return SerialType.MapOf(SerialType.Str, SerialType.Str)
            return SerialType.MapOf(ty(k, fieldOrdinal, serialized, false), ty(v, fieldOrdinal, serialized, false))
        }
        if (qn == "kotlin.collections.Set" || qn == "kotlin.collections.MutableSet" || qn == "java.util.Set") {
            val arg = type.arguments.firstOrNull()?.type?.resolve() ?: return SerialType.SetOf(SerialType.Str)
            return SerialType.SetOf(ty(arg, fieldOrdinal, serialized, false))
        }
        SerialType.primitive(qn, varLen)?.let { return it }
        val decl = type.declaration as? KSClassDeclaration
        val simple = type.declaration.simpleName.asString()
        return when {
            decl?.classKind == ClassKind.ENUM_CLASS ->
                SerialType.Enum(simple, fieldOrdinal ?: decl.serializeOrdinalDefault())
            simple in serialized || decl?.hasAnno("Serialize") == true -> SerialType.Nested(simple)
            fieldOrdinal == true -> SerialType.Enum(simple, true)
            else -> SerialType.Enum(simple, false)
        }
    }

    companion object {
        private const val SERIALIZE = "io.github.copi143.serialization.Serialize"
    }
}

private fun KSAnnotated.hasAnno(short: String) = annotations.any { it.shortName.asString() == short }

private fun KSClassDeclaration.serializeOrdinalDefault(): Boolean {
    val a = annotations.firstOrNull { it.shortName.asString() == "Serialize" } ?: return false
    return (a.arguments.firstOrNull { it.name?.asString() == "ordinal" }?.value as? Boolean) ?: false
}

private fun KSAnnotated.annoArg(short: String): String? {
    val a: KSAnnotation = annotations.firstOrNull { it.shortName.asString() == short } ?: return null
    return a.arguments.firstOrNull()?.value as? String
}
