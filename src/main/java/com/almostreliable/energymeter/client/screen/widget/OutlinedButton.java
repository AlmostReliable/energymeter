package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.client.screen.widget.base.LayoutPositionedButton;
import com.almostreliable.energymeter.core.Constants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class OutlinedButton extends LayoutPositionedButton {

    private static final int HEIGHT = 20;
    private static final int COLOR_HOVERED = 0xFF2B_2B2B;

    private final Runnable onPress;

    public OutlinedButton(int width, Component message, Runnable onPress) {
        super(width, HEIGHT, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var backgroundColor = isHovered() ? COLOR_HOVERED : Constants.COLOR_BACKGROUND;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);
        graphics.outline(getX(), getY(), width, height, Constants.COLOR_ACCENT);

        var textRenderer = graphics.textRendererForWidget(this, HoveredTextEffects.NONE);
        extractDefaultLabel(textRenderer);
    }
}
