package com.almostreliable.energymeter.network.action;

import com.almostreliable.energymeter.EnergyMeter;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneFunction;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneScale;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.client.screen.MeterTextBoxType;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.menu.MeterMenu;
import com.almostreliable.energymeter.menu.SynchronizedContainerMenu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public final class ClientActionRegistry {

    public static final Identifier TRANSFER_MODE_ID = EnergyMeter.getRL(Constants.TRANSFER_MODE_ID);
    public static final Identifier MEASURE_MODE_ID = EnergyMeter.getRL(Constants.MEASURE_MODE_ID);
    public static final Identifier RESET_TOTAL_ID = EnergyMeter.getRL("reset_total");
    public static final Identifier TOGGLE_GRAPH_PAUSE_ID = EnergyMeter.getRL("toggle_graph_pause");
    public static final Identifier UPDATE_TEXT_ID = EnergyMeter.getRL("update_text");
    public static final Identifier REDSTONE_FUNCTION_ID = EnergyMeter.getRL("redstone_function");
    public static final Identifier REDSTONE_SCALE_ID = EnergyMeter.getRL("redstone_scale");
    public static final Identifier TOGGLE_REDSTONE_INVERTED_ID = EnergyMeter.getRL("toggle_redstone_inverted");

    private static final Map<Identifier, Decoder<?>> DECODERS = new HashMap<>();
    private static final String ID = "id";
    private static final String PAYLOAD = "payload";

    public static <T extends ClientAction<?>> void register(Identifier id, Decoder<T> decoder) {
        DECODERS.put(id, decoder);
    }

    public static CompoundTag encode(ClientAction<?> action) {
        CompoundTag payload = new CompoundTag();
        action.encode(payload);

        CompoundTag data = new CompoundTag();
        data.putString(ID, action.id().toString());
        data.put(PAYLOAD, payload);

        return data;
    }

    @SuppressWarnings("unchecked")
    public static <M extends SynchronizedContainerMenu<?>> void handle(M menu, ServerPlayer player, CompoundTag data) {
        var id = Identifier.parse(data.getStringOr(ID, ""));
        var payload = data.getCompound(PAYLOAD).orElseGet(CompoundTag::new);

        var decoder = (Decoder<ClientAction<M>>) DECODERS.get(id);
        if (decoder == null) throw new IllegalStateException("unknown client action: " + id);

        var action = decoder.decode(payload);
        action.handleServer(menu, player);
    }

    public static void init() {
        ClientActionRegistry.register(
            TRANSFER_MODE_ID,
            EnumClientAction.<MeterMenu, MeterBlockEntity.TransferMode> decoder(
                TRANSFER_MODE_ID,
                MeterBlockEntity.TransferMode.class,
                (menu, value) -> menu.getBlockEntity().setTransferMode(value)
            )
        );

        ClientActionRegistry.register(
            MEASURE_MODE_ID,
            EnumClientAction.<MeterMenu, MeterBlockEntity.MeasureMode> decoder(
                MEASURE_MODE_ID,
                MeterBlockEntity.MeasureMode.class,
                (menu, value) -> menu.getBlockEntity().setMeasureMode(value)
            )
        );

        ClientActionRegistry.register(IoSettingClientAction.ID, IoSettingClientAction::decode);
        ClientActionRegistry.register(
            UPDATE_TEXT_ID,
            TextValueClientAction.<MeterBlockEntity, MeterMenu, MeterTextBoxType> decoder(
                UPDATE_TEXT_ID,
                MeterTextBoxType.class
            )
        );

        ClientActionRegistry.register(
            RESET_TOTAL_ID,
            SimpleClientAction.<MeterMenu> decoder(RESET_TOTAL_ID, menu -> menu.getBlockEntity().setTotalEnergy(0))
        );
        ClientActionRegistry.register(
            TOGGLE_GRAPH_PAUSE_ID,
            SimpleClientAction.<MeterMenu> decoder(TOGGLE_GRAPH_PAUSE_ID, menu -> menu.getBlockEntity().toggleGraphPause())
        );

        ClientActionRegistry.register(
            REDSTONE_FUNCTION_ID,
            EnumClientAction.<MeterMenu, RedstoneFunction> decoder(
                REDSTONE_FUNCTION_ID,
                RedstoneFunction.class,
                (menu, value) -> menu.getBlockEntity().getRedstoneHandler().setFunction(value)
            )
        );
        ClientActionRegistry.register(
            REDSTONE_SCALE_ID,
            EnumClientAction.<MeterMenu, RedstoneScale> decoder(
                REDSTONE_SCALE_ID,
                RedstoneScale.class,
                (menu, value) -> menu.getBlockEntity().getRedstoneHandler().setScale(value)
            )
        );
        ClientActionRegistry.register(
            TOGGLE_REDSTONE_INVERTED_ID,
            SimpleClientAction.<MeterMenu> decoder(TOGGLE_REDSTONE_INVERTED_ID, menu -> {
                var redstoneHandler = menu.getBlockEntity().getRedstoneHandler();
                redstoneHandler.setInverted(!redstoneHandler.isInverted());
            })
        );
    }

    @FunctionalInterface
    public interface Decoder<T> {

        T decode(CompoundTag tag);
    }
}
