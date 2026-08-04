package allyouneed.mixin.ae2;

import allyouneed.util.bigint.BigCpuStorage;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ensure CPU list entries see saturated / unbounded capacity from {@link BigCpuStorage}.
 */
@Mixin(value = CraftingStatusMenu.class, remap = false)
public class CraftingStatusMenuMixin {

    @Redirect(method = "createCpuList", at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingCPU;getAvailableStorage()J"))
    private long allyouneed$cpuStorage(ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster cluster) {
            return BigCpuStorage.getClusterStorageLong(cluster);
        }
        return cpu.getAvailableStorage();
    }
}
