package io.github.amelonrind.storagebrowser;

import io.github.amelonrind.storagebrowser.access.IMixinHandledScreen;
import io.github.amelonrind.storagebrowser.api.Position;
import io.github.amelonrind.storagebrowser.api.Size;
import io.github.amelonrind.storagebrowser.api.StorageBrowserAPI;
import io.github.amelonrind.storagebrowser.data.DataManager;
import io.github.amelonrind.storagebrowser.data.key.ChestPos;
import io.github.amelonrind.storagebrowser.screen.NameAssignmentScreen;
import io.github.amelonrind.storagebrowser.screen.StorageBrowseScreen;
import io.github.amelonrind.storagebrowser.util.Texts;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class StorageBrowser {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final String MOD_ID = "storage-browser";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Contract(pure = true)
    public static @NotNull String translateKey(@NotNull String path) {
        return MOD_ID + "." + path;
    }

    @Contract("_ -> new")
    public static @NotNull MutableText translate(@NotNull String path) {
        return Text.translatable(translateKey(path));
    }

    private static @Nullable DataManager profile = null;
    private static boolean inInv = false;
    private static Screen currentScreen = null;
    private static ChestPos p1;
    private static ChestPos p2;
    public static @NotNull Situation situation = Situation.LOADING;
    public static List<Text> tooltip = situation.getTooltip();

    public static @Nullable DataManager getCurrentProfile() {
        return profile;
    }

    public static void onPair(HandledScreen<?> screen, Inventory inv, @Nullable String type, @Nullable BlockPos pos1, @Nullable BlockPos pos2) {
        if (mc.world == null) return;
        if (profile != null) profile.updateDimension();
        inInv = false;
        currentScreen = screen;
        p1 = ChestPos.fromBlockPos(pos1);
        p2 = ChestPos.fromBlockPos(pos2);
        if (p1 == null) situation = Situation.UNKNOWN;
        else {
            if (p2 == null) handleSingle(screen, inv, type, pos1, p1);
            else handleDouble(screen, inv, type, pos1, pos2, p1, p2);
            if ((situation == Situation.CAN_ADD || situation == Situation.PARTIAL_ADDED)
                    && DataManager.globalSettings.get("autoAddContainer", false)
            ) situation = Situation.ADDED;
        }
        setStatus();
    }

    private static void handleSingle(HandledScreen<?> screen, Inventory inv, String type, BlockPos bpos, ChestPos pos) {
        assert mc.world != null;
        Block block = mc.world.getBlockState(bpos).getBlock();
        if (profile == null) situation = Situation.UNKNOWN;
        else if (profile.getChestData(pos) != null) situation = Situation.ADDED;
        else if (profile.isIgnored(pos)) situation = Situation.IGNORED;
        else situation = Situation.CAN_ADD;
        if (profile != null) ((IMixinHandledScreen) screen).storagebrowser_setOnClose(() -> {
//              await Threads.escapeThread();
            if (profile == null) return;
            if (situation == Situation.IGNORED) profile.setIgnored(pos, true);
            if (situation != Situation.ADDED || mc.world.getBlockState(bpos).getBlock() != block) return;
            profile.setIgnored(pos, false);
            profile.setChestData(pos, type, inv);
        });
    }

    private static void handleDouble(HandledScreen<?> screen, Inventory inv, String type, BlockPos bpos1, BlockPos bpos2, ChestPos pos1, ChestPos pos2) {
        assert mc.world != null;
        Block block1 = mc.world.getBlockState(bpos1).getBlock();
        Block block2 = mc.world.getBlockState(bpos2).getBlock();
        boolean added1;
        if (profile == null) {
            added1 = false;
            situation = Situation.UNKNOWN;
        } else {
            added1 = profile.getChestData(pos1) != null;
            boolean added2 = profile.getChestData(pos2) != null;
            if (added1 || added2) {
                if (added1 && added2) situation = Situation.ADDED;
                else situation = Situation.PARTIAL_ADDED;
            } else {
                boolean ignored1 = profile.isIgnored(pos1);
                boolean ignored2 = profile.isIgnored(pos2);
                if (ignored1 || ignored2) {
                    if (ignored1 && ignored2) situation = Situation.IGNORED;
                    else situation = Situation.PARTIAL_IGNORE;
                } else situation = Situation.CAN_ADD;
            }
        }
        if (profile != null) ((IMixinHandledScreen) screen).storagebrowser_setOnClose(() -> {
//            await Threads.escapeThread();
            if (profile == null) return;
            if (situation == Situation.IGNORED) {
                profile.setIgnored(pos1, true);
                profile.setIgnored(pos2, true);
            }
            if ((situation != Situation.ADDED
                    && situation != Situation.PARTIAL_ADDED)
                    || mc.world.getBlockState(bpos1).getBlock() != block1
                    || mc.world.getBlockState(bpos2).getBlock() != block2
            ) return;
            if (situation == Situation.ADDED) {
                profile.setIgnored(pos1, false);
                profile.setIgnored(pos2, false);
                profile.setChestData(pos1, type, inv, 0);
                profile.setChestData(pos2, type, inv, 27);
            } else {
                if (added1) {
                    profile.setIgnored(pos1, false);
                    profile.setChestData(pos1, type, inv, 0);
                } else {
                    profile.setIgnored(pos2, false);
                    profile.setChestData(pos2, type, inv, 27);
                }
            }
        });
    }

    public static void onOpenInventory(HandledScreen<?> screen) {
        if (profile == null) profile = DataManager.getCurrentProfile();
        else profile.updateDimension();
        inInv = true;
        currentScreen = screen;
        setStatus();
    }

    public static void onDimensionChange(ClientWorld world) {
        profile = world == null ? null : DataManager.getCurrentProfile();
        inInv = false;
        currentScreen = null;
    }

    public static void setStatus() {
        setStatus(null);
    }

    public static void setStatus(@Nullable Situation situation_) {
        if (situation_ != null) situation = situation_;
        if (currentScreen == null) return;
        if (inInv) {
            if (profile == null) {
                situation = Situation.NO_DATA;
                tooltip = situation.getTooltip();
            } else {
                situation = Situation.OPEN_BROWSER;
                tooltip = situation.getTooltip();
                tooltip.add(Texts.EMPTY);
                tooltip.add(Text.literal("§6Storage: " + profile.profileName));
                tooltip.add(Text.literal("§7Path: " + profile.getCurrentPathShort()));
            }
        } else {
            if (profile == null) {
                situation = Situation.NO_DATA;
                tooltip = situation.getTooltip();
            } else {
                tooltip = situation.getTooltip();
                tooltip.add(Texts.EMPTY);
                tooltip.add(Text.literal("§6Storage: " + profile.profileName));
                if (situation != Situation.UNKNOWN) {
                    if (p1 != null) tooltip.add(Text.literal(p2 == null ? "§7Pos: " : "§7Pos1: " + p1));
                    if (p2 != null) tooltip.add(Text.literal("§7Pos2: " + p2));
                }
            }
        }
    }

    public static class Icon extends ClickableWidget {
        private static final MinecraftClient mc = MinecraftClient.getInstance();
        private final int rows;
        private final Size screenSize;

        public Icon(Screen screen, int rows) {
            super(0, 0, 20, 20, null);
            this.rows = rows;
            screenSize = Size.fromScreen(screen);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            switch (situation) {
                case IGNORED, PARTIAL_ADDED, CAN_ADD -> setStatus(Situation.ADDED);
                case ADDED, PARTIAL_IGNORE -> setStatus(Situation.IGNORED);
                case OPEN_BROWSER -> {
                    if (profile != null) StorageBrowseScreen.open(currentScreen, profile);
                }
                case NO_DATA -> {
                    situation = Situation.UNKNOWN;
                    if ((profile = DataManager.getCurrentProfile()) == null) {
                        NameAssignmentScreen.open(currentScreen, DataManager.getCurrentWorldId(), () -> {
                                profile = DataManager.getCurrentProfile();
                                setStatus();
                        });
                    }
                    setStatus();
                }
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (DataManager.globalSettings.get("hideStatusIfLoading", false)) {
                visible = situation != Situation.LOADING;
            } else visible = true;
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        protected void renderButton(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
            Position pos = StorageBrowserAPI.getStatusPosition(screenSize, rows).setPosToWidget(this);
            int x = (int) pos.x + 2;
            int y = (int) pos.y + 2;

            context.drawItem((inInv ? Items.BARREL : Items.CHEST).getDefaultStack(), x, y);

            context.getMatrices().push();
            context.getMatrices().translate(0.0, 0.0, 200.0);
            x += 8 - mc.textRenderer.getWidth(situation.sym) / 2;
            y += 9 - mc.textRenderer.fontHeight / 2;
            context.drawText(mc.textRenderer, situation.sym, x, y, 0xffffff, true);
            context.getMatrices().pop();

            if (isMouseOver(mouseX, mouseY)) context.drawTooltip(mc.textRenderer, tooltip, mouseX, mouseY);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }

    }

    public enum Situation {
        LOADING(Symbol.LOADING),
        NO_DATA(Symbol.INVALID),
        UNKNOWN(Symbol.INVALID),
        IGNORED(Symbol.IGNORED),
        PARTIAL_IGNORE(Symbol.PARTIAL),
        PARTIAL_ADDED(Symbol.PARTIAL),
        CAN_ADD(Symbol.CAN_ADD),
        ADDED(Symbol.ADDED),
        OPEN_BROWSER(Symbol.STORAGE);

        public final Text sym;

        private Language langCache;
        private String tooltipKey;
        private List<Text> tooltips;

        Situation(Text sym) {
            this.sym = sym;
        }

        @Contract(" -> new")
        public @NotNull ArrayList<Text> getTooltip() {
            Language lang = Language.getInstance();
            if (lang != langCache) {
                langCache = lang;
                if (tooltipKey == null) tooltipKey = translateKey("status.description." + toString().toLowerCase());
                tooltips = Texts.translateMultiLine(tooltipKey);
            }
            return new ArrayList<>(tooltips);
        }

    }

    private static class Symbol {
        public static final Text STORAGE = Text.literal("§6S");
        public static final Text LOADING = Text.literal("§7...");
        public static final Text INVALID = Text.literal("§7-");
        public static final Text IGNORED = Text.literal("§c-");
        public static final Text PARTIAL = Text.literal("§6/");
        public static final Text CAN_ADD = Text.literal("§a+");
        public static final Text ADDED   = Text.literal("§a✔");
    }

}
