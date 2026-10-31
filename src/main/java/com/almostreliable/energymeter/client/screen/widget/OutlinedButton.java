package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.client.screen.widget.base.LayoutPositionedButton;
import com.almostreliable.energymeter.core.Constants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class OutlinedButton extends LayoutPositionedButton {

    private static final int HEIGHT = 20;
    private static final int COLOR_HOVERED = 0xFF2B_2B2B;

    private final Runnable onPress;

    public OutlinedButton(int width, Component message, Runnable onPress) {
        super(width, HEIGHT, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var backgroundColor = isHovered() ? COLOR_HOVERED : Constants.COLOR_BACKGROUND;
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);
        guiGraphics.renderOutline(getX(), getY(), width, height, Constants.COLOR_ACCENT);

        renderString(guiGraphics, font, getFGColor() | Mth.ceil(alpha * 255.0F) << 24);
    }
}
