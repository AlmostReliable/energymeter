package com.almostreliable.energymeter.data;

import com.almostreliable.energymeter.ModConstants;
import com.almostreliable.energymeter.block.FacingEntityBlock;
import com.almostreliable.energymeter.block.MonitorBlock;
import com.almostreliable.energymeter.block.multiblock.MultiblockType;
import com.almostreliable.energymeter.core.ModRegistration;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplate;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;

import com.mojang.math.Quadrant;

import org.jspecify.annotations.Nullable;

class EnergyMeterModels extends ModelProvider {

    private static final ExtendedModelTemplate MONITOR_TEMPLATE = ExtendedModelTemplateBuilder.builder()
        .parent(Identifier.withDefaultNamespace("block/block"))
        .requiredTextureSlot(TextureSlot.PARTICLE)
        .requiredTextureSlot(TextureSlot.FRONT)
        .requiredTextureSlot(TextureSlot.BACK)
        .requiredTextureSlot(TextureSlot.UP)
        .requiredTextureSlot(TextureSlot.DOWN)
        .requiredTextureSlot(TextureSlot.EAST)
        .requiredTextureSlot(TextureSlot.WEST)
        .element(element -> element
            .allFaces((direction, face) -> face.cullface(direction))
            .face(Direction.NORTH, face -> face.texture(TextureSlot.FRONT))
            .face(Direction.SOUTH, face -> face.texture(TextureSlot.BACK).uvs(16, 0, 0, 16))
            .face(Direction.UP, face -> face.texture(TextureSlot.UP).uvs(16, 0, 0, 16))
            .face(Direction.DOWN, face -> face.texture(TextureSlot.DOWN).uvs(16, 16, 0, 0))
            .face(Direction.EAST, face -> face.texture(TextureSlot.EAST))
            .face(Direction.WEST, face -> face.texture(TextureSlot.WEST)))
        .build();

    EnergyMeterModels(PackOutput output) {
        super(output, ModConstants.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        energyMeter(blockModels);
        externalMonitor(blockModels);
    }

    private static void energyMeter(BlockModelGenerators blockModels) {
        var block = ModRegistration.METER_BLOCK.get();
        var frameSprite = new Material(MultiblockType.NORMAL.getFrameSpriteId());
        var frontSprite = new Material(MultiblockType.NORMAL.getFrontSpriteId());
        var textureMapping = new TextureMapping()
            .put(TextureSlot.SIDE, frameSprite)
            .put(TextureSlot.FRONT, frontSprite)
            .put(TextureSlot.TOP, frameSprite);

        Identifier model = ModelTemplates.CUBE_ORIENTABLE.create(block, textureMapping, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
            MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                .with(PropertyDispatch.modify(FacingEntityBlock.FACING)
                    .generate(facing -> rotate(facing, null)))
        );
    }

    private static void externalMonitor(BlockModelGenerators blockModels) {
        var block = ModRegistration.MONITOR_BLOCK.get();

        for (var type : MultiblockType.values()) {
            monitorModel(blockModels, type);
        }

        blockModels.blockStateOutput.accept(
            MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(MonitorBlock.TYPE)
                    .generate(type -> BlockModelGenerators.plainVariant(type.getModelId())))
                .with(PropertyDispatch.modify(FacingEntityBlock.FACING, FacingEntityBlock.BOTTOM)
                    .generate(EnergyMeterModels::rotate))
        );

        var itemModel = ItemModelUtils.plainModel(MultiblockType.NORMAL.getModelId());
        blockModels.itemModelOutput.accept(block.asItem(), itemModel);
    }

    private static void monitorModel(BlockModelGenerators blockModels, MultiblockType type) {
        var frameSprite = new Material(type.getFrameSpriteId());
        var frontSprite = new Material(type.getFrontSpriteId());
        var upDownSprite = new Material(type.getUpDownFaceType().getFrameSpriteId());
        var leftRightSprite = new Material(type.getLeftRightFaceType().getFrameSpriteId());
        var textureMapping = new TextureMapping()
            .put(TextureSlot.PARTICLE, frameSprite)
            .put(TextureSlot.FRONT, frontSprite)
            .put(TextureSlot.BACK, frameSprite)
            .put(TextureSlot.UP, upDownSprite)
            .put(TextureSlot.DOWN, upDownSprite)
            .put(TextureSlot.EAST, leftRightSprite)
            .put(TextureSlot.WEST, leftRightSprite);

        MONITOR_TEMPLATE.create(type.getModelId(), textureMapping, blockModels.modelOutput);
    }

    private static VariantMutator rotate(Direction facing, @Nullable Direction bottom) {
        return VariantMutator.X_ROT.withValue(horizontalRotation(facing))
            .then(VariantMutator.Y_ROT.withValue(verticalRotation(facing, bottom)));
    }

    private static Quadrant verticalRotation(Direction facing, @Nullable Direction bottom) {
        return switch (bottom != null && facing.getAxis().isVertical() ? bottom : facing) {
            case EAST -> Quadrant.R90;
            case SOUTH -> Quadrant.R180;
            case WEST -> Quadrant.R270;
            default -> Quadrant.R0;
        };
    }

    private static Quadrant horizontalRotation(Direction facing) {
        return switch (facing) {
            case UP -> Quadrant.R270;
            case DOWN -> Quadrant.R90;
            default -> Quadrant.R0;
        };
    }
}
