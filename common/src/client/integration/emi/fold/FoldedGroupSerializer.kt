package allyouneed.client.integration.emi.fold

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer

/** 收藏使用同一个分类树节点解析；EMI 重载期间保留成员快照，分类完成后惰性切回树。 */
class FoldedGroupSerializer : EmiIngredientSerializer<FoldedGroupIngredient> {
    override fun getType(): String = TYPE
    override fun serialize(stack: FoldedGroupIngredient): JsonElement = JsonObject().apply {
        addProperty("type", TYPE)
        addProperty("key", stack.groupKey)
        addProperty("label", stack.displayName)
        addProperty("amount", stack.amount)
        addProperty("chance", stack.chance)
        add("members", JsonArray().apply {
            stack.members.forEach { member -> EmiIngredientSerializer.getSerialized(member)?.let(::add) }
        })
    }

    override fun deserialize(element: JsonElement): EmiIngredient = runCatching {
        val obj = element.asJsonObject
        val key = obj.get("key")?.asString ?: return EmiStack.EMPTY
        val members = EmiFoldGroups.resolve(key) ?: if (obj.has("members")) {
            obj.getAsJsonArray("members").flatMap { member ->
                runCatching { EmiIngredientSerializer.getDeserialized(member).emiStacks.filterNot { it.isEmpty } }.getOrDefault(emptyList())
            }
        } else {
            // 只为历史存档读取保留旧键规则；新的分组不再调用词法三阶段算法。
            EmiApi.getIndexStacks().filter { stack ->
                val id = stack.id
                EmiFoldCluster.compositeKeys(id.namespace, id.path, EmiFoldGroups.classOf(stack)).any {
                    it == key || EmiFoldCluster.baseKey(it) == key
                }
            }
        }
        if (members.isEmpty()) return EmiStack.EMPTY
        FoldedGroupIngredient(key, members, obj.get("amount")?.asLong ?: 1,
            obj.get("chance")?.asFloat ?: 1f, true, obj.get("label")?.asString)
    }.getOrDefault(EmiStack.EMPTY)

    companion object { const val TYPE = "ae2isallyouneed:fold" }
}
