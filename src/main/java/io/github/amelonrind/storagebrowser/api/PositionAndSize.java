package io.github.amelonrind.storagebrowser.api;

import net.minecraft.client.gui.widget.Widget;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class PositionAndSize extends Cacheable<PositionAndSize> {
    public int x;
    public int y;
    public int w;
    public int h;

    public PositionAndSize(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public PositionAndSize setPosToWidget(@NotNull Widget widget) {
        widget.setX(x);
        widget.setY(y);
        return this;
    }

    public boolean contains(int x, int y) {
        if (w == 0 || h == 0) return false;
        x = x - this.x;
        y = y - this.y;
        return (x == 0 || (x > 0 == w > 0 && (x > 0 ? w > x : w < x))) && (y == 0 || (y > 0 == h > 0 && (y > 0 ? h > y : h < y)));
    }

    public int centerX() {
        return x + w / 2;
    }

    public int centerY() {
        return y + h / 2;
    }

}
