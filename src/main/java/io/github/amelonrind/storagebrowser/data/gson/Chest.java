package io.github.amelonrind.storagebrowser.data.gson;

import com.google.gson.annotations.Expose;
import io.github.amelonrind.storagebrowser.data.DataManager;
import io.github.amelonrind.storagebrowser.data.key.ChestPos;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Chest implements Iterable<Integer> {
    private ChestPos pos;
    @Expose private String type;
    @SuppressWarnings("UnusedAssignment")
    @Expose private int slots = 0;
    @Expose private int[] items;
    @Expose private int[] counts;
    @SuppressWarnings("UnusedAssignment")
    @Expose private long time = 0L;

    public static Chest fromInventory(DataManager profile, ChestPos pos, String type, @NotNull Inventory inv, int from, int size) {
        if (from > inv.size()) from = inv.size();
        if (from + size > inv.size()) size = inv.size() - from;
        if (size <= 0 || inv.isEmpty()) return getEmpty(pos, type, size);
        List<Integer> items = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        int last = from + size - 1;
        while (inv.getStack(last).isEmpty() && last >= 0) last--;
        last++;
        for (int slot = from; slot < last; slot++) {
            ItemStack stack = inv.getStack(slot);
            int index = profile.getItemIndex(stack);
            items.add(index);
            if (index != 0) counts.add(stack.getCount());
        }
        if (counts.isEmpty()) return getEmpty(pos, type, size);
        int[] itemsArr = new int[items.size()];
        int[] countsArr = new int[counts.size()];
        for (int i = 0; i < items.size(); i++) itemsArr[i] = items.get(i);
        for (int i = 0; i < counts.size(); i++) countsArr[i] = counts.get(i);
        return new Chest(type, size, itemsArr, countsArr, System.currentTimeMillis()).init(pos);
    }

    private static Chest getEmpty(ChestPos pos, String type, int slots) {
        return new Chest(type, slots, null, null, System.currentTimeMillis()).init(pos);
    }

    public Chest(String type, int slots, int[] items, int[] counts, long time) {
        this.type = type;
        this.slots = slots;
        this.items = items;
        this.counts = counts;
        this.time = time;
    }

    public boolean init(String chestPos) {
        pos = ChestPos.fromString(chestPos);
        if (pos == null) return false;
        init(pos);
        return Arrays.stream(items).filter(i -> i > 0).count() == counts.length;
    }

    private Chest init(ChestPos pos) {
        this.pos = pos;
        if (type == null) type = "chest";
        if (items == null) items = new int[0];
        if (counts == null) counts = new int[0];
        return this;
    }

    public ChestPos getPos() {
        return pos;
    }

    @SuppressWarnings("unused")
    public int getSlots() {
        return slots;
    }

    @SuppressWarnings("unused")
    public int[] getItems() {
        return items;
    }

    @SuppressWarnings("unused")
    public int[] getCounts() {
        return counts;
    }

    @SuppressWarnings("unused")
    public long getTime() {
        return time;
    }

    @NotNull
    @Override
    public Iterator<Integer> iterator() {
        return Arrays.stream(items).iterator();
    }

}
