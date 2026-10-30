package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.client.screen.widget.base.LayoutPositionedButton;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.data.EnergyMeterLang.LangEntry;
import com.almostreliable.energymeter.util.TexRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.layouts.EqualSpacingLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RadioButton<T extends Enum<T>> extends LayoutPositionedButton {

    private static final int TEXTURE_SIZE = 24;
    private static final int VERTICAL_SPACING = 1;
    private static final int HORIZONTAL_SPACING = 4;
    private static final TexRenderer MANAGER = TexRenderer.button("radio", TEXTURE_SIZE);
    private static final TexRenderer UNSELECTED = MANAGER.copy().tex(0, 0, TEXTURE_SIZE / 2);
    private static final TexRenderer UNSELECTED_HOVERED = MANAGER.copy().tex(TEXTURE_SIZE / 2, 0, TEXTURE_SIZE / 2);
    private static final TexRenderer SELECTED = MANAGER.copy().tex(0, TEXTURE_SIZE / 2, TEXTURE_SIZE / 2);
    private static final TexRenderer SELECTED_HOVERED = MANAGER.copy().tex(TEXTURE_SIZE / 2, TEXTURE_SIZE / 2, TEXTURE_SIZE / 2);

    private final Supplier<Boolean> isSelected;
    private final Runnable onSelect;

    private RadioButton(T value, Component message, Supplier<T> currentSelectionSupplier, Consumer<T> onSelect) {
        super(
            TEXTURE_SIZE / 2 + HORIZONTAL_SPACING + Minecraft.getInstance().font.width(message),
            TEXTURE_SIZE / 2,
            message
        );
        this.isSelected = () -> value == currentSelectionSupplier.get();
        this.onSelect = () -> onSelect.accept(value);
    }

    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    public static <T extends Enum<T>> Layout createGroup(
        int width, Map<T, LangEntry> possibleSelections, Supplier<T> currentSelectionSupplier, Consumer<T> onSelectionChanged
    ) {
        var height = possibleSelections.size() * ((TEXTURE_SIZE / 2) + VERTICAL_SPACING);
        var layout = new EqualSpacingLayout(width, height, EqualSpacingLayout.Orientation.VERTICAL);
        for (var entry : possibleSelections.entrySet()) {
            var selection = entry.getKey();
            var selectionMessage = entry.getValue();

            var radioButton = new RadioButton<>(selection, selectionMessage.get(), currentSelectionSupplier, onSelectionChanged);
            layout.addChild(radioButton);
        }
        return layout;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (isSelected.get()) return;
        onSelect.run();
    }

    @Override
    public void playDownSound(SoundManager handler) {
        if (isSelected.get()) return;
        super.playDownSound(handler);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        TexRenderer buttonIcon;
        if (active && isHovered) {
            buttonIcon = isSelected.get() ? SELECTED_HOVERED : UNSELECTED_HOVERED;
        } else {
            buttonIcon = isSelected.get() ? SELECTED : UNSELECTED;
            setFocused(false);
        }

        buttonIcon.target(getX(), getY()).render(graphics);
        var textColor = active ? Constants.COLOR_WHITE : Constants.COLOR_GRAY;
        graphics.text(font, getMessage(), getX() + TEXTURE_SIZE / 2 + HORIZONTAL_SPACING, getY() + 2, textColor, false);
    }
}
