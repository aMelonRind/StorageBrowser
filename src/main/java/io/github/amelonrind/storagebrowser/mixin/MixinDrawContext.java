package io.github.amelonrind.storagebrowser.mixin;

import io.github.amelonrind.storagebrowser.screen.StorageBrowseScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(DrawContext.class)
public abstract class MixinDrawContext {

    @ModifyArg(method = "drawItemTooltip", index = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V"))
    public List<Text> appendExtraTooltip(List<Text> text) {
        if (StorageBrowseScreen.extraTooltip != null) {
            text.addAll(StorageBrowseScreen.extraTooltip);
            StorageBrowseScreen.extraTooltip = null;
        }
        return text;
    }

}
