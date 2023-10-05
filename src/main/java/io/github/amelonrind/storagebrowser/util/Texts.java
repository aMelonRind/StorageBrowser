package io.github.amelonrind.storagebrowser.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Texts {
    public static Text EMPTY = Text.literal("");

    public static MutableText getTextWithColor(@NotNull String text, int color) {
        return withColor(Text.literal(text), color);
    }

    public static MutableText getTextWithColorIndex(@NotNull String text, int color) {
        return withColorIndex(Text.literal(text), color);
    }

    public static MutableText getTranslatableWithColor(@NotNull String key, int color) {
        return withColor(Text.translatable(key), color);
    }

    public static MutableText withColor(@NotNull MutableText text, int color) {
        return text.setStyle(Style.EMPTY.withColor(color));
    }

    public static MutableText withColorIndex(@NotNull MutableText text, int color) {
        return text.setStyle(Style.EMPTY.withColor(Formatting.byColorIndex(color)));
    }

    public static List<Text> translateMultiLine(String key) {
        return translateMultiLine(key, key);
    }

    public static List<Text> translateMultiLine(String key, String fallback) {
        return Arrays.stream(Language.getInstance().get(key, fallback).split("\n"))
                .map((Function<? super String, Text>) Text::literal)
                .toList();
    }

    public static List<Text> convertToTextList(Object[] arr) {
        return Arrays.stream(arr)
                .map((Function<? super Object, Text>) line -> line instanceof Text txt ? txt : Text.literal(line.toString()))
                .toList();
    }

}
