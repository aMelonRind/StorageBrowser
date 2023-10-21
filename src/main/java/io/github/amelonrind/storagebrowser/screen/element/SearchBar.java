package io.github.amelonrind.storagebrowser.screen.element;

import io.github.amelonrind.storagebrowser.StorageBrowser;
import io.github.amelonrind.storagebrowser.api.PositionAndSize;
import io.github.amelonrind.storagebrowser.mixin.MixinTextFieldWidget;
import io.github.amelonrind.storagebrowser.util.Texts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SearchBar extends TextFieldWidget {
    private static final List<Text> tooltip = List.of(
            StorageBrowser.translate("browser.search-bar.tooltip.title"),
            Texts.withColor(StorageBrowser.translate("browser.search-bar.tooltip.tooltip"), 0xffbf7f),
            Texts.withColor(StorageBrowser.translate("browser.search-bar.tooltip.tag"), 0x7fffff),
            Texts.withColor(StorageBrowser.translate("browser.search-bar.tooltip.identifier"), 0x9f9fff),
            Texts.withColor(StorageBrowser.translate("browser.search-bar.tooltip.mod"), 0xff7fff)
    );
    private static final Text placeholder = StorageBrowser.translate("browser.search-bar.placeholder");
    public static boolean changed = false;
    public static String searchText = "";

    public TextRenderer textRenderer;
    public int barColor = 0xFFEEEEEE;
    public double searchProgress = 0.0;
    public double loadProgress = 0.0;
    private int loadDoneTicks = 0;
    private int searchDoneTicks = 0;
    private int cursorTicks = 0;
    private int tooltipTicks = 0;
    private int barTicks = 4;

    public SearchBar(TextRenderer textRenderer) {
        super(textRenderer, 0, 0, 10, 0, placeholder);
        this.textRenderer = textRenderer;
        setMaxLength(128);
        setText(searchText);
        setDrawsBackground(false);
        setPlaceholder(placeholder);
        changed = true;
    }

    public void setPos(@NotNull PositionAndSize vec) {
        setX(vec.x);
        setY(vec.y);
        int w = vec.w;
        if (w < 10) w = 10;
        if (width != w) {
            width = w;
            int l = Math.min(searchText.length(), ((MixinTextFieldWidget) this).getSelectionStart() + 5);
            ((MixinTextFieldWidget) this).setFirstCharacterIndex(l - textRenderer.trimToWidth(new StringBuilder(searchText.substring(0, l)).reverse().toString(), w).length());
        }
        height = vec.h;
    }

    @Override
    public void tick() {
        ++loadDoneTicks;
        ++searchDoneTicks;
        ++cursorTicks;
        ++tooltipTicks;
        if (barTicks < 4) ++barTicks;
    }

    @Override
    public void setCursor(int cursor) {
        super.setCursor(cursor);
        cursorTicks = 0;
    }

    protected void onChanged() {
        if (!searchText.equals(getText())) {
            searchText = getText();
            changed = true;
            searchProgress = 0.0;
        }
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused && getText().isBlank()) setText("");
        if (focused || isFocused()) barTicks = 0;
        super.setFocused(focused);
        cursorTicks = 0;
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        if (!searchText.equals(getText())) onChanged();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) super.setText("");
        setDrawsBackground(false);
        super.mouseClicked(mouseX, mouseY, button);
        if (!searchText.equals(getText())) onChanged();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean res = super.keyPressed(keyCode, scanCode, modifiers);
        if (res) onChanged();
        return res;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        boolean res = super.charTyped(chr, modifiers);
        if (res) onChanged();
        return res;
    }

    private int getColor(char c) {
        return switch (c) {
            case '#' -> 0xffbf7f;
            case '$' -> 0x7fffff;
            case '*' -> 0x9f9fff;
            case '@' -> 0xff7fff;
            default -> 0xffffff;
        };
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isFocused()) {
            renderBar(context, getX() + 3 - barTicks, getX() + width - 3 + barTicks, getY() + height, false);
        } else {
            renderBar(context, getX() - 1 + barTicks, getX() + width + 1 - barTicks, getY() + height, true);
        }
        if (isFocused() || !searchText.isEmpty()) {
            if (searchText.isEmpty()) {
                if (cursorTicks % 12 < 6) {
                    context.drawTextWithShadow(textRenderer, "_", getX(), getY() + height / 2 - 5, 0xffffff);
                }
            } else {
                int fci = ((MixinTextFieldWidget) this).getFirstCharacterIndex();
                int selStart = ((MixinTextFieldWidget) this).getSelectionStart() - fci;
                int selEnd = ((MixinTextFieldWidget) this).getSelectionEnd() - fci;
                String txt = textRenderer.trimToWidth(searchText.substring(fci), width);
                boolean cursorInTxt = 0 <= selStart && selStart <= txt.length();
                boolean cursorVisible = cursorInTxt && isFocused() && cursorTicks % 12 < 6;
                int y = getY() + height / 2 - 5;
                if (selEnd > txt.length()) selEnd = txt.length();

                int color = 0xffffff;
                int li = searchText.lastIndexOf(" ", fci);
                if (li == -1) {
                    color = getColor(searchText.charAt(0));
                } else if (li < fci) {
                    color = getColor(searchText.charAt(li + 1));
                }

                int x = getX();
                int index = 0;
                int index2;
                while (index < txt.length()) {
                    index2 = txt.indexOf(" ", index);
                    if (index2 == -1 || index2 == txt.length() - 1) {
                        context.drawTextWithShadow(textRenderer, txt.substring(index), x, y, color);
                        break;
                    }
                    x = context.drawTextWithShadow(textRenderer, txt.substring(index, index2 + 1), x, y, color) - 1;
                    index = index2 + 1;
                    color = getColor(txt.charAt(index));
                }

                if (cursorVisible || selStart != selEnd) {
                    if (selStart > txt.length()) selStart = txt.length();
                    int x1 = getX() + textRenderer.getWidth(txt.substring(0, selStart));
                    if (cursorVisible) {
                        if (selStart != selEnd || fci + selStart != searchText.length()) {
                            context.fill(RenderLayer.getGuiOverlay(), x1, y - 1, x1 + 1, y + 10, 0xffd0d0d0);
                        } else {
                            context.drawTextWithShadow(textRenderer, "_", x1, y, 0xFFFFFF);
                        }
                    }
                    if (selStart != selEnd) {
                        int x2 = getX() + textRenderer.getWidth(txt.substring(0, selEnd));
                        if (x1 > x2) {
                            int t = x1;
                            x1 = x2;
                            x2 = t;
                        }
                        if (x1 < getX()) x1 = getX();
                        if (x2 > getX() + width) x2 = getX() + width;
                        context.fill(RenderLayer.getGuiTextHighlight(), x1, y - 1, x2, y + 10, 0xff0000ff);
                    }
                }
            }
        } else {
            context.drawCenteredTextWithShadow(textRenderer, placeholder, getX() + width / 2, getY() + height / 2 - 5, 0xdddddd);
        }
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (getX() <= mouseX && mouseX < getX() + width && getY() <= mouseY && mouseY < getY() + height) {
            if (tooltipTicks > 12) context.drawTooltip(textRenderer, tooltip, getX() + width, getY() + 15);
        } else tooltipTicks = 0;
    }

    private void renderBar(DrawContext context, int x1, int x2, int y, boolean dim) {
        if (loadProgress > 0.0) {
            if (loadProgress > 1.0) {
                if (loadDoneTicks < 16) {
                    context.fill(x1, y + 1, x2, y + 2, 0xff00eeee - loadDoneTicks * 0x10000000);
                }
            } else {
                int lx2 = x1 + (int) Math.floor((x2 - x1) * loadProgress);
                if (lx2 == x1) ++lx2;
                context.fill(x1, y + 1, lx2, y + 2, 0xff00eeee);
                loadDoneTicks = 0;
            }
        }
        if (dim) context.setShaderColor(0.75f, 0.75f, 0.75f, 1.0f);
        context.fill(x1, y, x2, y + 1, barColor);
        if (searchProgress > 0.0) {
            if (searchText.isBlank()) {
                searchProgress = 1.1;
                searchDoneTicks = 999;
            }
            if (searchProgress > 1.0) {
                if (searchDoneTicks < 8) {
                    context.fill(x1, y, x2, y + 1, 0xff00ff00 - searchDoneTicks * 0x20000000);
                }
            } else {
                int sx2 = x1 + (int) Math.floor((x2 - x1) * searchProgress);
                if (sx2 == x1) ++sx2;
                context.fill(x1, y, sx2, y + 1, 0xff00ff00);
                searchDoneTicks = 0;
            }
        }
    }

}

