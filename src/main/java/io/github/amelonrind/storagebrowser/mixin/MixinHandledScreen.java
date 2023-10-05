package io.github.amelonrind.storagebrowser.mixin;

import io.github.amelonrind.storagebrowser.StorageBrowser;
import io.github.amelonrind.storagebrowser.access.IMixinHandledScreen;
import io.github.amelonrind.storagebrowser.listener.InvPosPair;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen implements IMixinHandledScreen {
    @Unique private static Screen last;
    @Unique private Runnable onClose = null;

    @Inject(at = @At("TAIL"), method = "init()V")
    protected void init(CallbackInfo ci) {
        if ((Object) this instanceof HandledScreen<?> hs) {
            if (hs instanceof GenericContainerScreen gcs) {
                addIcon(hs, gcs.getScreenHandler().getRows());
                InvPosPair.onOpenScreen(hs, gcs.getScreenHandler().getInventory(), "generic");
            } else if (hs instanceof ShulkerBoxScreen sbs) {
                addIcon(hs, 3);
                InvPosPair.onOpenScreen(hs, ((MixinShulkerBoxScreenHandler) sbs.getScreenHandler()).getInventory(), "shulker");
            } else if (hs instanceof InventoryScreen) {
                addIcon(hs, -1);
                StorageBrowser.onOpenInventory(hs);
            } else if (hs instanceof CreativeInventoryScreen) {
                addIcon(hs, -2);
                StorageBrowser.onOpenInventory(hs);
            }
        } else last = null;
    }

    @Unique
    private void addIcon(Screen screen, int rows) {
        if (last != screen) {
            StorageBrowser.setStatus(StorageBrowser.Situation.LOADING);
            last = screen;
        }
        ((MixinScreen) screen).callAddDrawableChild(new StorageBrowser.Icon(screen, rows));
    }

    @Inject(at = @At("TAIL"), method = "close")
    public void onClose(CallbackInfo ci) {
        if (onClose != null) {
            onClose.run();
            onClose = null;
        }
    }

    public void storagebrowser_setOnClose(Runnable run) {
        onClose = run;
    }

}
