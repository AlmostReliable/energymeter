package com.almostreliable.energymeter.compat.cct;

import com.almostreliable.energymeter.ModConstants;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.compat.MeterObserver;

import net.minecraft.core.Direction;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unused", "FinalMethod"})
public class MeterPeripheral implements IPeripheral, MeterObserver {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final String TRANSFER_MODE_ID = "transfer_mode";
    private static final String MEASURE_MODE_ID = "measure_mode";
    private static final String MEASURE_INTERVAL_ID = "measure_interval";
    private static final String ZERO_TOLERANCE_ID = "zero_tolerance";
    private static final String TRANSFER_LIMIT_ID = "transfer_limit";
    private static final String EFFECTIVE_TRANSFER_LIMIT_ID = "effective_transfer_limit";
    private static final String ENERGY_RATE_ID = "energy_rate";
    private static final String TOTAL_ENERGY_ID = "total_energy";
    private static final String CONNECTION_STATUS_ID = "connection_status";
    private static final String HAS_INPUT_ID = "has_input";
    private static final String HAS_OUTPUT_ID = "has_output";
    private static final String IO_CONFIG_ID = "io_config";
    private static final String REDSTONE_ID = "redstone";
    private static final String REDSTONE_FUNCTION_ID = "function";
    private static final String REDSTONE_LOWER_BOUND_ID = "lower_bound";
    private static final String REDSTONE_UPPER_BOUND_ID = "upper_bound";
    private static final String REDSTONE_SCALE_ID = "scale";
    private static final String REDSTONE_CONTROL_BEHAVIOR_ID = "control_behavior";
    private static final String REDSTONE_INVERTED_ID = "inverted";
    private static final String REDSTONE_OUTPUT_SIGNAL_ID = "output_signal";
    private static final String REDSTONE_INPUT_SIGNAL_ID = "input_signal";

    private final MeterBlockEntity entity;
    private final Set<IComputerAccess> computers = new HashSet<>();

    private Map<String, Object> lastData = Map.of();

    MeterPeripheral(MeterBlockEntity entity) {
        this.entity = entity;
    }

    @Override
    public String getType() {
        return ModConstants.MOD_ID;
    }

    @Override
    public synchronized void attach(IComputerAccess computer) {
        if (computers.add(computer) && computers.size() == 1) {
            entity.subscribeObserver(this);
        }
        lastData = createData();
    }

    @Override
    public synchronized void detach(IComputerAccess computer) {
        if (computers.remove(computer) && computers.isEmpty()) {
            entity.unsubscribeObserver(this);
        }
    }

    @Override
    public void onChange(MeterBlockEntity entity) {
        if (!this.entity.equals(entity)) return;
        var data = createData();
        lastData = data;
        queueEvent("energymeter_changed", data);
    }

    @Override
    public void onRemove(MeterBlockEntity entity) {
        if (!this.entity.equals(entity)) return;
        lastData = Map.of();
        queueEvent("energymeter_removed");
    }

    @SuppressWarnings("java:S1201")
    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof MeterPeripheral && entity.equals(((MeterPeripheral) other).entity);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getTransferMode() {
        return MethodResult.of(lastData.get(TRANSFER_MODE_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getMeasureMode() {
        return MethodResult.of(lastData.get(MEASURE_MODE_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getMeasureInterval() {
        return MethodResult.of(lastData.get(MEASURE_INTERVAL_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getZeroTolerance() {
        return MethodResult.of(lastData.get(ZERO_TOLERANCE_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getTransferLimit() {
        return MethodResult.of(lastData.get(TRANSFER_LIMIT_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getEffectiveTransferLimit() {
        return MethodResult.of(lastData.get(EFFECTIVE_TRANSFER_LIMIT_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getEnergyRate() {
        return MethodResult.of(lastData.get(ENERGY_RATE_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getTotalEnergy() {
        return MethodResult.of(lastData.get(TOTAL_ENERGY_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getConnectionStatus() {
        return MethodResult.of(lastData.get(CONNECTION_STATUS_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult hasInput() {
        return MethodResult.of(lastData.get(HAS_INPUT_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult hasOutput() {
        return MethodResult.of(lastData.get(HAS_OUTPUT_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getIoConfig() {
        return MethodResult.of(lastData.get(IO_CONFIG_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getRedstoneConfig() {
        return MethodResult.of(lastData.get(REDSTONE_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getRedstoneOutput() {
        return MethodResult.of(getRedstoneValue(REDSTONE_OUTPUT_SIGNAL_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getRedstoneInput() {
        return MethodResult.of(getRedstoneValue(REDSTONE_INPUT_SIGNAL_ID));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getData() {
        return MethodResult.of(lastData);
    }

    private Map<String, Object> createData() {
        Map<String, Object> data = new HashMap<>();
        data.put(TRANSFER_MODE_ID, entity.getTransferMode().name());
        data.put(MEASURE_MODE_ID, entity.getMeasureMode().name());
        data.put(MEASURE_INTERVAL_ID, entity.getMeasureInterval());
        data.put(ZERO_TOLERANCE_ID, entity.getZeroTolerance());
        data.put(TRANSFER_LIMIT_ID, entity.getTransferLimit());
        data.put(EFFECTIVE_TRANSFER_LIMIT_ID, createEffectiveTransferLimit());
        data.put(ENERGY_RATE_ID, entity.getEnergyRate());
        data.put(TOTAL_ENERGY_ID, entity.getTotalEnergy());
        data.put(CONNECTION_STATUS_ID, entity.getConnectionStatus().name());
        data.put(HAS_INPUT_ID, entity.getIoConfig().hasInput());
        data.put(HAS_OUTPUT_ID, entity.getIoConfig().hasOutput());
        data.put(IO_CONFIG_ID, createIoConfigStringMap());
        data.put(REDSTONE_ID, createRedstoneMap());
        return data;
    }

    private double createEffectiveTransferLimit() {
        var effectiveTransferLimit = entity.getEffectiveTransferLimit();
        // unlimited maps to math.huge so scripts can compare against it
        return effectiveTransferLimit == Long.MAX_VALUE ? Double.POSITIVE_INFINITY : effectiveTransferLimit;
    }

    private Map<String, Object> createRedstoneMap() {
        var redstoneHandler = entity.getRedstoneHandler();
        Map<String, Object> redstoneMap = new HashMap<>();
        redstoneMap.put(REDSTONE_FUNCTION_ID, redstoneHandler.getFunction().name());
        redstoneMap.put(REDSTONE_LOWER_BOUND_ID, redstoneHandler.getLowerBound());
        redstoneMap.put(REDSTONE_UPPER_BOUND_ID, redstoneHandler.getUpperBound());
        redstoneMap.put(REDSTONE_SCALE_ID, redstoneHandler.getScale().name());
        redstoneMap.put(REDSTONE_CONTROL_BEHAVIOR_ID, redstoneHandler.getControlBehavior().name());
        redstoneMap.put(REDSTONE_INVERTED_ID, redstoneHandler.isInverted());
        redstoneMap.put(REDSTONE_OUTPUT_SIGNAL_ID, redstoneHandler.getOutputSignal());
        redstoneMap.put(REDSTONE_INPUT_SIGNAL_ID, redstoneHandler.getInputSignal());
        return redstoneMap;
    }

    private @Nullable Object getRedstoneValue(String key) {
        if (!(lastData.get(REDSTONE_ID) instanceof Map<?, ?> redstoneMap)) return null;
        return redstoneMap.get(key);
    }

    private synchronized void queueEvent(String event, Object... arguments) {
        for (var computer : computers) {
            computer.queueEvent(event, arguments);
        }
    }

    private Map<String, String> createIoConfigStringMap() {
        Map<String, String> stringMap = new HashMap<>();
        for (var direction : DIRECTIONS) {
            var setting = entity.getIoConfig().getSetting(direction);
            stringMap.put(direction.name(), setting.setting().name());
        }
        return stringMap;
    }

    private static Direction parseDirection(String side) throws LuaException {
        Direction direction = Direction.byName(side.toUpperCase(Locale.ROOT));
        if (direction == null) {
            throw new LuaException("Invalid side: " + side);
        }
        return direction;
    }
}
