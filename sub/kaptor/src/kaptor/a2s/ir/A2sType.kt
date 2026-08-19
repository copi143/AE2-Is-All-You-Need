package kaptor.a2s.ir

import kaptor.a2s.resource.KeyTypeRef

/**
 * a2s 类型系统。
 *
 * - 标量：I32/I64/U32/U64/F32/F64/Boolean/String
 * - 大数：BigInt（默认整数）、Rational（默认小数）
 * - 资源：ResourceType（动态，来自 AE2 注册表）
 * - 容器：Stack、List<T>
 * - 特殊：Any/Null/Unit
 * - 可空：通过 [A2sNullableType] 包装
 */
sealed interface A2sType {
    /** 该类型的 JVM 描述符（用于字节码生成）。 */
    val descriptor: String
}

data object A2sI32 : A2sType { override val descriptor = "I" }
data object A2sI64 : A2sType { override val descriptor = "J" }
data object A2sU32 : A2sType { override val descriptor = "I" }
data object A2sU64 : A2sType { override val descriptor = "J" }
data object A2sF32 : A2sType { override val descriptor = "F" }
data object A2sF64 : A2sType { override val descriptor = "D" }
data object A2sBoolean : A2sType { override val descriptor = "Z" }
data object A2sString : A2sType { override val descriptor = "Ljava/lang/String;" }

data object A2sBigInt : A2sType { override val descriptor = "Ljava/math/BigInteger;" }
data object A2sRational : A2sType { override val descriptor = "Lkaptor/a2s/runtime/Rational;" }

data class A2sResourceType(val keyType: KeyTypeRef) : A2sType {
    override val descriptor = "Ljava/lang/Object;"
}

data object A2sStack : A2sType { override val descriptor = "Ljava/lang/Object;" }

data class A2sListType(val elementType: A2sType) : A2sType {
    override val descriptor = "Ljava/util/List;"
}

data object A2sAny : A2sType { override val descriptor = "Ljava/lang/Object;" }
data object A2sUnit : A2sType { override val descriptor = "V" }

/** 事件类型：引用脚本中 `event Xxx(...)` 声明的事件类。 */
data class A2sEventType(val eventName: String) : A2sType {
    override val descriptor = "Ljava/lang/Object;"
}

/** 可空类型包装：T? */
data class A2sNullableType(val inner: A2sType) : A2sType {
    override val descriptor = "Ljava/lang/Object;"
}

/** 未推断/未知类型，编译期兜底。 */
data object A2sUnknown : A2sType { override val descriptor = "Ljava/lang/Object;" }
