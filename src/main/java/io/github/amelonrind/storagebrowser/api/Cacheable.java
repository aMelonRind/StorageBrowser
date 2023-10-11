package io.github.amelonrind.storagebrowser.api;

@SuppressWarnings("unused")
public abstract class Cacheable<T extends Cacheable<T>> {
    public boolean cache = true;

    @SuppressWarnings("unchecked")
    public T unCache() {
        cache = false;
        return (T) this;
    }

}
