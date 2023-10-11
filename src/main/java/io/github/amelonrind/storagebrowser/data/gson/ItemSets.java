package io.github.amelonrind.storagebrowser.data.gson;

import com.google.gson.annotations.Expose;
import io.github.amelonrind.storagebrowser.data.DataManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import static io.github.amelonrind.storagebrowser.StorageBrowser.LOGGER;

public class ItemSets {
    private static final Predicate<Integer> notPositive = i -> i <= 0;
    private boolean init = false;
    private File file;

    @Expose private TreeSet<Integer> pinned;
    @Expose private TreeMap<String, TreeSet<Integer>> chunkItemRecord;

    public static @NotNull ItemSets load(@NotNull Path profileRoot) {
        File file = profileRoot.resolve("itemSets.json").toFile();
        if (file.isFile()) try (FileReader reader = new FileReader(file)) {
            return DataManager.gson.fromJson(reader, ItemSets.class).init(profileRoot, file);
        } catch (Exception e) {
            LOGGER.warn("Error while loading itemSets" + file.getName(), e);
        }
        return new ItemSets().init(profileRoot, file);
    }

    private ItemSets init(Path profileRoot, File file) {
        if (init) return this;
        init = true;
        this.file = file;
        if (pinned == null) pinned = new TreeSet<>();
        pinned.removeIf(notPositive);
        if (chunkItemRecord == null) chunkItemRecord = new TreeMap<>();
        else for (String key : Set.copyOf(chunkItemRecord.keySet())) {
            Path root = profileRoot.resolve("chests");
            if (key.split("[\\\\/]").length != 3 || !root.resolve(key + ".json").toFile().isFile()) {
                chunkItemRecord.remove(key);
            } else {
                Set<Integer> items = chunkItemRecord.get(key);
                if (items == null) chunkItemRecord.remove(key);
                else items.removeIf(notPositive);
            }
        }
        return this;
    }

    public void addFavorite(int item) {
        pinned.add(item);
        write();
    }

    public void removeFavorite(int item) {
        pinned.remove(item);
        write();
    }

    public boolean isFavorite(int item) {
        return pinned.contains(item);
    }

    public void setChunk(String serverId, String dimension, @NotNull ChestChunk chunk) {
        String key = serverId + "/" + dimension + "/" + chunk.getPos();
        TreeSet<Integer> items = chunk.getContainedItems();
        items.removeIf(notPositive);
        if (items.isEmpty()) chunkItemRecord.remove(key);
        else chunkItemRecord.put(key, items);
    }

    public void write() {
        DataManager.addSaveTask(this, () -> {
            try (FileWriter writer = new FileWriter(file)) {
                DataManager.uglyGson.toJson(this, ItemSets.class, writer);
            } catch (Exception e) {
                LOGGER.warn("Error while writing itemSets" + file.getName(), e);
            }
        });
    }

    public Set<Integer> getAllItems() {
        Set<Integer> res = new HashSet<>();
        chunkItemRecord.values().forEach(res::addAll);
        res.addAll(pinned);
        return res;
    }

}
