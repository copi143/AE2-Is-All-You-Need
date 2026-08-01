package allyouneed.machine

import net.minecraft.world.item.ItemStack

object MachineTypeRegistry {
    private val typesById = HashMap<String, MachineType>()

    fun register(type: MachineType) {
        typesById[type.id] = type
    }

    fun byId(id: String): MachineType? = typesById[id]

    fun byItem(stack: ItemStack): MachineType? =
        typesById.values.firstOrNull { it.accepts(stack) }

    fun getAll(): List<MachineType> = typesById.values.toList()

    fun indexById(id: String): Int = typesById.keys.indexOf(id)
}
