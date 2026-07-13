package allyouneed.api.machine

import net.minecraft.resources.ResourceLocation

object MachineTypeRegistry {
    private val byId = mutableMapOf<ResourceLocation, MachineType>()

    fun register(machineType: MachineType): MachineType {
        if (byId.containsKey(machineType.id)) {
            throw IllegalArgumentException("MachineType already registered: ${machineType.id}")
        }
        byId[machineType.id] = machineType
        return machineType
    }

    fun get(id: ResourceLocation): MachineType? = byId[id]

    fun getAll(): List<MachineType> = byId.values.toList()

    fun clear() {
        byId.clear()
    }
}
