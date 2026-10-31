package com.almostreliable.energymeter.block.entity;

import com.almostreliable.energymeter.block.MeterBlock;
import com.almostreliable.energymeter.block.component.EnergyHandlerHost;
import com.almostreliable.energymeter.block.component.GraphHandler;
import com.almostreliable.energymeter.block.component.IoConfig;
import com.almostreliable.energymeter.block.component.MeterEnergyHandler;
import com.almostreliable.energymeter.block.component.RedstoneHandler;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneFunction;
import com.almostreliable.energymeter.compat.MeterObserver;
import com.almostreliable.energymeter.core.Config;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.core.ModRegistration;
import com.almostreliable.energymeter.menu.MeterMenu;
import com.almostreliable.energymeter.network.packet.EnergyRateUpdatePacket;
import com.almostreliable.energymeter.util.EnumExtension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.energy.EmptyEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import org.jspecify.annotations.Nullable;

import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class MeterBlockEntity extends BlockEntity implements TickableMenuBlockEntity, EnergyHandlerHost {

    public static final int DEFAULT_INTERVAL = 5;
    private static final Direction[] DIRECTIONS = Direction.values();

    // components
    private final IoConfig ioConfig;
    private final GraphHandler graphHandler;
    private final MeterEnergyHandler energyHandler;
    private final RedstoneHandler redstoneHandler;

    // settings
    private TransferMode transferMode = TransferMode.SPLIT;
    private MeasureMode measureMode = MeasureMode.INSTANT;
    private int measureInterval = Config.COMMON.defaultInterval.getAsInt();
    private int zeroTolerance = Config.COMMON.defaultInterval.getAsInt();
    private long transferLimit;

    // tracking & display
    private final Set<MeterObserver> observers = Collections.synchronizedSet(new HashSet<>());
    private double energyRate;
    private long totalEnergy;
    private long lastEnergySyncTick;

    // status
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    public MeterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistration.METER_BLOCK_ENTITY.get(), pos, state);
        this.ioConfig = new IoConfig(this::onConnectionRelevantSettingChanged);
        this.graphHandler = new GraphHandler();
        this.energyHandler = new MeterEnergyHandler(this);
        this.redstoneHandler = new RedstoneHandler(this::onRedstoneSettingChanged);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ioConfig.serialize(output.child(Constants.SIDE_CONFIG_ID));
        redstoneHandler.serialize(output.child(Constants.REDSTONE_ID));
        output.putString(Constants.TRANSFER_MODE_ID, transferMode.name());
        output.putString(Constants.MEASURE_MODE_ID, measureMode.name());
        output.putInt(Constants.MEASURE_INTERVAL_ID, measureInterval);
        output.putInt(Constants.ZERO_TOLERANCE_ID, zeroTolerance);
        output.putLong(Constants.TRANSFER_LIMIT_ID, transferLimit);
        output.putLong(Constants.TOTAL_ENERGY_ID, totalEnergy);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child(Constants.SIDE_CONFIG_ID).ifPresent(ioConfig::deserialize);
        input.child(Constants.REDSTONE_ID).ifPresent(redstoneHandler::deserialize);
        input.getString(Constants.TRANSFER_MODE_ID).ifPresent(value -> transferMode = TransferMode.valueOf(value));
        input.getString(Constants.MEASURE_MODE_ID).ifPresent(value -> measureMode = MeasureMode.valueOf(value));
        measureInterval = input.getIntOr(Constants.MEASURE_INTERVAL_ID, measureInterval);
        zeroTolerance = input.getIntOr(Constants.ZERO_TOLERANCE_ID, zeroTolerance);
        transferLimit = input.getLongOr(Constants.TRANSFER_LIMIT_ID, transferLimit);
        totalEnergy = input.getLongOr(Constants.TOTAL_ENERGY_ID, totalEnergy);
    }

    // used to sync the latest energy rate to player entering the chunk
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        tag.putDouble(Constants.ENERGY_RATE_ID, energyRate);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        energyRate = input.getDoubleOr(Constants.ENERGY_RATE_ID, energyRate);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int wid, Inventory playerInventory, Player player) {
        return new MeterMenu(wid, playerInventory, this);
    }

    public void subscribeObserver(MeterObserver observer) {
        observers.add(observer);
    }

    public void unsubscribeObserver(MeterObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        synchronized (observers) {
            for (var observer : observers) {
                observer.onChange(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        synchronized (observers) {
            for (var observer : observers) {
                observer.onRemove(this);
            }
        }
        super.setRemoved();
    }

    @Override
    public void tick(ServerLevel level) {
        if (redstoneHandler.needsInputRefresh()) {
            refreshRedstoneInput(level);
        }

        if (!transferMode.isCorrectlyConfigured(ioConfig::hasInput, ioConfig::hasOutput)) {
            updateConnectionStatus();
            return;
        }

        graphHandler.tick(level.getGameTime(), measureInterval);
        energyHandler.tick();

        if (level.getGameTime() % measureInterval == 0) {
            onIntervalReached(level);
        }

        updateConnectionStatus();
    }

    private void onIntervalReached(ServerLevel level) {
        boolean energyRateChanged = refreshEnergyValues();

        // only sync if the value changed or every second at most (for clients without any info)
        if (energyRateChanged || level.getGameTime() - lastEnergySyncTick >= 20) {
            syncEnergyRate(level);
        }

        graphHandler.trackEnergyRate(energyRate);
    }

    @VisibleForTesting
    public boolean refreshEnergyValues() {
        var measuredEnergy = energyHandler.calculateAndRestartCycle(measureMode == MeasureMode.SMOOTHED);

        var lastEnergyRate = energyRate;
        var lastTotalEnergy = totalEnergy;
        energyRate = measuredEnergy.average();
        totalEnergy += measuredEnergy.total();

        var energyRateChanged = lastEnergyRate != energyRate;
        var statusChanged = updateConnectionStatus();

        if (!statusChanged && (energyRateChanged || lastTotalEnergy != totalEnergy)) {
            setChanged();
        }

        refreshRedstoneOutput();

        return energyRateChanged;
    }

    private void syncEnergyRate(ServerLevel level) {
        PacketDistributor.sendToPlayersTrackingChunk(
            level,
            level.getChunk(worldPosition).getPos(),
            new EnergyRateUpdatePacket(worldPosition, energyRate)
        );
        lastEnergySyncTick = level.getGameTime();
    }

    private void onConnectionRelevantSettingChanged() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        serverLevel.invalidateCapabilities(worldPosition);
        serverLevel.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        energyHandler.clearOutputCacheAndReset();
        energyRate = 0;
        syncEnergyRate(serverLevel);
        refreshRedstoneOutput();

        if (!updateConnectionStatus()) setChanged();
    }

    private void refreshRedstoneInput(ServerLevel level) {
        var frontDirection = MeterBlock.getFacingDir(getBlockState());
        var inputSignal = 0;

        for (var direction : DIRECTIONS) {
            if (direction == frontDirection) continue;

            var neighborPos = worldPosition.relative(direction);
            var neighborSignal = level.getSignal(neighborPos, direction);
            inputSignal = Math.max(inputSignal, neighborSignal);
        }

        if (redstoneHandler.refreshInput(inputSignal)) setChanged();
    }

    private void refreshRedstoneOutput() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!redstoneHandler.refreshOutput(energyRate)) return;

        serverLevel.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        setChanged();
    }

    private void onRedstoneSettingChanged() {
        var isEnabled = redstoneHandler.getFunction() != RedstoneFunction.OFF;
        var blockStateEnabled = getBlockState().getValue(MeterBlock.REDSTONE);
        if (level != null && blockStateEnabled != isEnabled) {
            var newState = getBlockState().setValue(MeterBlock.REDSTONE, isEnabled);
            level.setBlock(worldPosition, newState, Block.UPDATE_ALL);
        }

        refreshRedstoneOutput();
        setChanged();
    }

    private boolean updateConnectionStatus() {
        var newStatus = calculateConnectionStatus();
        if (connectionStatus == newStatus) return false;

        connectionStatus = newStatus;
        setChanged();

        return true;
    }

    private ConnectionStatus calculateConnectionStatus() {
        if (!transferMode.isCorrectlyConfigured(ioConfig::hasInput, ioConfig::hasOutput)) return ConnectionStatus.DISCONNECTED;
        if (isTransferBlocked()) return ConnectionStatus.BLOCKED;

        return energyRate > 0 ? transferMode.activeStatus : ConnectionStatus.IDLE;
    }

    // fall-back to clear output capability cache in case a block doesn't invalidate the capability
    public void onNeighborBlockChange(Direction direction) {
        if (ioConfig.getSetting(direction).isOutput()) {
            energyHandler.clearOutputCache(direction);
        }
    }

    @Nullable
    public EnergyHandler getEnergyCapability(@Nullable Direction direction) {
        // return empty storage on null direction because a few mod check this for cable connections
        if (direction == null) return EmptyEnergyHandler.INSTANCE;
        if (ioConfig.getSetting(direction).isDisabled()) return null;

        var level = getLevel();
        if (level instanceof ServerLevel serverLevel &&
            serverLevel.getBlockState(worldPosition.relative(direction)).is(ModRegistration.METER_BLOCK.get()) &&
            !Config.COMMON.allowMeterConnections.get()
        ) {
            return null;
        }

        return energyHandler.getEnergyStorage(direction);
    }

    @Override
    public IoConfig getIoConfig() {
        return ioConfig;
    }

    public GraphHandler getGraphHandler() {
        return graphHandler;
    }

    @TestOnly
    public MeterEnergyHandler getEnergyHandler() {
        return energyHandler;
    }

    public RedstoneHandler getRedstoneHandler() {
        return redstoneHandler;
    }

    public void toggleGraphPause() {
        graphHandler.togglePause();
        setChanged();
    }

    @Override
    public TransferMode getTransferMode() {
        return transferMode;
    }

    public void setTransferMode(TransferMode transferMode) {
        this.transferMode = transferMode;
        onConnectionRelevantSettingChanged();
    }

    public MeasureMode getMeasureMode() {
        return measureMode;
    }

    public void setMeasureMode(MeasureMode measureMode) {
        this.measureMode = measureMode;
        setChanged();
    }

    @Override
    public int getMeasureInterval() {
        return measureInterval;
    }

    public void setMeasureInterval(int measureInterval) {
        this.measureInterval = Math.max(measureInterval, DEFAULT_INTERVAL);
        graphHandler.clear();
        setChanged();
    }

    @Override
    public int getZeroTolerance() {
        return zeroTolerance;
    }

    public void setZeroTolerance(int zeroTolerance) {
        this.zeroTolerance = Math.max(zeroTolerance, DEFAULT_INTERVAL);
        setChanged();
    }

    public double getEnergyRate() {
        return energyRate;
    }

    public long getTotalEnergy() {
        return totalEnergy;
    }

    public void setTotalEnergy(long totalEnergy) {
        this.totalEnergy = totalEnergy;
        setChanged();
    }

    public long getTransferLimit() {
        return transferLimit;
    }

    public void setTransferLimit(long transferLimit) {
        this.transferLimit = Math.max(transferLimit, 0);
        setChanged();
    }

    @Override
    public long getEffectiveTransferLimit() {
        return redstoneHandler.getEffectiveTransferLimit(transferLimit, transferMode);
    }

    public boolean isTransferBlocked() {
        return getEffectiveTransferLimit() == 0;
    }

    public void setEnergyRate(double energyRate) {
        this.energyRate = energyRate;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public enum ConnectionStatus {
        DISCONNECTED, IDLE, SPLITTING, TRANSFERRING, CONSUMING, BLOCKED
    }

    public enum TransferMode implements EnumExtension<TransferMode> {

        SPLIT(true, true, ConnectionStatus.SPLITTING),
        TRANSFER(true, true, ConnectionStatus.TRANSFERRING),
        CONSUME(true, false, ConnectionStatus.CONSUMING);

        private final boolean requiresInput;
        private final boolean requiresOutput;
        private final ConnectionStatus activeStatus;

        TransferMode(boolean requiresInput, boolean requiresOutput, ConnectionStatus activeStatus) {
            this.requiresInput = requiresInput;
            this.requiresOutput = requiresOutput;
            this.activeStatus = activeStatus;
        }

        private boolean isCorrectlyConfigured(Supplier<Boolean> hasInput, Supplier<Boolean> hasOutput) {
            return (!requiresInput || hasInput.get()) && (!requiresOutput || hasOutput.get());
        }
    }

    public enum MeasureMode {
        INSTANT, SMOOTHED
    }
}
