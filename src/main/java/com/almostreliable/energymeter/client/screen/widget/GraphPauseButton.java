package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.core.Constants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class GraphPauseButton extends AbstractButton {

    public static final int SIZE = 9;
    private static final int ICON_INSET = 1;
    private static final int ICON_SIZE = SIZE - ICON_INSET * 2;
    private static final int PAUSE_BAR_WIDTH = 2;
    private static final int PLAY_WIDTH = (ICON_SIZE + 1) / 2;

    private final BooleanSupplier isPaused;
    private final Runnable onToggle;

    public GraphPauseButton(int x, int y, BooleanSupplier isPaused, Runnable onToggle) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.isPaused = isPaused;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onToggle.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var iconX = getX() + ICON_INSET;
        var iconY = getY() + ICON_INSET;
        var color = isHovered() ? Constants.COLOR_WHITE : Constants.COLOR_GRAY;

        if (!isPaused.getAsBoolean()) {
            var rightBarX = iconX + ICON_SIZE - PAUSE_BAR_WIDTH - 1;
            graphics.fill(iconX + 1, iconY, iconX + 1 + PAUSE_BAR_WIDTH, iconY + ICON_SIZE, color);
            graphics.fill(rightBarX, iconY, rightBarX + PAUSE_BAR_WIDTH, iconY + ICON_SIZE, color);
            return;
        }

        // triangle from multiple columns
        var playX = iconX + (ICON_SIZE - PLAY_WIDTH) / 2;
        for (var column = 0; column < PLAY_WIDTH; column++) {
            graphics.fill(playX + column, iconY + column, playX + column + 1, iconY + ICON_SIZE - column, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
