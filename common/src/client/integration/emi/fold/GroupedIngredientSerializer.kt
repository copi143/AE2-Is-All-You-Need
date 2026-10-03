package allyouneed.client.integration.emi.fold

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer

/**
 * [GroupedIngredient] 的 EMI 序列化，用于收藏夹/历史记录持久化。
 *
 * 内联成员收藏按实际单个成员保存，恢复为裸成员，不持久化界面的临时展开状态。
 */
class GroupedIngredientSerializer : EmiIngredientSerializer<GroupedIngredient> {
    override fun getType(): String = TYPE

    override fun serialize(stack: GroupedIngredient): JsonElement =
        JsonObject().apply {
            addProperty("type", TYPE)
            addProperty("key", stack.groupKey)
            val memberEl = EmiIngredientSerializer.getSerialized(stack.emiStacks.single())
            if (memberEl != null) add("member", memberEl)
        }

    override fun deserialize(element: JsonElement): EmiIngredient {
        val obj = runCatching { element.asJsonObject }.getOrNull()
            ?: return EmiStack.EMPTY
        val member = obj.get("member")?.let { memberEl ->
            val deserialized = EmiIngredientSerializer.getDeserialized(memberEl)
            deserialized.emiStacks.firstOrNull()
        } ?: return EmiStack.EMPTY
        // 旧的“展开成员”收藏恢复为真实物品，不把旧组键带入新导航状态。
        return member
    }

    companion object {
        const val TYPE = "ae2isallyouneed:fold_member"
    }
}
