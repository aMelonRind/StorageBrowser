package io.github.amelonrind.storagebrowser.api;

import net.minecraft.client.gui.widget.Widget;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class Position extends Cacheable<Position> {
    public double x;
    public double y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Position add(double x, double y) {
        return new Position(this.x + x, this.y + y);
    }

    public Position multiply(double x, double y) {
        return new Position(this.x * x, this.y * y);
    }

    public Position add(double adder) {
        return add(adder, adder);
    }

    public Position multiply(double multiplier) {
        return multiply(multiplier, multiplier);
    }

    public Position center() {
        return multiply(0.5);
    }

    public PositionAndSize withSize(int width, int height) {
        return new PositionAndSize((int) x, (int) y, width, height);
    }

    public PositionAndSize withSize(@NotNull Size size) {
        return withSize(size.width, size.height);
    }

    public PositionAndSize withCenteredSize(int width, int height) {
        return center().add((double) -width / 2, (double) -height / 2).withSize(width, height);
    }

    public PositionAndSize withCenteredSize(@NotNull Size size) {
        return withCenteredSize(size.width, size.height);
    }

    public Position copy() {
        Position c = new Position(x, y);
        c.cache = this.cache;
        return c;
    }

    public Position setPosToWidget(@NotNull Widget widget) {
        widget.setX((int) x);
        widget.setY((int) y);
        return this;
    }

}
