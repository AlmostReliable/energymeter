package com.almostreliable.energymeter.data;

import com.almostreliable.energymeter.ModConstants;
import com.almostreliable.energymeter.block.multiblock.MultiblockType;
import com.almostreliable.energymeter.client.MonitorSheetSpriteSource;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.AtlasIds;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.data.SpriteSourceProvider;

import java.util.concurrent.CompletableFuture;

class EnergyMeterAtlases extends SpriteSourceProvider {

    EnergyMeterAtlases(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, ModConstants.MOD_ID);
    }

    @Override
    protected void gather() {
        atlas(AtlasIds.BLOCKS)
            .addSource(new MonitorSheetSpriteSource(MultiblockType.FRONT_SHEET))
            .addSource(new MonitorSheetSpriteSource(MultiblockType.FRAME_SHEET));
    }
}
