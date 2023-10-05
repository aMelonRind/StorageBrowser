package io.github.amelonrind.storagebrowser.api;

import io.github.amelonrind.storagebrowser.StorageBrowser;
import io.github.amelonrind.storagebrowser.data.DataManager;
import io.github.amelonrind.storagebrowser.data.ItemData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.*;

import static io.github.amelonrind.storagebrowser.data.DataManager.FILE_NAME_REGEX;

@SuppressWarnings("unused")
public class StorageBrowserAPI {
    private static final Map<String, Supplier<String>> serverDetectors = new HashMap<>();
    private static @Nullable BiFunction<Size, Integer, Position> statusPosition;
    public static @Nullable BiConsumer<String, Throwable> onCallbackError;

    public static class Browser {
        private static @Nullable Function<Size, PositionAndSize> itemsPosition;
        private static @Nullable Function<Size, PositionAndSize> searchBarPosition;
        private static @Nullable Predicate<ItemData> lastFilterer = null;
        private static @Nullable BiFunction<ItemData, ItemData, Double> lastSortMethod = null;
        public static @Nullable Predicate<ItemData> filterer = null;
        public static @Nullable BiFunction<ItemData, ItemData, Double> sortMethod = null;
        public static @Nullable BiConsumer<ItemData, Integer> onClickItem = null;
        public static @Nullable Function<ItemData, Object[]> tooltipFunction = null;
        public static @Nullable Function<ItemData, Object[]> extraTooltipFunction = null;

        public static void setItemsPositionFunction(@Nullable Function<Size, PositionAndSize> func) {
            itemsPosition = func;
            itemsPositionCache.clear();
        }

        public static void setSearchBarPositionFunction(@Nullable Function<Size, PositionAndSize> func) {
            searchBarPosition = func;
            searchBarPositionCache.clear();
        }

        public static boolean wasMutatorChanged() {
            if (lastFilterer != filterer || lastSortMethod != sortMethod) {
                lastFilterer = filterer;
                lastSortMethod = sortMethod;
                return true;
            }
            return false;
        }

        @Contract(pure = true)
        public static @Nullable Comparator<ItemData> getSortComparator() {
            if (sortMethod == null) return null;
            BiFunction<ItemData, ItemData, Double> finalSortMethod = sortMethod;
            return (a, b) -> (int) Math.signum(finalSortMethod.apply(a, b));
        }

        public static int defaultSortMethod(@NotNull ItemData a, @NotNull ItemData b) {
            int res = a.compareCount(b);
            if (res != 0) return -res;
            res = a.compareName(b);
            if (res != 0) return res;
            return a.compareDistance(b);
        }

        public static PositionAndSize getItemsPosition(Size size) {
            return runFunction(
                    "itemsPositionFunction",
                    itemsPosition,
                    () -> itemsPosition == null ? null : itemsPosition.apply(size.copy()),
                    () -> defaultItemsPosition.apply(size),
                    itemsPositionCache,
                    size
            );
        }

        public static PositionAndSize getSearchBarPosition(Size size) {
            return runFunction(
                    "searchBarPositionFunction",
                    searchBarPosition,
                    () -> searchBarPosition == null ? null : searchBarPosition.apply(size.copy()),
                    () -> defaultSearchBarPosition.apply(size),
                    searchBarPositionCache,
                    size
            );
        }

    }

    public static void setServerDetector(String profileName, @Nullable Supplier<String> detector) {
        if (profileName == null) return;
        if (detector != null) serverDetectors.put(profileName, detector);
        else serverDetectors.remove(profileName);
        DataManager profile = StorageBrowser.getCurrentProfile();
        if (profile != null && profile.profileName.equals(profileName)) profile.updateDimension();
    }

    public static void setStatusPositionFunction(@Nullable BiFunction<Size, Integer, Position> func) {
        statusPosition = func;
        statusPositionCache.clear();
    }


    private static final BiFunction<Size, Integer, Position> defaultStatusPosition = (size, rows) -> {
        if (rows == -1) return size.center().add(-110, -105);
        if (rows == -2) return size.center().add(-123, -117);
        return size.center().add(-110, -40 - rows * 9);
    };
    private static final Function<Size, PositionAndSize> defaultItemsPosition = size ->
            size.center().add(-90, -80).withSize(180, 101);
    private static final Function<Size, PositionAndSize> defaultSearchBarPosition = size ->
            size.center().add(-88, -100).withSize(176, 16);

    private static final CacheInfo<Size, Integer, Position> statusPositionCache = new CacheInfo<>();
    private static final CacheInfo<Size, ?, PositionAndSize> itemsPositionCache = new CacheInfo<>();
    private static final CacheInfo<Size, ?, PositionAndSize> searchBarPositionCache = new CacheInfo<>();

    public static @Nullable String getServer(String profileName) {
        if (!serverDetectors.containsKey(profileName)) return "default";
        String res = "default";
        try {
            res = serverDetectors.get(profileName).get();
        } catch (Exception e) {
            onCallbackError("serverDetector:" + profileName, e);
        }
        if (res != null && res.matches(FILE_NAME_REGEX)) return res;
        return null;
    }

    public static Position getStatusPosition(Size size, int rows) {
        return runFunction(
                "statusPositionFunction",
                statusPosition,
                () -> statusPosition == null ? null : statusPosition.apply(size.copy(), rows),
                () -> defaultStatusPosition.apply(size, rows),
                statusPositionCache,
                size,
                rows
        );
    }

    private static <T extends Position, U, R extends Cacheable<?>> R runFunction(
            String name, Object func, Supplier<R> runner, Supplier<R> defaultFuncRunner, CacheInfo<T, U, R> cache, T arg
    ) {
        return runFunction(name, func, runner, defaultFuncRunner, cache, arg, null);
    }

    private static <T extends Position, U, R extends Cacheable<?>> R runFunction(
            String name, Object func, Supplier<R> runner, Supplier<R> defaultFuncRunner, CacheInfo<T, U, R> cache, T arg, @Nullable U arg2
    ) {
        if (cache.matches(func != null, arg, arg2)) return cache.getR();
        if (func != null) {
            try {
                R res = runner.get();
                if (res != null) {
                    if (res.cache) cache.setAll(true, arg, arg2, res);
                    else cache.clear();
                    return res;
                }
            } catch (Throwable e) {
                onCallbackError(name, e);
            }
        }
        return cache.setAll(false, arg, arg2, defaultFuncRunner.get());
    }

    public static void onCallbackError(String name, Throwable err) {
        if (onCallbackError != null) try {
            onCallbackError.accept(name, err);
        } catch (Throwable ignore) {}
    }

}
