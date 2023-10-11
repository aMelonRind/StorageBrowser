package io.github.amelonrind.storagebrowser.data;

import com.google.common.io.Files;
import com.google.gson.*;
import io.github.amelonrind.storagebrowser.StorageBrowser;
import io.github.amelonrind.storagebrowser.api.StorageBrowserAPI;
import io.github.amelonrind.storagebrowser.data.gson.Chest;
import io.github.amelonrind.storagebrowser.data.gson.ChestChunk;
import io.github.amelonrind.storagebrowser.data.gson.ItemSets;
import io.github.amelonrind.storagebrowser.data.key.ChestPos;
import io.github.amelonrind.storagebrowser.data.key.ChunkPos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static io.github.amelonrind.storagebrowser.StorageBrowser.LOGGER;

public class DataManager {
    public static final String FILE_NAME_REGEX = "^[^\\\\/:*?\"<>|\\r\\n]+(?<!\\.)(?<!^/s+)$";
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<Object, Runnable> saveTasks = new HashMap<>();
    private static boolean saving = false;
    public static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
    public static final Gson uglyGson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    public static final Path configRoot = FabricLoader.getInstance().getConfigDir().resolve(StorageBrowser.MOD_ID);
    public static final Settings profileIndex = Settings.open(configRoot.resolve("profileIndex.json").toFile());
    public static final Settings globalSettings = Settings.open(configRoot.resolve("settings.json").toFile());

    static {
        File root = configRoot.toFile();
        if (!root.isDirectory()) if (!root.mkdir()) LOGGER.warn("Failed to create config root");
    }

    public static @Nullable String getProfileIndex(String id) {
        return profileIndex.has(id, Settings.Type.STRING) ? profileIndex.get(id, "") : null;
    }

    public static void setProfileIndex(String id, String name) {
        profileIndex.set(id, name);
    }

    @SuppressWarnings("unused")
    public static void removeProfileIndex(String id) {
        profileIndex.remove(id);
    }

    public static boolean hasProfile(String name) {
        return profileIndex.getJson().asMap().values().stream().anyMatch(e -> {
            if (!e.isJsonPrimitive()) return false;
            JsonPrimitive p = e.getAsJsonPrimitive();
            return p.isString() && p.getAsString().equals(name);
        });
    }

    public static @Nullable DataManager getCurrentProfile() {
        String name = getProfileIndex(getCurrentWorldId());
        return name == null ? null : new DataManager(name);
    }

    /**
     * the same implementation as jsmacros FWorld.getWorldIdentifier()
     */
    public static @NotNull String getCurrentWorldId() {
        IntegratedServer server = mc.getServer();
        if (server != null) {
            return "LOCAL_" + server.getSavePath(WorldSavePath.ROOT).normalize().getFileName();
        }
        ServerInfo multiplayerServer = mc.getCurrentServerEntry();
        if (multiplayerServer != null) {
            if (mc.isConnectedToRealms()) {
                return "REALM_" + multiplayerServer.name;
            }
            if (multiplayerServer.isLocal()) {
                return "LAN_" + multiplayerServer.name;
            }
            return multiplayerServer.address.replace(":25565", "").replace(":", "_");
        }
        return "UNKNOWN_NAME";
    }

    private static @NotNull String escapeIdentifier(@NotNull String id) {
        if (id.endsWith(".")) id = id.substring(0, id.length() - 1) + "_";
        return id.replaceAll("[:/]", "_");
    }

    public static String verifyPathName(@NotNull String name) {
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return name.endsWith(".") ? name.replaceFirst("\\.+$", "") : name;
    }

    public static void backup(@NotNull File file) {
        if (!file.isFile()) return;
        String bakName = file.getName() + ".bak";
        int max = 0;
        String[] files = file.toPath().getParent().toFile().list((dir, name) -> name.startsWith(bakName));
        if (files != null) {
            int nameLen = bakName.length();
            max = Math.max(0, Arrays.stream(files).mapToInt(name -> {
                try {
                    return Integer.parseInt(name.substring(nameLen));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }).max().orElse(0));
        }
        max++;
        try {
            Files.copy(file, Path.of(file.getPath()).getParent().resolve(bakName + max).toFile());
        } catch (IOException e) {
            LOGGER.error("Error while backing up file (" + file.getPath() + ")", e);
        }
    }

    public static void writeJson(String name, File file, JsonElement json) {
        writeJson(name, file, json, false);
    }

    public static void writeJson(String name, File file, JsonElement json, boolean ugly) {
        addSaveTask(file, () -> {
            File dir = file.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                LOGGER.warn("Failed to create directory for file " + file);
                return;
            }
            try (FileWriter writer = new FileWriter(file)) {
                (ugly ? uglyGson : gson).toJson(json, writer);
            } catch (IOException | JsonIOException e) {
                LOGGER.error("Error while writing " + name + " json file (" + file.getPath() + ")", e);
            }
        });
    }

    public static void addSaveTask(Object key, Runnable task) {
        synchronized (saveTasks) {
            saveTasks.put(key, task);
        }
    }

    public static void onTick() {
        if (!saveTasks.isEmpty() && !saving) {
            saving = true;
            new Thread(() -> {
                synchronized (saveTasks) {
                    for (Runnable task : saveTasks.values()) {
                        try {
                            task.run();
                        } catch (Throwable ignored) {}
                    }
                    saveTasks.clear();
                }
                saving = false;
            }, "Storage Browser Data Save Task").start();
        }
    }


    public final String profileName;
    public final Settings settings;
    @SuppressWarnings("FieldCanBeLocal")
    private final Path profileRoot;
    private final Path chestsRoot;
    private final File itemsFile;
    private String serverId = "default";
    private String dimension = null;
    /**
     * index 0 is always air
     */
    private final NbtList items;
    private final Map<Integer, ItemStack> itemStackCache = new TreeMap<>();
    private int newItemIndex = -1;
    private Path currentPath;
    private final Map<ChunkPos, ChestChunk> chestChunks = new HashMap<>();
    public final ItemSets itemSets;

    public DataManager(String profileName) {
        this.profileName = profileName = verifyPathName(profileName);
        settings = Settings.open(profileName);
        profileRoot = configRoot.resolve("profiles").resolve(profileName);
        chestsRoot = profileRoot.resolve("chests");
        if (!chestsRoot.toFile().isDirectory() && !chestsRoot.toFile().mkdirs()) {
            LOGGER.warn("Failed to create directory for " + profileName);
        }
        itemsFile = profileRoot.resolve("items.nbt").toFile();
        NbtList data = null;
        if (itemsFile.isFile()) try {
            NbtCompound nbt = NbtIo.readCompressed(itemsFile);
            if (nbt == null) throw new NullPointerException("loaded value is null");
            data = nbt.getList("items", NbtElement.COMPOUND_TYPE);
        } catch (Throwable e) {
            LOGGER.warn("Failed to load items for " + profileName, e);
            backup(itemsFile);
            if (!itemsFile.delete()) LOGGER.warn("Failed to delete file " + itemsFile);
        }
        NbtCompound empty = new NbtCompound();
        items = data == null ? new NbtList() : data;
        if (items.isEmpty()) items.add(empty);

        itemSets = ItemSets.load(profileRoot);

        List<Integer> existItems = itemSets.getAllItems().stream().sorted().toList();
        boolean shouldSaveItems = false;
        int size = items.size();
        int existSize = existItems.size();
        if (existSize == 0) {
            if (size > 1) {
                items.clear();
                items.add(empty);
                shouldSaveItems = true;
            }
        } else {
            int ii = 0;
            for (int i = 1; i < size; i++) {
                while (existItems.get(ii) < i && ii + 1 < existSize) ii++;
                if (i != existItems.get(ii)) {
                    items.set(i, empty);
                    shouldSaveItems = true;
                }
            }
            for (int i = size - 1; i > 0 && empty.equals(items.get(i)); i--) {
                items.remove(i);
                shouldSaveItems = true;
            }
        }
        if (shouldSaveItems) saveItems();

        updateNewItemIndex();
        LOGGER.info(String.format("new DataManager(\"%s\"), Loaded items size: %d", profileName, items.size() - 1));
        updateDimension();
    }

    private void saveItems() {
        addSaveTask(itemsFile, () -> {
            NbtCompound nbt = new NbtCompound();
            nbt.put("items", items);
            try {
                NbtIo.writeCompressed(nbt, itemsFile);
            } catch (IOException e) {
                LOGGER.error("Error while writing items.nbt file (" + itemsFile.getPath() + ")", e);
            }
        });
    }

    private void updateNewItemIndex() {
        if (newItemIndex + 1 == items.size()) {
            newItemIndex++;
            return;
        }
        int nullIndex = items.lastIndexOf(new NbtCompound());
        newItemIndex = nullIndex > 0 ? nullIndex : items.size();
    }

    public void updateDimension() {
        String idr = StorageBrowserAPI.getServer(profileName);
        String dmr = mc.world == null ? "null" : escapeIdentifier(mc.world.getRegistryKey().getValue().toString());
        if (idr == null) idr = serverId;
        if (!Objects.equals(serverId, idr) || !Objects.equals(dimension, dmr) || chestChunks.size() > 128) {
            clearCache();
        }
        currentPath = chestsRoot.resolve(serverId = idr).resolve(dimension = dmr);
        File root = currentPath.toFile();
        if (!root.isDirectory() && !root.mkdirs()) {
            LOGGER.warn(String.format("failed to create dir: data/%s/chests/%s/%s", profileName, serverId, dimension));
        }
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    public String getCurrentPathShort() {
        return serverId + "/" + dimension;
    }

    public NbtList getNbtItemList() {
        return items.copy();
    }

    public int getItemIndex(@NotNull ItemStack item) {
        if (item.isEmpty()) return 0;
        NbtCompound nbt = ItemData.getNbtFromItem(item);
        if (globalSettings.get("cleanUUID", true)) ItemData.cleanUUID(nbt);
        int index = items.indexOf(nbt);
        if (index != -1) return index;
        index = newItemIndex;
        if (index >= items.size()) {
            index = items.size();
            items.add(nbt);
        } else items.set(index, nbt);
        updateNewItemIndex();
        saveItems();
        return index;
    }

    @SuppressWarnings("unused")
    public ItemStack getItem(int index) {
        if (itemStackCache.containsKey(index)) return itemStackCache.get(index);
        NbtCompound nbt = getNbtItem(index);
        return itemStackCache.put(index, nbt == null ? null : ItemStack.fromNbt(nbt));
    }

    public NbtCompound getNbtItem(int index) {
        if (index <= 0 || index >= items.size()) return null;
        NbtCompound nbt = (NbtCompound) items.get(index);
        return (nbt == null || nbt.isEmpty()) ? null : nbt;
    }

    public File getChunkFile(ChunkPos pos) {
        return currentPath.resolve(pos + ".json").toFile();
    }

    public @NotNull ChestChunk getChestChunk(ChunkPos pos) {
        ChestChunk chunk = chestChunks.get(pos);
        if (chunk != null) return chunk;
        File file = getChunkFile(pos);
        chunk = ChestChunk.load(file, pos);
        if (chunk == null) chunk = new ChestChunk(file, pos);
        chestChunks.put(pos, chunk);
        return chunk;
    }

    public void saveChestChunk(ChunkPos pos) {
        ChestChunk chunk = chestChunks.get(pos);
        if (chunk != null) {
            itemSets.setChunk(serverId, dimension, chunk);
            itemSets.write();
            chunk.write();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasChestChunkData(ChunkPos pos) {
        ChestChunk chunk;
        if ((chunk = chestChunks.get(pos)) != null && !chunk.isEmpty()) return true;
        return getChunkFile(pos).isFile();
    }

    public void setChestData(@NotNull ChestPos pos, String type, Inventory inv) {
        setChestData(pos, type, inv, 0, inv.size());
    }

    public void setChestData(@NotNull ChestPos pos, String type, Inventory inv, int from) {
        setChestData(pos, type, inv, from, 27);
    }

    public void setChestData(@NotNull ChestPos pos, String type, Inventory inv, int from, int size) {
        ChestChunk chunk = getChestChunk(pos.toChunkPos());
        if (chunk.isIgnored(pos)) return;
        chunk.addChest(Chest.fromInventory(this, pos, type, inv, from, size));
        saveChestChunk(pos.toChunkPos());
    }

    public @Nullable Chest getChestData(@NotNull ChestPos pos) {
        if (!hasChestChunkData(pos.toChunkPos())) return null;
        return getChestChunk(pos.toChunkPos()).getChest(pos);
    }

    public void setIgnored(@NotNull ChestPos pos, boolean ignore) {
        ChestChunk chunk = getChestChunk(pos.toChunkPos());
        if (ignore ? chunk.ignore(pos) : chunk.unIgnore(pos)) saveChestChunk(pos.toChunkPos());
    }

    public boolean isIgnored(@NotNull ChestPos pos) {
        if (!hasChestChunkData(pos.toChunkPos())) return false;
        return getChestChunk(pos.toChunkPos()).isIgnored(pos);
    }

    public List<ChunkPos> getChunksInRenderDistance() {
        return getChunksInRenderDistance(32);
    }

    public List<ChunkPos> getChunksInRenderDistance(int max) {
        if (mc.player == null) return new ArrayList<>();
        int dist = Math.min(max, mc.options.getViewDistance().getValue());
        int x = mc.player.getChunkPos().x;
        int z = mc.player.getChunkPos().z;
        return getChunksInChunkArea(x - dist, z - dist, x + dist, z + dist);
    }

    public List<ChunkPos> getChunksInChunkArea(int x1, int z1, int x2, int z2) {
        return new ArrayList<>(getAllChunksStream()
                .filter(c -> c.x >= x1 && c.x <= x2 && c.z >= z1 && c.z <= z2).toList());
    }

    @SuppressWarnings("unused")
    public List<ChunkPos> getAllChunks() {
        return new ArrayList<>(getAllChunksStream().toList());
    }

    private Stream<ChunkPos> getAllChunksStream() {
        String[] list = currentPath.toFile().list();
        if (list == null) return Stream.empty();
        return Arrays.stream(list).map(ChunkPos::fromString).filter(Objects::nonNull);
    }

    public void clearCache() {
        chestChunks.clear();
        itemStackCache.clear();
    }

    @SuppressWarnings("unused")
    public static class Settings {
        private static final Path profileRoot = configRoot.resolve("profiles");
        private final File file;
        private JsonObject json;

        @Contract("_ -> new")
        public static @NotNull Settings open(@NotNull String profileName) {
            return open(profileRoot.resolve(profileName).resolve("settings.json").toFile());
        }

        @Contract("_ -> new")
        public static @NotNull Settings open(@NotNull File file) {
            return new Settings(file);
        }

        private Settings(File file) {
            this.file = file;
            reload();
        }

        public void reload() {
            if (file.isFile()) try (FileReader reader = new FileReader(file)) {
                JsonElement e = JsonParser.parseReader(reader);
                if (!e.isJsonObject()) throw new AssertionError("loaded json isn't object!");
                json = e.getAsJsonObject();
                return;
            } catch (IOException | JsonIOException | JsonSyntaxException e) {
                LOGGER.error("Error while reading settings json file (" + file.getPath() + ")", e);
                backup(file);
            }
            json = new JsonObject();
            write();
        }

        private void write() {
            writeJson("settings", file, json);
        }

        public JsonObject getJson() {
            return json;
        }

        public boolean has(String key, Type type) {
            JsonPrimitive p = getPrimitive(key);
            if (p == null) return false;
            if (type == null) return true;
            return switch (type) {
                case BOOLEAN -> p.isBoolean();
                case NUMBER -> p.isNumber();
                case STRING -> p.isString();
            };
        }

        public void remove(String key) {
            json.remove(key);
        }

        public boolean get(String key, boolean defaultValue) {
            JsonPrimitive p = getPrimitive(key);
            if (p != null && p.isBoolean()) return p.getAsBoolean();
            json.addProperty(key, defaultValue);
            write();
            return defaultValue;
        }

        public int get(String key, int defaultValue) {
            JsonPrimitive p = getPrimitive(key);
            if (p != null && p.isNumber()) return p.getAsInt();
            json.addProperty(key, defaultValue);
            write();
            return defaultValue;
        }

        public double get(String key, double defaultValue) {
            JsonPrimitive p = getPrimitive(key);
            if (p != null && p.isNumber()) return p.getAsDouble();
            json.addProperty(key, defaultValue);
            write();
            return defaultValue;
        }

        public String get(String key, String defaultValue) {
            JsonPrimitive p = getPrimitive(key);
            if (p != null && p.isString()) return p.getAsString();
            json.addProperty(key, defaultValue);
            write();
            return defaultValue;
        }

        private @Nullable JsonPrimitive getPrimitive(String key) {
            if (!json.has(key)) return null;
            JsonElement e = json.get(key);
            return e.isJsonPrimitive() ? e.getAsJsonPrimitive() : null;
        }

        @SuppressWarnings("UnusedReturnValue")
        public boolean set(String key, boolean value) {
            json.addProperty(key, value);
            write();
            return value;
        }

        @SuppressWarnings("UnusedReturnValue")
        public int set(String key, int value) {
            json.addProperty(key, value);
            write();
            return value;
        }

        @SuppressWarnings("UnusedReturnValue")
        public double set(String key, double value) {
            json.addProperty(key, value);
            write();
            return value;
        }

        @SuppressWarnings("UnusedReturnValue")
        public String set(String key, String value) {
            json.addProperty(key, value);
            write();
            return value;
        }

        public enum Type {
            BOOLEAN,
            NUMBER,
            STRING
        }

    }

}
