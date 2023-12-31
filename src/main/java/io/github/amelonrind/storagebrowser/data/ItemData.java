package io.github.amelonrind.storagebrowser.data;

import io.github.amelonrind.storagebrowser.screen.StorageBrowseScreen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("unused")
public class ItemData implements Comparable<ItemData> {
    private static final Text[] smallCountTexts = new Text[] {
            Text.literal("0"),
            null,
            Text.literal("2"),
            Text.literal("3"),
            Text.literal("4"),
            Text.literal("5"),
            Text.literal("6"),
            Text.literal("7"),
            Text.literal("8"),
            Text.literal("9")
    };
    @SuppressWarnings("SpellCheckingInspection")
    private static final String units = "KMBTQ";
    public static final Object infoSync = new Object();
    public final StorageBrowseScreen screen;
    public final ItemStack item;
    private int index = -1;
    private long count = 0L;
    private double distance = -1.0;
    private BlockPos nearest = null;
    private String nearestType = null;
    private boolean isPinned = false;

    private boolean generatedCountText = false;
    private Text countText = null;
    private boolean countIsShort = false;

    private boolean generatedTooltip = false;
    private List<Text> extraTooltip = null;

    private final Map<SearchSession.Type, SearchSession.Token> searchTokens = new HashMap<>(6, 1.0f);

    @ApiStatus.Internal
    public static @NotNull String localeNumber(long num) {
        if (-1000L < num && num < 1000L) return Long.toString(num);
        StringBuilder str = new StringBuilder(Long.toString(num));
        int i = str.indexOf(".");
        if (i == -1) i = str.length();
        int stop = str.charAt(0) == '-' ? 1 : 0;
        while ((i -= 3) > stop) str.insert(i, ",");
        return str.toString();
    }

    @ApiStatus.Internal
    public static void cleanUUID(@NotNull NbtCompound nbt) {
        for (String key : Set.copyOf(nbt.getKeys())) {
            if (key.length() == 4) {
                if (key.equalsIgnoreCase("text")) continue;
                if (key.equalsIgnoreCase("uuid")) {
                    nbt.remove(key);
                    continue;
                }
            }
            switch (nbt.getType(key)) {
                case NbtElement.STRING_TYPE: {
                    String str = nbt.getString(key);
                    int len = str.length();
                    if (len != 32 && !(len == 36 &&
                            str.charAt(8) == '-' &&
                            str.charAt(13) == '-' &&
                            str.charAt(18) == '-' &&
                            str.charAt(23) == '-')
                    ) continue;
                    if (len == 36) str = new StringBuilder(str)
                            .deleteCharAt(8)
                            .deleteCharAt(12)
                            .deleteCharAt(16)
                            .deleteCharAt(20)
                            .toString();
                    if (str.chars().allMatch(c -> ('0' <= c && c <= '9') || ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F'))) {
                        nbt.remove(key);
                    }
                    break;
                }
                case NbtElement.INT_ARRAY_TYPE: {
                    if (nbt.containsUuid(key)) nbt.remove(key);
                    break;
                }
                case NbtElement.COMPOUND_TYPE: {
                    cleanUUID(nbt.getCompound(key));
                    break;
                }
                case NbtElement.LIST_TYPE: {
                    NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
                    int size = list.size();
                    for (int i = 0; i < size; i++) cleanUUID(list.getCompound(i));
                    break;
                }
            }
        }
    }

    @ApiStatus.Internal
    public static @NotNull NbtCompound getNbtFromItem(@NotNull ItemStack item) {
        NbtCompound nbt = item.writeNbt(new NbtCompound());
        nbt.putByte("Count", (byte) 1);
        return nbt;
    }

    @ApiStatus.Internal
    public static @Nullable ItemData fromNbt(StorageBrowseScreen screen, NbtCompound nbt) {
        ItemStack stack = ItemStack.fromNbt(nbt);
        if (stack == ItemStack.EMPTY) return null;
        int count = stack.getCount();
        if (count == 1) return new ItemData(screen, stack);
        stack.setCount(1);
        ItemData item = new ItemData(screen, stack);
        item.setCount(count);
        return item;
    }

    public ItemData(StorageBrowseScreen screen, ItemStack item) {
        this.screen = screen;
        this.item = item;
    }

    public int getIndex() {
        return index;
    }

    @ApiStatus.Internal
    public void setIndex(int index) {
        this.index = index;
        this.isPinned = screen.profile.itemSets.isFavorite(index);
    }

    public double getDistance() {
        return distance;
    }

    public BlockPos getNearestPos() {
        return nearest;
    }

    public String getNearestContainerType() {
        return nearestType;
    }

    public void setPinned(boolean pinned) {
        if (isPinned == pinned) return;
        if (index == -1) index = screen.profile.getItemIndex(item);
        isPinned = pinned;
        if (pinned) {
            screen.profile.itemSets.addFavorite(index);
        } else {
            screen.profile.itemSets.removeFavorite(index);
        }
        screen.markDirty();
    }

    public boolean isPinned() {
        return isPinned;
    }

    public long getCount() {
        return count;
    }

    @ApiStatus.Internal
    public void setCount(long count) {
        synchronized (infoSync) {
            generatedCountText = false;
            generatedTooltip = false;
            this.count = count;
        }
    }

    @ApiStatus.Internal
    public void addCount(long count) {
        synchronized (infoSync) {
            generatedCountText = false;
            generatedTooltip = false;
            if (this.count + count < this.count && count >= 0L) this.count = Long.MAX_VALUE;
            else this.count += count;
        }
    }

    @ApiStatus.Internal
    public void foundAt(BlockPos pos, String type, double distance) {
        synchronized (infoSync) {
            if (this.distance != -1.0 && distance >= this.distance) return;
            this.distance = distance;
            nearest = pos;
            nearestType = type;
        }
    }

    @ApiStatus.Internal
    public ItemData merge(@NotNull ItemData other) {
        addCount(other.count);
        foundAt(other.nearest, other.nearestType, other.distance);
        if (index == -1) index = other.index;
        if (other.isPinned) setPinned(true);
        return this;
    }

    public Text getCountText(double textScale) {
        synchronized (infoSync) {
            if (generatedCountText) return countText;
            generatedCountText = true;
            generatedTooltip = false;
            countIsShort = false;
            if (count < 10L && count >= 0L) return countText = smallCountTexts[(int) count];
            String str = Long.toString(count);
            if (count < 10000L && count > 0L) return countText = Text.literal(str);
            boolean isLarge = textScale > 0.6;
            int len = str.length();
            int unit = Math.min(units.length(), (len - (str.startsWith("-") ? 1 : 0) - (isLarge ? 1 : 2)) / 3);
            StringBuilder res = new StringBuilder();
            if (unit == 0) res.append(str);
            else {
                countIsShort = true;
                res.append(str, 0, len - unit * 3);
                if (res.length() < (isLarge ? 3 : 4)) res.append(".").append(str.charAt(len - unit * 3));
                res.append(units.charAt(unit - 1));
            }
            if (res.charAt(0) == '-') res.insert(0, "§c");
            return countText = Text.literal(res.toString());
        }
    }

    public ArrayList<Text> getExtraTooltip() {
        synchronized (infoSync) {
            if (generatedTooltip) return extraTooltip != null ? new ArrayList<>(extraTooltip) : new ArrayList<>();
            generatedTooltip = true;
            List<Text> tooltips = new ArrayList<>();

            if (countIsShort) tooltips.add(Text.literal("§7Count: " + localeNumber(count)));

            extraTooltip = (tooltips.isEmpty() ? null : tooltips);
            return extraTooltip != null ? new ArrayList<>(extraTooltip) : new ArrayList<>();
        }
    }

    public int comparePinned(@NotNull ItemData other) {
        if (isPinned == other.isPinned) return 0;
        return isPinned ? -1 : 1;
    }

    public int compareCount(@NotNull ItemData other) {
        return Long.compare(count, other.count);
    }

    public int compareName(@NotNull ItemData other) {
        return item.getName().getString().compareTo(other.item.getName().getString());
    }

    public int compareDistance(@NotNull ItemData other) {
        if (distance == other.distance) return 0;
        if (distance == -1.0) return 1;
        if (other.distance == -1.0) return -1;
        return (distance - other.distance < 0.0) ? -1 : 1;
    }

    public int compareSearch(@NotNull ItemData other) {
        for (SearchSession.Type t : SearchSession.Type.priority) {
            int res = SearchSession.Token.compare(searchTokens.get(t), other.searchTokens.get(t));
            if (res != 0) return res;
        }
        return 0;
    }

    private void initSearchTokens() {
        String name = item.getName().getString();
        String tooltip = item.getTooltip(null, TooltipContext.BASIC).stream()
                .skip(1)
                .map(Text::getString)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        Identifier fullId = Registries.ITEM.getId(item.getItem());
        String modid = fullId.getNamespace();
        String id = fullId.getPath();
        String tags = item.getRegistryEntry().streamTags()
                .map(t -> t.id().toString())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        screen.searchSession.setupTokens(searchTokens, name, tooltip, modid, id, tags);
    }

    public int matchQuery() {
        if (searchTokens.isEmpty()) initSearchTokens();
        return searchTokens.values().stream().mapToInt(SearchSession.Token::match).sum();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean matchesQuery() {
        return !searchTokens.isEmpty() && searchTokens.values().stream().allMatch(SearchSession.Token::matches);
    }

    public boolean doesntMatchQuery() {
        return !matchesQuery();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemData oi)) return false;
        if (index != -1 && oi.index != -1) return index == oi.index;
        return ItemStack.areEqual(item, oi.item);
    }

    @Override
    public int compareTo(@NotNull ItemData o) {
        int res;
        if ((res = comparePinned(o)) != 0) return res;
        if (!screen.searchSession.isQueryEmpty() && (res = compareSearch(o)) != 0) return res;
        if ((res = compareCount(o)) != 0) return -res;
        if ((res = compareName(o)) != 0) return res;
        return compareDistance(o);
    }

}

