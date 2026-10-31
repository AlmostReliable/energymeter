package com.almostreliable.energymeter.block.multiblock;

import com.almostreliable.energymeter.EnergyMeter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum MultiblockType implements StringRepresentable {

    NORMAL(true, true, true, true),
    MIDDLE(false, false, false, false),
    VERTICAL(true, true, false, false),
    HORIZONTAL(false, false, true, true),
    SIDE_U(false, false, true, false),
    SIDE_D(false, false, false, true),
    SIDE_L(true, false, false, false),
    SIDE_R(false, true, false, false),
    CORNER_U(true, true, true, false),
    CORNER_D(true, true, false, true),
    CORNER_L(true, false, true, true),
    CORNER_R(false, true, true, true),
    CORNER_UL(true, false, true, false),
    CORNER_UR(false, true, true, false),
    CORNER_DL(true, false, false, true),
    CORNER_DR(false, true, false, true);

    public static final ResourceLocation FRONT_SHEET = EnergyMeter.getRL("monitor/front");
    public static final ResourceLocation FRAME_SHEET = EnergyMeter.getRL("monitor/frame");
    private static final MultiblockType[] VALUES = values();

    private final boolean hasBorderLeft;
    private final boolean hasBorderRight;
    private final boolean hasBorderUp;
    private final boolean hasBorderDown;

    MultiblockType(boolean hasBorderLeft, boolean hasBorderRight, boolean hasBorderUp, boolean hasBorderDown) {
        this.hasBorderLeft = hasBorderLeft;
        this.hasBorderRight = hasBorderRight;
        this.hasBorderUp = hasBorderUp;
        this.hasBorderDown = hasBorderDown;
    }

    public static MultiblockType fromBorders(boolean hasBorderLeft, boolean hasBorderRight, boolean hasBorderUp, boolean hasBorderDown) {
        for (var value : VALUES) {
            if (value.hasBorderLeft == hasBorderLeft && value.hasBorderRight == hasBorderRight &&
                value.hasBorderUp == hasBorderUp && value.hasBorderDown == hasBorderDown) {
                return value;
            }
        }

        throw new IllegalStateException("every border combination has a multiblock type");
    }

    public MultiblockType getUpDownFaceType() {
        return fromBorders(hasBorderLeft, hasBorderRight, true, true);
    }

    public MultiblockType getLeftRightFaceType() {
        return fromBorders(true, true, hasBorderUp, hasBorderDown);
    }

    public int getSheetColumn(boolean isRightHalf) {
        if (isRightHalf) return hasBorderRight ? 2 : 1;
        return hasBorderLeft ? 0 : 1;
    }

    public int getSheetRow(boolean isBottomHalf) {
        if (isBottomHalf) return hasBorderDown ? 2 : 1;
        return hasBorderUp ? 0 : 1;
    }

    public ResourceLocation getSheetSpriteId(ResourceLocation sheet) {
        return sheet.withPath(path -> "block/" + path + "_" + getSerializedName());
    }

    public ResourceLocation getFrontSpriteId() {
        return getSheetSpriteId(FRONT_SHEET);
    }

    public ResourceLocation getFrameSpriteId() {
        return getSheetSpriteId(FRAME_SHEET);
    }

    public ResourceLocation getModelId() {
        return EnergyMeter.getRL("block/monitor/" + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
