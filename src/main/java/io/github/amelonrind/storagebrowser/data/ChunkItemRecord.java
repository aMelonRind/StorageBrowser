package io.github.amelonrind.storagebrowser.data;

import com.google.gson.*;
import io.github.amelonrind.storagebrowser.data.gson.ChestChunk;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static io.github.amelonrind.storagebrowser.StorageBrowser.LOGGER;

public class ChunkItemRecord {
    private final File file;
    public final JsonObject data;

    @Contract("_ -> new")
    public static @NotNull ChunkItemRecord load(@NotNull Path profileRoot) {
        File file = profileRoot.resolve("chunk-item_record.json").toFile();
        if (!file.isFile()) return new ChunkItemRecord(file);
        boolean shouldBackup = false;
        try (FileReader reader = new FileReader(file)) {
            JsonElement e = JsonParser.parseReader(reader);
            if (!e.isJsonObject()) throw new AssertionError("loaded json is not object");
            JsonObject o = e.getAsJsonObject();
            Set<String> m = o.keySet();
            Path root = profileRoot.resolve("chests");
            for (String key : m) {
                if (key.split("[\\\\/]").length != 3 || !root.resolve(key + ".json").toFile().isFile()) {
                    shouldBackup = true;
                    o.remove(key);
                }
            }
            if (shouldBackup) DataManager.backup(file);
            return new ChunkItemRecord(file, o);
        } catch (IOException e) {
            LOGGER.warn("Failed to load chunk-item record", e);
            shouldBackup = true;
        }
        if (shouldBackup) DataManager.backup(file);
        return new ChunkItemRecord(file);
    }

    public ChunkItemRecord(File file) {
        this(file, new JsonObject());
    }

    public ChunkItemRecord(File file, JsonObject data) {
        this.file = file;
        this.data = data;
    }

    public void setChunk(String serverId, String dimension, @NotNull ChestChunk chunk) {
        String key = serverId + "/" + dimension + "/" + chunk.getPos();
        Set<Integer> items = chunk.getContainedItems();
        if (items.isEmpty()) {
            data.remove(key);
            return;
        }
        JsonArray arr = new JsonArray(items.size());
        items.forEach(arr::add);
        data.add(key, arr);
    }

    public void write() {
        DataManager.writeJson("chunk-item_record", file, data, true);
    }

    public Set<Integer> getAll() {
        Set<Integer> res = new HashSet<>();
        for (JsonElement e : data.asMap().values()) {
            if (!e.isJsonArray()) continue;
            for (JsonElement n : e.getAsJsonArray()) {
                if (!n.isJsonPrimitive()) continue;
                JsonPrimitive p = n.getAsJsonPrimitive();
                if (!p.isNumber()) continue;
                try {
                    res.add(p.getAsInt());
                } catch (Throwable ignored) {}
            }
        }
        return res;
    }

}
