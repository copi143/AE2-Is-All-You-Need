package kaptor.a2s.resource

/**
 * 资源解析器接口。kaptor 模块不依赖 AE2，由 common 模块提供实现并在运行时注入。
 *
 * 负责两件事：
 * 1. 解析脚本中的资源类型名（如 `Item`、`Energy`），映射到 AE2 的 [AEKeyType]。
 * 2. 解析反引号资源引用（如 `` `item|minecraft:diamond` ``），映射到具体的 key。
 */
interface ResourceResolver {
    /**
     * 解析资源类型名（大小写不敏感），返回抽象的 key 类型引用。
     *
     * 脚本中类型标注（`val x: Item`）与反引号前缀（`` `item/...` ``）均通过此方法解析。
     * 若类型名未注册，返回 null，由编译器报错「未知类型」。
     */
    fun resolveKeyType(name: String): KeyTypeRef?

    /**
     * 解析反引号资源引用，返回具体的资源。
     *
     * @param prefix 类型前缀（`` `item|...` `` 中的 `item`），可能为 null（裸名引用）
     * @param namespace 命名空间（`` `minecraft:diamond` `` 中的 `minecraft`），可能为 null
     * @param path 路径（`` `diamond` `` 中的 `diamond`）
     */
    fun resolve(prefix: String?, namespace: String?, path: String): ResolvedResource?
}

/**
 * 抽象的 key 类型引用。kaptor 不依赖 AE2 的 [AEKeyType]，用 opaque 接口隔离。
 */
interface KeyTypeRef {
    /** 规范化后的类型名，如 "Item"、"Fluid"。 */
    val name: String
}

/**
 * 解析后的资源。
 */
sealed interface ResolvedResource {
    /** 资源的规范标识（如 "minecraft:diamond"）。 */
    val key: String

    /** 仅 key，无数量。 */
    data class Key(override val key: String) : ResolvedResource

    /** key + 数量。 */
    data class Stack(override val key: String, val amount: Long) : ResolvedResource
}
