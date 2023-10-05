package io.github.amelonrind.storagebrowser.data.key;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChestPos {
    public final int x;
    public final int y;
    public final int z;
    private final String str;
    private ChunkPos chunkPos;

    public static ChestPos fromBlockPos(BlockPos pos) {
        if (pos == null) return null;
        return new ChestPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static @Nullable ChestPos fromString(@NotNull String str) {
        if (str.endsWith(".json")) str = str.substring(0, str.length() - 5);
        String[] pos = str.split(",");
        if (pos.length != 3) return null;
        try {
            return new ChestPos(Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2]));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public ChestPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        str = String.format("%d,%d,%d", x, y, z);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public ChunkPos toChunkPos() {
        return chunkPos == null ? (chunkPos = new ChunkPos(x >> 4, z >> 4)) : chunkPos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChestPos chestPos = (ChestPos) o;
        return x == chestPos.x && y == chestPos.y && z == chestPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return str;
    }

}
