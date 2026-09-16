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
            val file = cls.containingFile ?: continue
            val model = toModel(cls, serialized) ?: continue
            if (!generated.add("${model.pkg}.${model.name}")) continue
            env.codeGenerator.createNewFile(
                Dependencies(false, file),
                model.pkg,
                "${model.name}Serdes",
            ).use { it.write(emit(model).toByteArray()) }
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
            val ordinal = prop.hasAnno("SerialOrdinal")
            val name = prop.simpleName.asString()
            SerialProp(
                name = name,
                getter = name,
                setter = if (prop.isMutable) name else null,
                wireName = prop.annoArg("SerialName") ?: name,
                type = ty(type, ordinal, serialized, prop.hasAnno("SerialVarLen")),
                nullable = type.isMarkedNullable || prop.hasAnno("SerialNullable"),
                varLen = prop.hasAnno("SerialVarLen"),
            )
        }.toList()
        if (fields.isEmpty()) return null
        val construct = fields.isNotEmpty() && fields.all { it.name in ctorNames }
        return SerialClass(cls.packageName.asString(), cls.simpleName.asString(), fields, construct)
    }

    private fun ty(type: KSType, ordinal: Boolean, serialized: Set<String>, varLen: Boolean): SerialTy {
        val qn = type.declaration.qualifiedName?.asString() ?: return SerialTy.Str
        if (qn == "kotlin.collections.List" || qn == "kotlin.collections.MutableList" || qn == "java.util.List") {
            val arg = type.arguments.firstOrNull()?.type?.resolve() ?: return SerialTy.ListOf(SerialTy.Str)
            return SerialTy.ListOf(ty(arg, ordinal, serialized, varLen))
        }
        return when (qn) {
            "kotlin.Boolean", "java.lang.Boolean" -> SerialTy.Bool
            "kotlin.Byte", "java.lang.Byte" -> SerialTy.I8
            "kotlin.Short", "java.lang.Short" -> SerialTy.I16
            "kotlin.Int", "java.lang.Integer" -> if (varLen) SerialTy.VarInt else SerialTy.I32
            "kotlin.Long", "java.lang.Long" -> if (varLen) SerialTy.VarLong else SerialTy.I64
            "kotlin.Float", "java.lang.Float" -> SerialTy.F32
            "kotlin.Double", "java.lang.Double" -> SerialTy.F64
            "kotlin.String", "java.lang.String" -> SerialTy.Str
            "kotlin.ByteArray" -> SerialTy.Bytes
            "kotlin.IntArray" -> SerialTy.Ints
            "kotlin.LongArray" -> SerialTy.Longs
            "java.math.BigInteger" -> if (varLen) SerialTy.VarBigInt else SerialTy.BigInt
            "java.util.UUID" -> SerialTy.Uuid
            "net.minecraft.resources.ResourceLocation" -> SerialTy.ResLoc
            "net.minecraft.core.BlockPos" -> SerialTy.BlockPos
            else -> {
                val decl = type.declaration as? KSClassDeclaration
                val simple = type.declaration.simpleName.asString()
                when {
                    decl?.classKind == ClassKind.ENUM_CLASS -> SerialTy.Enum(simple, ordinal)
                    simple in serialized || decl?.hasAnno("Serialize") == true -> SerialTy.Nested(simple)
                    ordinal -> SerialTy.Enum(simple, true)
                    else -> SerialTy.Enum(simple, false)
                }
            }
        }
    }

    companion object {
        private const val SERIALIZE = "io.github.copi143.serialization.Serialize"
    }
}

private fun KSAnnotated.hasAnno(short: String) = annotations.any { it.shortName.asString() == short }

private fun KSAnnotated.annoArg(short: String): String? {
    val a: KSAnnotation = annotations.firstOrNull { it.shortName.asString() == short } ?: return null
    return a.arguments.firstOrNull()?.value as? String
}
