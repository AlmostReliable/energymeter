package com.almostreliable.energymeter.block.entity;

import com.almostreliable.energymeter.block.MeterBlock;
import com.almostreliable.energymeter.block.component.EnergyHandler;
import com.almostreliable.energymeter.block.component.EnergyHandlerHost;
import com.almostreliable.energymeter.block.component.GraphHandler;
import com.almostreliable.energymeter.block.component.IoConfig;
import com.almostreliable.energymeter.block.component.RedstoneHandler;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneFunction;
import com.almostreliable.energymeter.compat.MeterObserver;
import com.almostreliable.energymeter.core.Config;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.core.Registration;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.energy.EmptyEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static com.almostreliable.energymeter.core.Constants.MEASURE_INTERVAL_ID;
import static com.almostreliable.energymeter.core.Constants.MEASURE_MODE_ID;
import static com.almostreliable.energymeter.core.Constants.REDSTONE_ID;
import static com.almostreliable.energymeter.core.Constants.SIDE_CONFIG_ID;
import static com.almostreliable.energymeter.core.Constants.TOTAL_ENERGY_ID;
import static com.almostreliable.energymeter.core.Constants.TRANSFER_LIMIT_ID;
import static com.almostreliable.energymeter.core.Constants.TRANSFER_MODE_ID;
import static com.almostreliable.energymeter.core.Constants.ZERO_TOLERANCE_ID;

public class MeterBlockEntity extends BlockEntity implements TickableMenuBlockEntity, EnergyHandlerHost {

    public static final int DEFAULT_INTERVAL = 5;
    private static final Direction[] DIRECTIONS = Direction.values();

    // components
    private final IoConfig ioConfig;
    private final GraphHandler graphHandler;
    private final EnergyHandler energyHandler;
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
        super(Registration.METER_BLOCK_ENTITY.get(), pos, state);
        this.ioConfig = new IoConfig(this::onConnectionRelevantSettingChanged);
        this.graphHandler = new GraphHandler();
        this.energyHandler = new EnergyHandler(this);
        this.redstoneHandler = new RedstoneHandler(this::onRedstoneSettingChanged);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(SIDE_CONFIG_ID, ioConfig.serializeNBT(registries));
        tag.put(REDSTONE_ID, redstoneHandler.serializeNBT(registries));
        tag.putString(TRANSFER_MODE_ID, transferMode.name());
        tag.putString(MEASURE_MODE_ID, measureMode.name());
        tag.putInt(MEASURE_INTERVAL_ID, measureInterval);
        tag.putInt(ZERO_TOLERANCE_ID, zeroTolerance);
        tag.putLong(TRANSFER_LIMIT_ID, transferLimit);
        tag.putLong(TOTAL_ENERGY_ID, totalEnergy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(SIDE_CONFIG_ID)) ioConfig.deserializeNBT(registries, tag.getCompound(SIDE_CONFIG_ID));
        if (tag.contains(REDSTONE_ID)) redstoneHandler.deserializeNBT(registries, tag.getCompound(REDSTONE_ID));
        if (tag.contains(TRANSFER_MODE_ID)) transferMode = TransferMode.valueOf(tag.getString(TRANSFER_MODE_ID));
        if (tag.contains(MEASURE_MODE_ID)) measureMode = MeasureMode.valueOf(tag.getString(MEASURE_MODE_ID));
        if (tag.contains(MEASURE_INTERVAL_ID)) measureInterval = tag.getInt(MEASURE_INTERVAL_ID);
        if (tag.contains(ZERO_TOLERANCE_ID)) zeroTolerance = tag.getInt(ZERO_TOLERANCE_ID);
        if (tag.contains(TRANSFER_LIMIT_ID)) transferLimit = tag.getLong(TRANSFER_LIMIT_ID);
        if (tag.contains(TOTAL_ENERGY_ID)) totalEnergy = tag.getLong(TOTAL_ENERGY_ID);
    }

    // used to sync the latest energy rate to player entering the chunk
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        tag.putDouble(Constants.ENERGY_RATE_ID, energyRate);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        if (tag.contains(Constants.ENERGY_RATE_ID)) energyRate = tag.getDouble(Constants.ENERGY_RATE_ID);
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
        serverLevel.blockUpdated(worldPosition, getBlockState().getBlock());
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
    public IEnergyStorage getEnergyCapability(@Nullable Direction direction) {
        // return empty storage on null direction because a few mod check this for cable connections
        if (direction == null) return EmptyEnergyStorage.INSTANCE;
        if (ioConfig.getSetting(direction).isDisabled()) return null;

        var level = getLevel();
        if (level instanceof ServerLevel serverLevel &&
            serverLevel.getBlockState(worldPosition.relative(direction)).is(Registration.METER_BLOCK) &&
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
    public EnergyHandler getEnergyHandler() {
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

    @OnlyIn(Dist.CLIENT) // only used for syncing
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
