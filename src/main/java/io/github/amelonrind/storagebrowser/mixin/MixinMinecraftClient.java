package io.github.amelonrind.storagebrowser.mixin;

import io.github.amelonrind.storagebrowser.StorageBrowser;
import io.github.amelonrind.storagebrowser.data.DataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    @Shadow private Profiler profiler;

    @Inject(at = @At("TAIL"), method = "tick")
    public void tick(CallbackInfo ci) {
        profiler.push("storage-browser");
        DataManager.onTick();
        profiler.pop();
    }

    @Inject(at = @At("TAIL"), method = "joinWorld")
    public void onJoinWorld(ClientWorld world, CallbackInfo ci) {
        StorageBrowser.onDimensionChange(world);
    }

}
