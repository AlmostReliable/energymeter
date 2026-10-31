package com.almostreliable.energymeter;

import com.almostreliable.energymeter.client.MeterRenderer;
import com.almostreliable.energymeter.client.MonitorSheetSpriteSource;
import com.almostreliable.energymeter.client.screen.MeterScreen;
import com.almostreliable.energymeter.client.screen.MonitorScreen;
import com.almostreliable.energymeter.core.ModRegistration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;

@Mod(value = ModConstants.MOD_ID, dist = Dist.CLIENT)
public final class EnergyMeterClient {

    public EnergyMeterClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(this::registerSpriteSources);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistration.METER_MENU.get(), MeterScreen::new);
        event.register(ModRegistration.MONITOR_MENU.get(), MonitorScreen::new);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistration.METER_BLOCK_ENTITY.get(), MeterRenderer::new);
    }

    private void registerSpriteSources(RegisterSpriteSourcesEvent event) {
        event.register(MonitorSheetSpriteSource.ID, MonitorSheetSpriteSource.MAP_CODEC);
    }
}
