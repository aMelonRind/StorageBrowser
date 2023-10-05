package io.github.amelonrind.storagebrowser.mixin;

import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextFieldWidget.class)
public interface MixinTextFieldWidget {

    @Accessor int getFirstCharacterIndex();

    @Accessor void setFirstCharacterIndex(int value);

    @Accessor int getSelectionStart();

    @Accessor int getSelectionEnd();

}
