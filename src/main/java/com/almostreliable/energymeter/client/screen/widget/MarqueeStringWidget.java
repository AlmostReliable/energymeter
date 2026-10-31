package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.client.screen.widget.base.LayoutPositionedWidget;
import com.almostreliable.energymeter.core.Constants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class MarqueeStringWidget extends LayoutPositionedWidget {

    private static final double PERIOD_PER_SCROLLED_PIXEL = 0.5;
    private static final double MIN_SCROLL_PERIOD = 3.0;

    private int color = Constants.COLOR_WHITE;
    private float alignX;

    public MarqueeStringWidget(int width, int height, Component message) {
        super(width, height, message);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        // do not play click sound
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Component text = getMessage();

        int widgetWidth = getWidth();
        int textWidth = font.width(text);
        int x = getX();
        int y = getY() + (getHeight() - font.lineHeight) / 2;

        if (textWidth <= widgetWidth) {
            int alignedX = x + Math.round(alignX * (widgetWidth - textWidth));
            graphics.text(font, text, alignedX, y, color, false);
            return;
        }

        int overflow = textWidth - widgetWidth;
        double time = System.nanoTime() / 1_000_000_000.0;
        double period = Math.max(overflow * PERIOD_PER_SCROLLED_PIXEL, MIN_SCROLL_PERIOD);
        double phase = Math.sin((Math.PI / 2) * Math.cos(Math.PI * 2 * time / period)) / 2 + 0.5;

        int scrollOffset = (int) Mth.lerp(phase, 0, overflow);

        graphics.enableScissor(x, getY() - 2, x + widgetWidth, getY() + getHeight() + 2);
        graphics.text(font, text, x - scrollOffset, y, color, false);
        graphics.disableScissor();
    }

    public MarqueeStringWidget alignLeft() {
        this.alignX = 0.0f;
        return this;
    }

    public MarqueeStringWidget alignCenter() {
        this.alignX = 0.5f;
        return this;
    }

    public MarqueeStringWidget alignRight() {
        this.alignX = 1.0f;
        return this;
    }

    public void setColor(int color) {
        this.color = color;
    }
}
