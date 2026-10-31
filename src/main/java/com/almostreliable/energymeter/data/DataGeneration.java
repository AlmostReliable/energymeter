package com.almostreliable.energymeter.data;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class DataGeneration {

    private DataGeneration() {}

    public static void initClient(GatherDataEvent.Client event) {
        event.createProvider(EnergyMeterModels::new);
        event.createProvider(EnergyMeterAtlases::new);
        event.createProvider(EnergyMeterLang::new);
    }

    public static void initServer(GatherDataEvent.Server event) {
        event.createProvider(EnergyMeterLoot::new);
        event.createProvider(EnergyMeterRecipes.Runner::new);
        event.createProvider(EnergyMeterTags::new);
    }
}
