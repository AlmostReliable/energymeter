package com.almostreliable.energymeter.util;

import com.almostreliable.energymeter.EnergyMeter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class TexRenderer {

    public static final int DEFAULT_TEXTURE_SIZE = 256;

    private final ResourceLocation texture;
    private final int textureWidth;
    private final int textureHeight;

    private int texX;
    private int texY;
    private int texWidth;
    private int texHeight;

    private int targetX;
    private int targetY;

    private TexRenderer(ResourceLocation texture, int textureWidth, int textureHeight) {
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public static TexRenderer of(ResourceLocation texture, int textureWidth, int textureHeight) {
        return new TexRenderer(texture, textureWidth, textureHeight);
    }

    public static TexRenderer of(ResourceLocation texture) {
        return new TexRenderer(texture, DEFAULT_TEXTURE_SIZE, DEFAULT_TEXTURE_SIZE);
    }

    public static TexRenderer button(String buttonTexture, int textureWidth, int textureHeight) {
        var texture = EnergyMeter.getRL("textures/gui/button/" + buttonTexture + ".png");
        return of(texture, textureWidth, textureHeight);
    }

    public static TexRenderer button(String buttonTexture, int textureSize) {
        return button(buttonTexture, textureSize, textureSize);
    }

    public static TexRenderer button(String buttonTexture) {
        return button(buttonTexture, DEFAULT_TEXTURE_SIZE, DEFAULT_TEXTURE_SIZE);
    }

    public TexRenderer copy() {
        return new TexRenderer(texture, textureWidth, textureHeight);
    }

    public TexRenderer tex(int x, int y, int width, int height) {
        texX = x;
        texY = y;
        texWidth = width;
        texHeight = height;
        return this;
    }

    public TexRenderer tex(int x, int y, int size) {
        tex(x, y, size, size);
        return this;
    }

    public TexRenderer target(int x, int y) {
        targetX = x;
        targetY = y;
        return this;
    }

    public void render(GuiGraphics guiGraphics) {
        guiGraphics.blit(texture, targetX, targetY, texX, texY, texWidth, texHeight, textureWidth, textureHeight);
    }
}
