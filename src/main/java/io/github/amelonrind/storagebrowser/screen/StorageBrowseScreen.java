package io.github.amelonrind.storagebrowser.screen;

import com.google.gson.*;
import io.github.amelonrind.storagebrowser.StorageBrowser;
import io.github.amelonrind.storagebrowser.api.PositionAndSize;
import io.github.amelonrind.storagebrowser.api.Size;
import io.github.amelonrind.storagebrowser.api.StorageBrowserAPI;
import io.github.amelonrind.storagebrowser.data.DataManager;
import io.github.amelonrind.storagebrowser.data.ItemData;
import io.github.amelonrind.storagebrowser.data.key.ChunkPos;
import io.github.amelonrind.storagebrowser.screen.element.SearchBar;
import io.github.amelonrind.storagebrowser.util.Texts;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static io.github.amelonrind.storagebrowser.StorageBrowser.LOGGER;

import static io.github.amelonrind.storagebrowser.api.StorageBrowserAPI.Browser.filterer;
import static io.github.amelonrind.storagebrowser.api.StorageBrowserAPI.Browser.onClickItem;
import static io.github.amelonrind.storagebrowser.api.StorageBrowserAPI.Browser.tooltipFunction;
import static io.github.amelonrind.storagebrowser.api.StorageBrowserAPI.Browser.extraTooltipFunction;

public class StorageBrowseScreen extends Screen {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Text TITLE = StorageBrowser.translate("browser.title");
    private static final Text NO_ITEM_TEXT = StorageBrowser.translate("browser.no-item");
    private static final Text LOADING_TEXT = StorageBrowser.translate("browser.loading");
    private static final Text SEARCHING_TEXT = StorageBrowser.translate("browser.searching");
    private static final Text NOT_ENOUGH_SPACE_TEXT = StorageBrowser.translate("browser.no-space");

    public static List<Text> extraTooltip = null;

    private @Nullable Screen parent;
    public final DataManager profile;
    private SearchBar searchBar = null;
    public String loadingLabel = "";
    public double loadProgress = 0.0;
    public final ArrayList<ItemData> loadedItems = new ArrayList<>();
    private final ArrayList<ItemData> displayedItems = new ArrayList<>();
    public boolean sortReversed = false;
    private boolean isDraggingScrollBar = false;
    private boolean isDraggingScroll = false;
    private boolean dirty = false;
    private boolean destroyed = false;
    private double scrolled = 0.0;
    private final int[] clickingItem = new int[] {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1}; // I don't think any mouse has more than 10 keys but anyway

    private PositionAndSize searchBarVec = new PositionAndSize(0, 0, 0, 0);
    private int signX = 1;
    private int signY = 1;
    private int startX = 0;
    private int startY = 0;
    private int countX = 0;
    private int countY = 0;
    private int cy18 = 0;
    private int scrollBarX = -1;
    private int scrollBarSize = 0;
    private int totalRows = 0;
    private int flooredScrolled = 0;

    public static void open(Screen parent, @NotNull DataManager profile) {
        LOGGER.info(String.format("Opening browser %s %s", profile.profileName, profile.getCurrentPathShort()));
        DataManager.globalSettings.reload();
        profile.settings.reload();
        StorageBrowseScreen screen = new StorageBrowseScreen(profile);
        screen.setParent(parent);

//        boolean isCurrentWorld = mc.world != null && profile.profileName.equals(DataManager.getProfileIndex(DataManager.getCurrentWorldId()));

        screen.sortReversed = DataManager.globalSettings.get("sortReversed", false);

        ItemsLoader loader = new ItemsLoader(
                screen,
                profile,
                DataManager.globalSettings.get("unpackShulker", false),
                profile.getChunksInRenderDistance(),
                mc.player == null ? Vec3d.ZERO : mc.player.getPos()
        );

        new Thread(loader::load, "Storage Browser Items Loader").start();

        mc.setScreen(screen);
    }

    public StorageBrowseScreen(DataManager profile) {
        super(TITLE);
        this.profile = profile;
    }

    @Override
    protected void init() {
        super.init();
        if (searchBar == null) {
            searchBar = new SearchBar(textRenderer);
            searchBar.loadProgress = loadProgress;
        }
    }

    @Override
    public void tick() {
        super.tick();
        searchBar.tick();
    }

    @Override
    public void close() {
        mc.setScreen(parent);
        destroy();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setParent(null);
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusOn(searchBar.isFocused() ? null : searchBar);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E && !searchBar.isFocused()) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void setParent(@Nullable Screen parent) {
        this.parent = parent;
    }

    public void setLoadProgress(double progress) {
        if (searchBar != null) searchBar.loadProgress = progress;
        else loadProgress = progress;
    }

    public void markDirty() {
        dirty = true;
    }

    public void destroy() {
        destroyed = true;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void filterAndSort() {
        if (!dirty && !StorageBrowserAPI.Browser.wasMutatorChanged()) return;
        if (filterer != null) {
            displayedItems.clear();
            try {
                for (ItemData item : loadedItems) {
                    if (filterer.test(item)) displayedItems.add(item);
                }
            } catch (Throwable e) {
                StorageBrowserAPI.onCallbackError("filterer", e);
                filterer = null;
            }
        }
        if (filterer == null) {
            displayedItems.clear();
            displayedItems.addAll(loadedItems);
        }
        Comparator<ItemData> sortMethod = StorageBrowserAPI.Browser.getSortComparator();
        if (sortMethod != null) {
            try {
                synchronized (ItemData.infoSync) {
                    displayedItems.sort(sortMethod);
                }
            } catch (Throwable e) {
                StorageBrowserAPI.onCallbackError("sortMethod", e);
                StorageBrowserAPI.Browser.sortMethod = null;
            }
        }
        if (StorageBrowserAPI.Browser.sortMethod == null) {
            synchronized (ItemData.infoSync) {
                displayedItems.sort(StorageBrowserAPI.Browser::defaultSortMethod);
            }
        }
        if (sortReversed) Collections.reverse(displayedItems);
        isDraggingScroll = false;
        isDraggingScrollBar = false;
        Arrays.fill(clickingItem, -1);
        dirty = false;
    }

    private int getHoveredIndex(double mouseX, double mouseY) {
        return getHoveredIndex((int) Math.floor(mouseX), (int) Math.floor(mouseY));
    }

    private int getHoveredIndex(int mouseX, int mouseY) {
        if (countX == 0) return -1;
        int x = startX - 1;
        int y = startY - 1;
        if (signX == -1) x -= countX * 18 - 18;
        if (signY == -1) y -= countY * 18 - 18;
        if (x > mouseX || mouseX >= x + countX * 18
                ||  y > mouseY || mouseY >= y + countY * 18
        ) return -1;
        int posX = (mouseX - x) / 18;
        int posY = (mouseY - y) / 18;
        if (signX == -1) posX = countX - 1 - posX;
        if (signY == -1) posY = countY - 1 - posY;
        int index = (posY + flooredScrolled) * countX + posX;
        return index < displayedItems.size() ? index : -1;
    }

    @Override // button 01234: left right mid prev next
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) Math.floor(mouseX);
        int y = (int) Math.floor(mouseY);
        boolean searchFocused = false;
        boolean handled = false;
        int hovered = getHoveredIndex(x, y);
        if (hovered != -1) {
            isDraggingScroll = true;
            if (0 <= button && button < 10) {
                clickingItem[button] = hovered;
            }
            handled = true;
        } else {
            if (button == 0 && displayedItems.size() > countX * countY) {
                int sx = scrollBarX;
                if (signX == -1) sx -= 10;
                if (sx - 1 <= x && mouseX <= sx + 10) {
                    int sy = startY - 1;
                    if (signY == -1) sy -= cy18 - 18;
                    if (sy <= y && mouseY <= sy + cy18) {
                        isDraggingScrollBar = true;
                        double value = Math.max(0.0, Math.min(1.0, (mouseY - sy - scrollBarSize / 2.0) / (cy18 - scrollBarSize)));
                        if (signY == -1) value = 1.0 - value;
                        scrolled = value * (totalRows - countY);
                        handled = true;
                    }
                }
            }
            if (button == 0 || button == 1 || button == 2) {
                if (searchBarVec.contains(x, y)) {
                    searchFocused = true;
                    handled = true;
                }
            }
        }
        if (searchFocused || button == 0 || button == 1 || button == 2) {
            searchBar.setFocused(searchFocused);
            if (searchFocused) {
                focusOn(searchBar);
                searchBar.mouseClicked(mouseX, mouseY, button);
            } else if (getFocused() == searchBar) {
                focusOn(null);
                handled = true;
            }
        }
        if (handled) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) {
            if (isDraggingScrollBar) {
                int sy = startY - 1;
                if (signY == -1) sy -= cy18 - 18;
                double value = Math.max(0.0, Math.min(1.0, (mouseY - sy - scrollBarSize / 2.0) / (cy18 - scrollBarSize)));
                if (signY == -1) value = 1.0 - value;
                scrolled = value * (totalRows - countY);
            }
            if (isDraggingScroll) scrolled -= signY * deltaY / 18;
        }
        if (0 <= button && button < 10 && clickingItem[button] != -1
                && getHoveredIndex(mouseX, mouseY) != clickingItem[button]) clickingItem[button] = -1;
        if (isDraggingScrollBar || isDraggingScroll || clickingItem[button] != -1) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingScrollBar = false;
            isDraggingScroll = false;
        }
        if (0 <= button && button < 10 && clickingItem[button] != -1) {
            if (getHoveredIndex(mouseX, mouseY) == clickingItem[button]) {
                ItemData item = displayedItems.get(clickingItem[button]);
                if (onClickItem != null) {
                    try {
                        onClickItem.accept(item, button);
                    } catch (Throwable e) {
                        StorageBrowserAPI.onCallbackError("onClickItem", e);
                        onClickItem = null;
                    }
                }
                if (onClickItem == null) {
                    defaultOnClick(item, button);
                }
                return true;
            }
            clickingItem[button] = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void defaultOnClick(ItemData item, int button) {
        if (button == 2) item.setPinned(!item.isPinned());
        else if (button == 0) {
            if (mc.player != null) mc.player.sendMessage(
                    Text.translatable(
                            StorageBrowser.translateKey("browser.on-click"),
                            item.item.getName().copy().setStyle(
                                    Style.EMPTY.withHoverEvent(new HoverEvent(
                                            HoverEvent.Action.SHOW_ITEM,
                                            new HoverEvent.ItemStackContent(item.item)
                                    ))
                            ),
                            String.format("%d, %d, %d", item.getNearestPos().getX(), item.getNearestPos().getY(), item.getNearestPos().getZ()),
                            item.getNearestContainerType()
                    ).setStyle(
                            Style.EMPTY.withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    StorageBrowser.translate("browser.on-click.tooltip")
                            ))
                    )
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (Screen.hasShiftDown()) amount *= 4.0;
        if (Screen.hasControlDown()) amount *= 2.0;
        scrolled += -amount * signY;
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        if (destroyed) {
            close();
            return;
        }
        super.render(context, mouseX, mouseY, tickDelta);
        renderBackground(context);
        if (!loadingLabel.isBlank()) {
            context.drawTextWithShadow(textRenderer, loadingLabel, 8, height - 8 - textRenderer.fontHeight, 0xffffff);
        }
        countX = 0;

        filterAndSort();
        List<ItemData> items = List.copyOf(displayedItems);

        Size screenSize = new Size(width, height);

        searchBarVec = StorageBrowserAPI.Browser.getSearchBarPosition(screenSize.copy());
        searchBar.setPos(searchBarVec);

        PositionAndSize itemsVec = StorageBrowserAPI.Browser.getItemsPosition(screenSize.copy());

        if (items.isEmpty()) {
            Text text = NO_ITEM_TEXT;
            if (searchBar.loadProgress <= 1.0) text = LOADING_TEXT;
            else if (searchBar.searchProgress > 0.0 && searchBar.searchProgress <= 1.0) text = SEARCHING_TEXT;
            addLabel(itemsVec, text, context);
            searchBar.render(context, mouseX, mouseY, tickDelta);
            return;
        }

        signY = itemsVec.h < 0 ? -1 : 1;
        if (scrolled < 0) scrolled = 0;

        if (Math.abs(itemsVec.w) < 18 || Math.abs(itemsVec.h) < 18) {
            addLabel(itemsVec, NOT_ENOUGH_SPACE_TEXT, context);
            searchBar.render(context, mouseX, mouseY, tickDelta);
            return;
        }

        signX = itemsVec.w < 0 ? -1 : 1;
        countX = Math.abs(itemsVec.w) / 18;
        countY = Math.abs(itemsVec.h) / 18;
        startX = itemsVec.centerX() + ((signX * (1 - countX)) - 1) * 9 + 1;
//        startY = itemsVec.centerY() + ((signY * (1 - countY)) - 1) * 9 + 1;
        startY = itemsVec.y + signY * 9 - 8;
        int deltaX = signX * 18;
        int deltaY = signY * 18;
        cy18 = countY * 18;

        scrolled = Math.min(scrolled, Math.max(0.0, Math.ceil((float) items.size() / countX) - countY));

        if (items.size() > countX * countY) {
            scrollBarX = startX + signX * (countX * 18 - 5) + 7;
            totalRows = (int) Math.ceil((float) items.size() / countX);
            scrollBarSize = Math.max((int) Math.floor((float) countY / totalRows * cy18), 2);
            int y = startY + signY * (int) Math.floor((cy18 - scrollBarSize) * (scrolled / (totalRows - countY)));
            if (signY == -1) y += 19 - scrollBarSize;
            context.drawVerticalLine(scrollBarX, y, y + scrollBarSize, 0xffaaaaaa);
        }
        searchBar.render(context, mouseX, mouseY, tickDelta);

        flooredScrolled = (int) Math.floor(scrolled);

        int end = Math.min((flooredScrolled + countY) * countX, items.size());

        int guiScale = mc.options.getGuiScale().getValue();
        float textScale = (float) Math.ceil(guiScale / 2.0) / guiScale;

        int textDY = 16 - (int) Math.floor(textRenderer.fontHeight * textScale);

        for (int i = flooredScrolled * countX; i < end; ++i) {
            ItemData item = items.get(i);
            if (item == null) continue;
            int x = startX + deltaX * (i % countX);
            int y = startY + deltaY * (i / countX - flooredScrolled);
            context.drawItem(item.item, x, y);
            context.drawItemInSlot(textRenderer, item.item, x, y, "");
            Text countText = item.getCountText(textScale);
            if (countText != null || item.isPinned()) {
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200.0f);
                if (item.isPinned()) context.drawTextWithShadow(textRenderer, "\uD83D\uDCCC", x + 11, y - 2, 0xffff00);
                if (countText != null) {
                    int tx = x + 17 - (int) Math.ceil(textRenderer.getWidth(countText) * textScale);
                    int ty = y + textDY;
                    context.getMatrices().translate(tx, ty, 0);
                    context.getMatrices().scale(textScale, textScale, 1.0f);
                    context.drawTextWithShadow(textRenderer, countText, 0, 0, 0xffffff);
                }
                context.getMatrices().pop();
            }
        }

        int hovered = getHoveredIndex(mouseX, mouseY);
        if (hovered != -1) {
            ItemData item = items.get(hovered);
            if (tooltipFunction != null) {
                try {
                    Object[] tooltip = tooltipFunction.apply(item);
                    if (tooltip != null && tooltip.length > 0) {
                        List<Text> converted = Texts.convertToTextList(tooltip);
                        if (!converted.isEmpty()) {
                            context.drawTooltip(textRenderer, converted, mouseX + 4, mouseY + 4);
                        }
                    }
                } catch (Throwable e) {
                    StorageBrowserAPI.onCallbackError("tooltipFunction", e);
                    tooltipFunction = null;
                }
            }
            if (tooltipFunction == null) {
                List<Text> extra = item.getExtraTooltip();
                if (extraTooltipFunction != null) {
                    try {
                        Object[] extras = extraTooltipFunction.apply(item);
                        if (extras != null && extras.length > 0) {
                            extra.addAll(Texts.convertToTextList(extras));
                        }
                    } catch (Throwable e) {
                        StorageBrowserAPI.onCallbackError("extraTooltipFunction", e);
                        extraTooltipFunction = null;
                    }
                }
                if (!extra.isEmpty()) extraTooltip = extra;
                context.drawItemTooltip(textRenderer, item.item, mouseX + 4, mouseY + 4);
            }
        }
    }

    private void addLabel(@NotNull PositionAndSize vec, Text text, @NotNull DrawContext context) {
        context.drawTextWithShadow(
                textRenderer,
                text,
                vec.centerX() - textRenderer.getWidth(text) / 2,
                vec.centerY() - textRenderer.fontHeight / 2,
                0xffffff
        );
    }

    static class ItemsLoader {
        private final StorageBrowseScreen screen;
        private final DataManager profile;
        private final NbtList nbtItems;
        /** index: index of nbtItems, value: index of screen.loadedItems | -1: null | -2: unpacked shulker */
        private final int[] fastIndex;
        private final boolean unpackShulker;
        /** index: index of nbtItems */
        private final Map<Integer, Pair<ItemData[], long[]>> shulkerIndex;
        /** list of ItemData from unpacked shulker so unpacking can be faster */
        private final List<ItemData> unpackedItems;
        private final List<ItemData> nonIndexed;
        private final Path path;
        private final List<ChunkPos> chunks;
        private final Vec3d pos;

        private boolean loaded = false;

        public ItemsLoader(StorageBrowseScreen screen, DataManager profile, boolean unpackShulker, List<ChunkPos> chunks, Vec3d playerPos) {
            this.screen = screen;
            this.profile = profile;
            this.nbtItems = profile.getNbtItemList();
            this.unpackShulker = unpackShulker;
            this.path = profile.getCurrentPath();
            this.chunks = chunks;
            this.pos = playerPos;

            fastIndex = new int[this.nbtItems.size()];
            Arrays.fill(fastIndex, -1);
            if (unpackShulker) {
                shulkerIndex = new HashMap<>();
                unpackedItems = new ArrayList<>();
                nonIndexed = new ArrayList<>();
            } else {
                shulkerIndex = null;
                unpackedItems = null;
                nonIndexed = null;
            }
        }

        public void load() {
            if (loaded) return;
            loaded = true;
            int size = chunks.size();
            for (int i = 0; i < size; i++) {
                if (screen.isDestroyed()) return;
                screen.loadingLabel = String.format("Loading Chunk %s (%d/%d)", chunks.get(i), i + 1, size);
                screen.setLoadProgress((double) (i + 1) / size);
                loadItemsInChunk(chunks.get(i).toString());
            }
            screen.setLoadProgress(1.1);
            screen.loadingLabel = "";
        }

        private void loadItemsInChunk(String chunkPos) {
            File file = path.resolve(chunkPos + ".json").toFile();
            if (!file.isFile()) return;
            JsonObject chunk = readJson(file);
            if (chunk == null || !chunk.has("chests")) return;
            JsonElement chestsE = chunk.get("chests");
            if (!chestsE.isJsonObject()) return;
            JsonObject chests = chestsE.getAsJsonObject();
            for (String key : chests.keySet()) {
                if (screen.isDestroyed()) return;
                BlockPos pos = convertToBlockPos(key);
                if (pos == null) continue;
                JsonElement chestE = chests.get(key);
                if (!chestE.isJsonObject()) continue;
                JsonObject chest = chestE.getAsJsonObject();
                if (!chest.has("items") || !chest.has("counts")) continue;
                int[] items = convertToIntArray(chest.get("items"));
                if (items == null) continue;
                int[] counts = convertToIntArray(chest.get("counts"));
                if (counts == null) continue;
                decodeItems(items, counts, pos, chest.has("type") ? chest.get("type").getAsString() : "unknown");
            }
        }

        private void decodeItems(int @NotNull [] itemIndexes, int[] counts, BlockPos pos, String type) {
            int countIndex = 0;
            Map<Integer, Long> items = new HashMap<>();
            for (int itemIndex : itemIndexes) {
                if (itemIndex == 0) continue;
                Integer ii = itemIndex;
                items.put(ii, items.getOrDefault(ii, 0L) + counts[countIndex++]);
            }
            double distance = Math.sqrt(pos.getSquaredDistance(this.pos));
            for (int key : items.keySet()) {
                if (screen.isDestroyed()) return;
                addItem(key, items.get(key), distance, pos, type);
            }
        }

        private void addItem(int index, long count, double distance, BlockPos pos, String type) {
            if (index <= 0) return;
            int fi = fastIndex[index];
            if (fi != -1) {
                if (fi >= 0) {
                    ItemData item = screen.loadedItems.get(fi);
                    item.addCount(count);
                    item.foundAt(pos, type, distance);
                } else if (fi == -2) {
                    Pair<ItemData[], long[]> pair = shulkerIndex.get(index);
                    ItemData[] items = pair.getLeft();
                    long[] counts = pair.getRight();
                    for (int i = 0; i < items.length; i++) {
                        items[i].addCount(counts[i] * count);
                        items[i].foundAt(pos, type, distance);
                    }
                }
            } else {
                NbtElement nbt = nbtItems.get(index);
                if (nbt == null || nbt.getType() != NbtElement.COMPOUND_TYPE) return;
                ItemData item = ItemData.fromNbt(screen, (NbtCompound) nbt);
                if (item == null) return;
                item.setCount(count);
                item.foundAt(pos, type, distance);
                item.setIndex(index);
                if (!unpack(item)) {
                    int i = nonIndexed == null ? -1 : nonIndexed.indexOf(item);
                    if (i != -1) {
                        ItemData it = nonIndexed.get(i);
                        it.merge(item);
                        nonIndexed.remove(i);
                        fastIndex[it.getIndex()] = screen.loadedItems.indexOf(it);
                    } else {
                        fastIndex[item.getIndex()] = screen.loadedItems.size();
                        synchronized (screen.loadedItems) {
                            screen.loadedItems.add(item);
                        }
                    }
                }
            }
            screen.markDirty();
        }

        private boolean unpack(ItemData item) {
            if (!unpackShulker) return false;
            if (!Block.getBlockFromItem(item.item.getItem()).getDefaultState().isIn(BlockTags.SHULKER_BOXES)) return false;

            NbtCompound nbt1 = item.item.getNbt();
            if (nbt1 == null) return false;
            NbtElement nbt2 = nbt1.get("BlockEntityTag");
            if (nbt2 == null || nbt2.getType() != NbtElement.COMPOUND_TYPE) return false;
            NbtElement nbt3 = ((NbtCompound) nbt2).get("Items");
            if (nbt3 == null || nbt3.getType() != NbtElement.LIST_TYPE) return false;
            NbtList list = (NbtList) nbt3;
            if (list.getHeldType() != NbtElement.COMPOUND_TYPE) return false;

            HashMap<NbtCompound, Long> items = new HashMap<>();
            for (NbtElement e : list) {
                if (e.getType() != NbtElement.COMPOUND_TYPE) return false;
                NbtCompound o = (NbtCompound) e;
                if (!o.contains("id", NbtElement.STRING_TYPE)) return false;
                byte count = o.getByte("Count");
                if (count == 0) continue;
                NbtCompound nbt = new NbtCompound();
                nbt.putString("id", o.getString("id"));
                nbt.putByte("Count", (byte) 1);
                if (o.contains("tag", NbtElement.COMPOUND_TYPE)) nbt.put("tag", o.getCompound("tag").copy());

                items.put(nbt, items.getOrDefault(nbt, 0L) + count);
            }
            if (items.isEmpty()) return false;

            int size = items.size();
            ItemData[] itemArr = new ItemData[size];
            long[] countArr = new long[size];
            NbtCompound[] keys = items.keySet().toArray(new NbtCompound[0]);
            for (int i = 0; i < size; i++) {
                countArr[i] = items.get(keys[i]);
                ItemData data = ItemData.fromNbt(screen, keys[i]);
                if (data == null) return false;
                int index;
                if ((index = unpackedItems.indexOf(data)) != -1) {
                    data = unpackedItems.get(index).merge(data);
                } else if ((index = screen.loadedItems.indexOf(data)) != -1) {
                    data = screen.loadedItems.get(index).merge(data);
                    unpackedItems.add(data);
                } else {
                    unpackedItems.add(data);
                    nonIndexed.add(data);
                    synchronized (screen.loadedItems) {
                        screen.loadedItems.add(data);
                    }
                }
                itemArr[i] = data;
            }
            fastIndex[item.getIndex()] = -2;
            shulkerIndex.put(item.getIndex(), new Pair<>(itemArr, countArr));

            for (int i = 0; i < size; i++) {
                itemArr[i].addCount(countArr[i] * item.getCount());
                itemArr[i].foundAt(item.getNearestPos(), item.getNearestContainerType(), item.getDistance());
            }

            return true;
        }

        private @Nullable JsonObject readJson(File file) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element.isJsonObject()) return element.getAsJsonObject();
                else return null;
            } catch (Exception e) {
                return null;
            }
        }

        private int @Nullable [] convertToIntArray(@NotNull JsonElement element) {
            if (!element.isJsonArray()) return null;
            JsonArray jsonArr = element.getAsJsonArray();
            int len = jsonArr.size();
            int[] arr = new int[len];
            for (int i = 0; i < len; i++) {
                JsonElement e = jsonArr.get(i);
                if (!e.isJsonPrimitive()) return null;
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (!p.isNumber()) return null;
                arr[i] = p.getAsInt();
            }
            return arr;
        }

        private @Nullable BlockPos convertToBlockPos(@NotNull String pos) {
            String[] split = pos.split(",");
            if (split.length != 3) return null;
            try {
                return new BlockPos(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
            } catch (Throwable ignored) {
                return null;
            }
        }

    }

}
