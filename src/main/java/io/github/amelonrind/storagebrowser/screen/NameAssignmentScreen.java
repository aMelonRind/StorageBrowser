package io.github.amelonrind.storagebrowser.screen;

import io.github.amelonrind.storagebrowser.StorageBrowser;
import io.github.amelonrind.storagebrowser.data.DataManager;
import io.github.amelonrind.storagebrowser.util.Texts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import static io.github.amelonrind.storagebrowser.data.DataManager.FILE_NAME_REGEX;

public class NameAssignmentScreen extends Screen {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final String SCREEN_TITLE = StorageBrowser.translateKey("name-assign.screen-title");
    private static final String TITLE1 = StorageBrowser.translateKey("name-assign.title1");
    private static final String TITLE2 = StorageBrowser.translateKey("name-assign.title2");
    private static final String WORLD_ID = StorageBrowser.translateKey("name-assign.world-id");
    private static final String CONFIRM = StorageBrowser.translateKey("name-assign.confirm");
    private static final String TOOLTIP_INVALID = StorageBrowser.translateKey("name-assign.tooltip.invalid");
    private static final String TOOLTIP_DUPLICATE = StorageBrowser.translateKey("name-assign.tooltip.duplicate");
    private static final String TOOLTIP_CONFIRM_DUPLICATE = StorageBrowser.translateKey("name-assign.tooltip.confirm-duplicate");
    private static final String TOOLTIP_VALID = StorageBrowser.translateKey("name-assign.tooltip.valid");
    private static final String TEXT_FIELD_NARRATION = StorageBrowser.translateKey("name-assign.text-field.narration");

    public static void open(Screen parent, String worldId, @Nullable Runnable onClose) {
        mc.setScreen(new NameAssignmentScreen(parent, worldId, onClose));
    }

    private final Text text_title1 = Text.translatable(TITLE1);
    private final Text text_title2 = Text.translatable(TITLE2);
    private final Text text_worldId;
    private final Tooltip tooltip_invalid = Tooltip.of(Text.translatable(TOOLTIP_INVALID));
    private final Tooltip tooltip_duplicate = Tooltip.of(Text.translatable(TOOLTIP_DUPLICATE));
    private final Tooltip tooltip_confirmDuplicate = Tooltip.of(Text.translatable(TOOLTIP_CONFIRM_DUPLICATE));
    private final Tooltip tooltip_valid = Tooltip.of(Text.translatable(TOOLTIP_VALID));

    private final Screen parent;
    private final String worldId;
    private final @Nullable Runnable onClose;
    private final String confirmText = Language.getInstance().get(CONFIRM);

    private TextFieldWidget textField;
    private ButtonWidget button;

    private State state = State.EMPTY;
    private String text = "";

    private NameAssignmentScreen(Screen parent, String worldId, @Nullable Runnable onClose) {
        super(Text.translatable(SCREEN_TITLE));
        this.parent = parent;
        this.worldId = worldId;
        this.onClose = onClose;
        text_worldId = Texts.withColorIndex(Text.translatable(WORLD_ID, worldId), 0x8);
    }

    @Override
    protected void init() {
        clearChildren();
        super.init();

        // infer server name from world identifier
        if (worldId.startsWith("LOCAL_")) text = worldId.substring(6);
        else if (worldId.matches("^\\w.*")) {
            text = worldId;
            if (text.contains(".")) {
                int last = text.lastIndexOf(".");
                if (last == 0) text = text.substring(1);
                else {
                    text = text.substring(text.lastIndexOf(".", last - 1) + 1, last);
                    if (text.isBlank()) text = "";
                }
            }
            if (!text.isBlank()) {
                if (text.contains(":")) text = text.substring(0, text.indexOf(':'));
                if (text.chars().noneMatch(c -> c >= 'a' && c <= 'z')) text = text.toLowerCase();
                text = text.substring(0, 1).toUpperCase() + text.substring(1);
            }
        }

        int cx = width / 2;
        int cy = height / 2;
        button = new ButtonWidget.Builder(Text.literal(confirmText), this::onConfirm)
                .position(cx - 30, cy + 16)
                .width(60)
                .build();
        textField = new TextFieldWidget(
                mc.textRenderer,
                cx - Math.min(80, cx - 10),
                cy - 8,
                Math.max(Math.min(80, cx - 10), 10) * 2,
                16,
                Text.translatable(TEXT_FIELD_NARRATION)
        );
        textField.setFocusUnlocked(false);
        textField.setMaxLength(32);
        textField.setChangedListener(this::onTextChange);
        textField.setText(text);
        addDrawableChild(button);
        addDrawableChild(textField);
        setInitialFocus(textField);
    }

    @Override
    public void tick() {
        super.tick();
        textField.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;
        int cx = width / 2;
        int cy = height / 2;
        context.drawTextWithShadow(tr, text_title1,  cx - tr.getWidth(text_title1)  / 2, cy - 48, 0xFFFFFF);
        context.drawTextWithShadow(tr, text_title2,  cx - tr.getWidth(text_title2)  / 2, cy - 38, 0xFFFFFF);
        context.drawTextWithShadow(tr, text_worldId, cx - tr.getWidth(text_worldId) / 2, cy - 28, 0xFFFFFF);
    }

    private void onTextChange(String str) {
        if (str == null || str.isBlank()) {
            button.setMessage(Text.literal(confirmText));
            button.setTooltip(null);
            text = "invalid";
            state = State.EMPTY;
        } else if (!str.matches(FILE_NAME_REGEX)) {
            button.setMessage(Texts.getTextWithColorIndex(confirmText, 0xc));
            button.setTooltip(tooltip_invalid);
            text = "invalid";
            state = State.INVALID;
        } else if (DataManager.hasProfile(str)) {
            button.setMessage(Texts.getTextWithColorIndex(confirmText, 0x6));
            button.setTooltip(tooltip_duplicate);
            text = str;
            state = State.DUPLICATE;
        } else {
            button.setMessage(Texts.getTextWithColorIndex(confirmText, 0xa));
            button.setTooltip(tooltip_valid);
            text = str;
            state = State.VALID;
        }
    }

    private void onConfirm(ButtonWidget btn) {
        if (state == State.DUPLICATE) {
            btn.setMessage(Texts.getTextWithColorIndex(confirmText, 0x6));
            btn.setTooltip(tooltip_confirmDuplicate);
            state = State.DUPLICATE_CONFIRM;
        } else if (state == State.VALID || state == State.DUPLICATE_CONFIRM) {
            DataManager.setProfileIndex(worldId, text);
            close();
        }
    }

    @Override
    public void close() {
        if (onClose != null) onClose.run();
        mc.setScreen(parent);
    }

    enum State { EMPTY, INVALID, VALID, DUPLICATE, DUPLICATE_CONFIRM }

}
