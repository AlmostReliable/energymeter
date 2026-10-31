package com.almostreliable.energymeter.data;

import com.almostreliable.energymeter.ModConstants;
import com.almostreliable.energymeter.block.FacingEntityBlock;
import com.almostreliable.energymeter.block.MonitorBlock;
import com.almostreliable.energymeter.block.multiblock.MultiblockType;
import com.almostreliable.energymeter.core.Registration;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.ModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import org.jetbrains.annotations.Nullable;

class EnergyMeterModels extends BlockStateProvider {

    EnergyMeterModels(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ModConstants.MOD_ID, existingFileHelper);

        // sprites are generated from the monitor sheets at atlas load
        for (var type : MultiblockType.values()) {
            existingFileHelper.trackGenerated(type.getFrontSpriteId(), ModelProvider.TEXTURE);
            existingFileHelper.trackGenerated(type.getFrameSpriteId(), ModelProvider.TEXTURE);
        }
    }

    @Override
    protected void registerStatesAndModels() {
        energyMeter();
        externalMonitor();
    }

    private void energyMeter() {
        var block = Registration.METER_BLOCK;
        var frameSprite = MultiblockType.NORMAL.getFrameSpriteId();
        var frontSprite = MultiblockType.NORMAL.getFrontSpriteId();
        var model = models().orientable(block.getId().getPath(), frameSprite, frontSprite, frameSprite);

        getVariantBuilder(block.get()).forAllStatesExcept(
            state -> {
                Direction facing = FacingEntityBlock.getFacingDir(state);
                return ConfiguredModel.builder()
                    .modelFile(model)
                    .rotationX(getHorizontalRotation(facing))
                    .rotationY(getVerticalRotation(facing, null))
                    .build();
            },
            FacingEntityBlock.BOTTOM
        );

        itemModels().simpleBlockItem(block.get());
    }

    private void externalMonitor() {
        var block = Registration.MONITOR_BLOCK;

        for (var type : MultiblockType.values()) {
            monitorModel(type);
        }

        getVariantBuilder(block.get()).forAllStatesExcept(
            state -> {
                var type = state.getValue(MonitorBlock.TYPE);
                var facing = FacingEntityBlock.getFacingDir(state);
                var bottom = FacingEntityBlock.getBottomDir(state);

                return ConfiguredModel.builder()
                    .modelFile(new ModelFile.UncheckedModelFile(type.getModelId()))
                    .rotationX(getHorizontalRotation(facing))
                    .rotationY(getVerticalRotation(facing, bottom))
                    .build();
            },
            MonitorBlock.CONTROLLER, MonitorBlock.HORIZONTAL, MonitorBlock.VERTICAL
        );

        itemModels().withExistingParent(block.getId().getPath(), MultiblockType.NORMAL.getModelId());
    }

    private void monitorModel(MultiblockType type) {
        var frameSprite = type.getFrameSpriteId();
        var upDownSprite = type.getUpDownFaceType().getFrameSpriteId();
        var leftRightSprite = type.getLeftRightFaceType().getFrameSpriteId();

        models().getBuilder(type.getModelId().getPath())
            .parent(new ModelFile.UncheckedModelFile("block/block"))
            .texture("particle", frameSprite)
            .texture("front", type.getFrontSpriteId())
            .texture("back", frameSprite)
            .texture("up", upDownSprite)
            .texture("down", upDownSprite)
            .texture("east", leftRightSprite)
            .texture("west", leftRightSprite)
            .element()
            .from(0, 0, 0)
            .to(16, 16, 16)
            .allFaces((direction, face) -> face.cullface(direction))
            .face(Direction.NORTH).texture("#front").end()
            .face(Direction.SOUTH).texture("#back").uvs(16, 0, 0, 16).end()
            .face(Direction.UP).texture("#up").uvs(16, 0, 0, 16).end()
            .face(Direction.DOWN).texture("#down").uvs(16, 16, 0, 0).end()
            .face(Direction.EAST).texture("#east").end()
            .face(Direction.WEST).texture("#west").end()
            .end();
    }

    private int getVerticalRotation(Direction facing, @Nullable Direction bottom) {
        return switch (bottom != null && facing.getAxis().isVertical() ? bottom : facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private int getHorizontalRotation(Direction facing) {
        return switch (facing) {
            case UP -> 270;
            case DOWN -> 90;
            default -> 0;
        };
    }
}
