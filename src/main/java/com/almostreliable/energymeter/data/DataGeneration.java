package com.almostreliable.energymeter.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class DataGeneration {

    private DataGeneration() {}

    public static void init(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        var registryAccess = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new EnergyMeterLang(output));
        generator.addProvider(event.includeClient(), new EnergyMeterModels(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new EnergyMeterAtlases(output, registryAccess, existingFileHelper));

        generator.addProvider(event.includeServer(), new EnergyMeterLoot(output, registryAccess));
        generator.addProvider(event.includeServer(), new EnergyMeterRecipes(output, registryAccess));
        generator.addProvider(event.includeServer(), new EnergyMeterTags(output, registryAccess, existingFileHelper));
    }
}
