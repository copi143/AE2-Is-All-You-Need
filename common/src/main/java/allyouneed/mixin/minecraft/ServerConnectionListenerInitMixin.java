package allyouneed.mixin.minecraft;

import allyouneed.net.http.ProtocolDetector;
import allyouneed.net.http.SharedHttp;
import io.netty.channel.Channel;
import net.minecraft.server.network.LegacyQueryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerConnectionListenerInitMixin {
    @Inject(method = "initChannel(Lio/netty/channel/Channel;)V", at = @At("TAIL"))
    private void allyouneed$installHttpDetector(Channel channel, CallbackInfo ci) {
        if (!SharedHttp.enabled()) {
            return;
        }
        LegacyQueryHandler query = (LegacyQueryHandler) channel.pipeline().get("legacy_query");
        if (query == null) {
            return;
        }
        channel.pipeline().addFirst(
            SharedHttp.DETECTOR,
            new ProtocolDetector(((LegacyQueryHandlerAccessor) query).getServerConnectionListener())
        );
    }
}
