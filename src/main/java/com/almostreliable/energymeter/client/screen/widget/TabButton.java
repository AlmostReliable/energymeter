package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.EnergyMeter;
import com.almostreliable.energymeter.client.screen.MeterScreen.TabType;
import com.almostreliable.energymeter.client.screen.widget.base.LayoutPositionedButton;
import com.almostreliable.energymeter.data.EnergyMeterLang;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public class TabButton extends LayoutPositionedButton {

    private static final Identifier TEXTURE = EnergyMeter.getRL("textures/gui/button/tab.png");
    private static final int TEXTURE_WIDTH = 45;
    private static final int TEXTURE_HEIGHT = 32;
    private static final int TAB_WIDTH = 25;
    public static final int TAB_HEIGHT = 19;
    private static final int ICON_WIDTH = 15;
    private static final int ICON_HEIGHT = 13;

    private final TabType tabType;
    private final boolean isSelected;
    private final Consumer<TabType> onClick;

    public TabButton(TabType tabType, TabType selected, Consumer<TabType> onClick) {
        super(TAB_WIDTH, TAB_HEIGHT);
        this.tabType = tabType;
        this.isSelected = tabType == selected;
        this.onClick = onClick;

        setTooltip(Tooltip.create(EnergyMeterLang.TAB_TYPE.get(tabType).get()));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (isSelected) return;
        onClick.accept(tabType);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // tab background
        int tabHeight = isSelected ? TAB_HEIGHT : (TAB_HEIGHT - 1);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, getX(), getY(), 0, 0, TAB_WIDTH, tabHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        // tab icon
        int uOffset = tabType.ordinal() * ICON_WIDTH;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            TEXTURE,
            getX() + 5,
            getY() + 3,
            uOffset,
            TAB_HEIGHT,
            ICON_WIDTH,
            ICON_HEIGHT,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT
        );
    }

    @Override
    public void playDownSound(SoundManager handler) {
        if (isSelected) return;
        super.playDownSound(handler);
    }
}
