package com.almostreliable.energymeter.client;

import com.almostreliable.energymeter.EnergyMeter;
import com.almostreliable.energymeter.block.multiblock.MultiblockType;

import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.LazyLoadedImage;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MonitorSheetSpriteSource(Identifier sheet) implements SpriteSource {

    public static final Identifier ID = EnergyMeter.getRL("monitor_sheet");
    public static final MapCodec<MonitorSheetSpriteSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Identifier.CODEC.fieldOf("sheet").forGetter(MonitorSheetSpriteSource::sheet))
            .apply(instance, MonitorSheetSpriteSource::new)
    );

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        var sheetFile = TEXTURE_ID_CONVERTER.idToFile(sheet);
        var resource = resourceManager.getResource(sheetFile);
        if (resource.isEmpty()) {
            EnergyMeter.LOGGER.warn("Missing monitor sheet: {}", sheetFile);
            return;
        }

        var types = MultiblockType.values();
        var image = new LazyLoadedImage(sheetFile, resource.get(), types.length);
        for (var type : types) {
            var spriteId = type.getSheetSpriteId(sheet);
            output.add(spriteId, new QuarterLoader(image, sheetFile, spriteId, type));
        }
    }

    @Override
    public MapCodec<MonitorSheetSpriteSource> codec() {
        return MAP_CODEC;
    }

    private record QuarterLoader(LazyLoadedImage image, Identifier sheetFile, Identifier spriteId, MultiblockType type) implements DiscardableLoader {

        private static final int MAX_QUARTERS = 6;

        @Override
        public SpriteContents get(SpriteResourceLoader loader) {
            try {
                var sheetImage = image.get();
                int width = sheetImage.getWidth();
                int height = sheetImage.getHeight();
                if (width != height || width % MAX_QUARTERS != 0) {
                    EnergyMeter.LOGGER.error(
                        "Monitor sheet {} must be square with a size divisible by {}, got {}x{}",
                        sheetFile,
                        MAX_QUARTERS,
                        width,
                        height
                    );
                    return MissingTextureAtlasSprite.create();
                }

                int quarterSize = width / 6;
                var sprite = new NativeImage(NativeImage.Format.RGBA, quarterSize * 2, quarterSize * 2, false);
                copyQuarter(sheetImage, sprite, quarterSize, false, false);
                copyQuarter(sheetImage, sprite, quarterSize, true, false);
                copyQuarter(sheetImage, sprite, quarterSize, false, true);
                copyQuarter(sheetImage, sprite, quarterSize, true, true);

                var frameSize = new FrameSize(quarterSize * 2, quarterSize * 2);
                return new SpriteContents(spriteId, frameSize, sprite);
            } catch (Exception e) {
                EnergyMeter.LOGGER.error("Failed to build monitor sprite {} from {}", spriteId, sheetFile, e);
                return MissingTextureAtlasSprite.create();
            } finally {
                image.release();
            }
        }

        private void copyQuarter(NativeImage sheetImage, NativeImage sprite, int quarterSize, boolean isRightHalf, boolean isBottomHalf) {
            int offsetX = isRightHalf ? quarterSize : 0;
            int offsetY = isBottomHalf ? quarterSize : 0;
            int sourceX = type.getSheetColumn(isRightHalf) * quarterSize * 2 + offsetX;
            int sourceY = type.getSheetRow(isBottomHalf) * quarterSize * 2 + offsetY;
            sheetImage.copyRect(sprite, sourceX, sourceY, offsetX, offsetY, quarterSize, quarterSize, false, false);
        }

        @Override
        public void discard() {
            image.release();
        }
    }
}
