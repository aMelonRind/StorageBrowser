package io.github.amelonrind.storagebrowser.data.key;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChunkPos {
    public final int x;
    public final int z;
    private final String str;

    @SuppressWarnings("unused")
    @Contract("_ -> new")
    public static @NotNull ChunkPos fromBlockPos(@NotNull BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static @Nullable ChunkPos fromString(@NotNull String str) {
        if (str.endsWith(".json")) str = str.substring(0, str.length() - 5);
        String[] pos = str.split(",");
        if (pos.length != 2) return null;
        try {
            return new ChunkPos(Integer.parseInt(pos[0]), Integer.parseInt(pos[1]));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
        str = String.format("%d,%d", x, z);
    }

    public boolean contains(@NotNull ChestPos chestPos) {
        return (chestPos.x >> 4) == x && (chestPos.z >> 4) == z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkPos chunkPos = (ChunkPos) o;
        return x == chunkPos.x && z == chunkPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return str;
    }

}
