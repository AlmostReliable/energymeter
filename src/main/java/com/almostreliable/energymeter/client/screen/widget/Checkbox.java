package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.client.screen.widget.base.LayoutPositionedButton;
import com.almostreliable.energymeter.core.Constants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class Checkbox extends LayoutPositionedButton {

    private static final int BOX_SIZE = 13;
    private static final int CHECK_INSET = 3;
    private static final int HORIZONTAL_SPACING = 4;
    private static final int COLOR_BORDER = 0xFF00_0000;
    private static final int COLOR_BORDER_INACTIVE = 0xFF55_5555;
    private static final int COLOR_HOVERED = 0x60FF_FFFF;

    private final BooleanSupplier isChecked;
    private final Runnable onToggle;

    public Checkbox(int width, Component message, BooleanSupplier isChecked, Runnable onToggle) {
        super(width, BOX_SIZE, message);
        this.isChecked = isChecked;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress() {
        onToggle.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var boxX = getX() + width - BOX_SIZE;
        var y = getY();
        var borderColor = active ? COLOR_BORDER : COLOR_BORDER_INACTIVE;

        if (active && isHovered) {
            guiGraphics.fill(boxX + 1, y + 1, boxX + BOX_SIZE - 1, y + BOX_SIZE - 1, COLOR_HOVERED);
        } else {
            setFocused(false);
        }

        guiGraphics.renderOutline(boxX, y, BOX_SIZE, BOX_SIZE, borderColor);
        if (isChecked.getAsBoolean()) {
            guiGraphics.fill(boxX + CHECK_INSET, y + CHECK_INSET, boxX + BOX_SIZE - CHECK_INSET, y + BOX_SIZE - CHECK_INSET, borderColor);
        }

        var textColor = active ? Constants.COLOR_WHITE : Constants.COLOR_GRAY;
        var textX = boxX - HORIZONTAL_SPACING - font.width(getMessage());
        guiGraphics.drawString(font, getMessage(), textX, y + 3, textColor);
    }
}
