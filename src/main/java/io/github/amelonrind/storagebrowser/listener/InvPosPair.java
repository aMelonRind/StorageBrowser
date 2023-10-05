package io.github.amelonrind.storagebrowser.listener;

import io.github.amelonrind.storagebrowser.StorageBrowser;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class InvPosPair {
    private static @Nullable BlockPos clicked;
    private static @Nullable BlockPos otherHalf;
    private static String type;
    private static @Nullable String id;
    private static long time;

    public static void onClick(BlockPos pos, String type, String id) {
        onClick(pos, type, id, null);
    }

    public static void onClick(BlockPos pos, String type, String id, @Nullable BlockPos other) {
        long current = System.currentTimeMillis();
        if (current - time < 3000) {
            clicked = null;
            otherHalf = null;
        } else {
            clicked = pos;
            otherHalf = other;
            InvPosPair.type = type;
            InvPosPair.id = id;
        }
        time = current;
    }

    public static void onOpenScreen(HandledScreen<?> screen, Inventory inv, String type) {
        if (clicked == null || !type.equals(InvPosPair.type) || System.currentTimeMillis() - time > 3000) {
            on(screen, inv, null, null, null);
            return;
        }
        if (otherHalf != null) {
            ScreenHandler handler = screen.getScreenHandler();
            if (!(handler instanceof GenericContainerScreenHandler gcs) || gcs.getInventory().size() != 54) {
                otherHalf = null;
            }
        }
        on(screen, inv, id, clicked, otherHalf);
        id = null;
        clicked = otherHalf = null;
        time = 0;
    }

    private static void on(HandledScreen<?> screen, Inventory inv, @Nullable String id, @Nullable BlockPos pos1, @Nullable BlockPos pos2) {
        StorageBrowser.onPair(screen, inv, id, pos1, pos2);
    }

}
