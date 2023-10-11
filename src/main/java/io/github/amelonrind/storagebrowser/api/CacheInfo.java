package io.github.amelonrind.storagebrowser.api;

public class CacheInfo<T, U, R> {
    public boolean isCustom = false;
    private T t;
    private U u;
    private R r;

    public R getR() {
        return r;
    }

    public boolean matches(boolean isCustom, T t, U u) {
        return this.isCustom == isCustom && this.t == t && this.u == u;
    }

    public R setAll(boolean isCustom, T t, U u, R r) {
        this.isCustom = isCustom;
        this.t = t;
        this.u = u;
        this.r = r;
        return r;
    }

    public void clear() {
        t = null;
        u = null;
        r = null;
    }

}
