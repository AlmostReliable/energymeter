package com.almostreliable.energymeter.client;

import com.almostreliable.energymeter.block.FacingEntityBlock;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.util.NumberFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jspecify.annotations.Nullable;

public class MeterRenderer implements BlockEntityRenderer<MeterBlockEntity, MeterRenderer.State> {

    private static final int MAX_DISTANCE = 32;
    private static final float HALF = 1f / 2f;
    private static final float SCALE = 1f / 40f;
    private final Font font;

    public MeterRenderer(Context context) {
        font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
        MeterBlockEntity blockEntity, State state, float partialTick, Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, crumblingOverlay);

        // turn off display if player is too far away
        LocalPlayer player = Minecraft.getInstance().player;
        state.visible = player != null && blockEntity.getBlockPos().distSqr(player.blockPosition()) <= Math.pow(MAX_DISTANCE, 2);
        if (!state.visible) return;

        // extract display info
        state.blockState = blockEntity.getBlockState();
        NumberFormatter.FormatResult energyRateFormatted = NumberFormatter.formatEnergy(blockEntity.getEnergyRate());
        state.energy = energyRateFormatted.getEnergy();
        state.unit = energyRateFormatted.getUnitPerTick();
    }

    @Override
    public void submit(State state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!state.visible || state.blockState == null) return;

        // resolve the facing side and resolve the vector used for positioning
        Direction facing = FacingEntityBlock.getFacingDir(state.blockState);
        Direction bottom = FacingEntityBlock.getBottomDir(state.blockState);

        stack.pushPose();
        stack.translate(HALF, HALF, HALF);

        switch (facing) {
            case UP -> stack.mulPose(bottom.getRotation());
            case DOWN -> {
                stack.mulPose(bottom.getOpposite().getRotation());
                stack.mulPose(Axis.XP.rotationDegrees(180));
            }
            default -> {
                stack.mulPose(facing.getRotation());
                stack.mulPose(Axis.XP.rotationDegrees(90));
            }
        }

        stack.translate(0, SCALE, -HALF - 0.000_1);

        stack.scale(SCALE, SCALE, SCALE);

        submitText(state.energy, -font.lineHeight, stack, collector);

        stack.scale(0.8f, 0.8f, 0.8f);
        submitText(state.unit, 0, stack, collector);

        stack.popPose();
    }

    private void submitText(String text, float y, PoseStack stack, SubmitNodeCollector collector) {
        collector.submitText(
            stack,
            font.width(text) * -HALF,
            y,
            Component.literal(text).getVisualOrderText(),
            false,
            Font.DisplayMode.NORMAL,
            LightCoordsUtil.FULL_BRIGHT,
            Constants.COLOR_WHITE,
            0,
            0
        );
    }

    public static class State extends BlockEntityRenderState {

        private boolean visible;
        private @Nullable BlockState blockState;
        private String energy = "";
        private String unit = "";
    }
}
