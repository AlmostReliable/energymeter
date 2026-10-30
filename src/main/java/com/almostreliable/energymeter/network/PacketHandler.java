package com.almostreliable.energymeter.network;

import com.almostreliable.energymeter.network.action.ClientActionRegistry;
import com.almostreliable.energymeter.network.packet.ClientActionPacket;
import com.almostreliable.energymeter.network.packet.EnergyRateUpdatePacket;
import com.almostreliable.energymeter.network.packet.MenuSyncPacket;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PacketHandler {

    private static final String PROTOCOL = "2";

    private PacketHandler() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(PacketHandler::onPacketRegistration);
        ClientActionRegistry.init();
    }

    private static void onPacketRegistration(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);

        // server to client
        registrar.playToClient(
            MenuSyncPacket.TYPE,
            MenuSyncPacket.STREAM_CODEC,
            wrapHandler(MenuSyncPacket::handle)
        );
        registrar.playToClient(
            EnergyRateUpdatePacket.TYPE,
            EnergyRateUpdatePacket.STREAM_CODEC,
            wrapHandler(EnergyRateUpdatePacket::handle)
        );

        // client to server
        registrar.playToServer(
            ClientActionPacket.TYPE,
            ClientActionPacket.STREAM_CODEC,
            wrapHandler(ClientActionPacket::handle)
        );
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> wrapHandler(IPayloadHandler<T> handler) {
        return (payload, context) -> context.enqueueWork(() -> handler.handle(payload, context));
    }
}
