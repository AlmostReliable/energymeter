package com.almostreliable.energymeter.client.screen.widget;

import com.almostreliable.energymeter.EnergyMeter;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.data.EnergyMeterLang;
import com.almostreliable.energymeter.util.TooltipBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class GuideButton extends AbstractButton {

    private static final int SIZE = 16;
    private static final Component TOOLTIP = EnergyMeter.isModLoaded(Constants.GUIDE_ME) ?
        EnergyMeterLang.BUTTON_GUIDE_OPEN_TOOLTIP.get() :
        EnergyMeterLang.BUTTON_GUIDE_MISSING_TOOLTIP.get();

    public GuideButton(int x, int y) {
        super(x, y, SIZE, SIZE, Component.literal("?"));
        setTooltip(TooltipBuilder.create().literal(TOOLTIP).build());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        graphics.text(
            font,
            getMessage(),
            getX() + (SIZE - font.width(getMessage())) / 2,
            getY() + (SIZE - font.lineHeight) / 2,
            Constants.COLOR_WHITE,
            false
        );
    }

    @Override
    public void onPress(InputWithModifiers input) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.connection.sendCommand(Constants.GUIDE_ME + "c energymeter:guide open energymeter:interface.md");
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
