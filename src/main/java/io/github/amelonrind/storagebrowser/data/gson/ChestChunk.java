package io.github.amelonrind.storagebrowser.data.gson;

import com.google.gson.annotations.Expose;
import io.github.amelonrind.storagebrowser.data.DataManager;
import io.github.amelonrind.storagebrowser.data.key.ChestPos;
import io.github.amelonrind.storagebrowser.data.key.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

import static io.github.amelonrind.storagebrowser.StorageBrowser.LOGGER;

public class ChestChunk implements Iterable<Chest> {
    private boolean init = false;
    private File file;
    private ChunkPos chunkPos;
    @Expose private TreeMap<String, Chest> chests;
    @Expose private TreeSet<String> ignored;

    public static @Nullable ChestChunk load(@NotNull File file, @NotNull ChunkPos pos) {
        if (file.isFile()) try (FileReader reader = new FileReader(file)) {
            return DataManager.gson.fromJson(reader, ChestChunk.class).init(file, pos);
        } catch (Exception e) {
            LOGGER.warn("Error while loading chunk" + file.getName(), e);
        }
        return null;
    }

    public ChestChunk(File file, ChunkPos pos) {
        init(file, pos);
    }

    public ChestChunk init(File file, ChunkPos pos) {
        if (init) return this;
        init = true;
        this.file = file;
        chunkPos = pos;
        if (chests == null) chests = new TreeMap<>();
        else for (String key : Set.copyOf(chests.keySet())) {
            Chest chest = chests.get(key);
            if (chest == null || !chest.init(key)) chests.remove(key);
        }
        if (ignored == null) ignored = new TreeSet<>();
        return this;
    }

    public boolean isIgnored(@NotNull ChestPos pos) {
        return ignored.contains(pos.toString());
    }

    public boolean ignore(@NotNull ChestPos pos) {
        ignored.add(pos.toString());
        return chests.remove(pos.toString()) != null;
    }

    public boolean unIgnore(@NotNull ChestPos pos) {
        return ignored.removeIf(s -> pos.toString().equals(s));
    }

    public ChestChunk addChest(@NotNull Chest chest) {
        if (isIgnored(chest.getPos()) || !chunkPos.contains(chest.getPos())) return this;
        chests.put(chest.getPos().toString(), chest);
        return this;
    }

    public @Nullable Chest getChest(@NotNull ChestPos pos) {
        Chest chest = chests.get(pos.toString());
        if (chest == null) return null;
        if (isIgnored(pos)) {
            chests.remove(pos.toString());
            return null;
        }
        return chest;

    }

    public ChunkPos getPos() {
        return chunkPos;
    }

    public TreeSet<Integer> getContainedItems() {
        TreeSet<Integer> res = new TreeSet<>();
        for (Chest chest : chests.values()) for (int item : chest) res.add(item);
        res.remove(0);
        return res;
    }

    public boolean isEmpty() {
        return chests.isEmpty() && ignored.isEmpty();
    }

    public void write() {
        DataManager.addSaveTask(this, () -> {
            if (isEmpty()) {
                if (file.isFile() && !file.delete()) LOGGER.warn("Failed to delete chunk data" + file.getName());
                return;
            }
            try (FileWriter writer = new FileWriter(file)) {
                DataManager.uglyGson.toJson(this, ChestChunk.class, writer);
            } catch (Exception e) {
                LOGGER.warn("Error while writing chunk" + file.getName(), e);
            }
        });
    }

    @NotNull
    @Override
    public Iterator<Chest> iterator() {
        return chests.values().iterator();
    }

}
