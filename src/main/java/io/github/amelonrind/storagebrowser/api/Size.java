package io.github.amelonrind.storagebrowser.api;

import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * just for appropriate name for screen size instead of position
 */
@SuppressWarnings("unused")
public class Size extends Position {
    public int width;
    public int height;

    @Contract("_ -> new")
    public static @NotNull Size fromScreen(@NotNull Screen screen) {
        return new Size(screen.width, screen.height);
    }

    public Size(int width, int height) {
        super(width, height);
        this.width = width;
        this.height = height;
    }

    public Size copy() {
        return new Size(width, height);
    }

}
