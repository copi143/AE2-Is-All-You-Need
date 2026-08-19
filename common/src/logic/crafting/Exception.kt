package allyouneed.logic.crafting

sealed class CraftingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class LooksLikeDosAttack(message: String, cause: Throwable? = null) : CraftingException(message, cause)
