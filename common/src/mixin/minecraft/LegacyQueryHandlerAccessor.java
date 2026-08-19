package allyouneed.mixin.minecraft;

import net.minecraft.server.network.LegacyQueryHandler;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LegacyQueryHandler.class)
public interface LegacyQueryHandlerAccessor {
    @Accessor("serverConnectionListener")
    ServerConnectionListener getServerConnectionListener();
}
