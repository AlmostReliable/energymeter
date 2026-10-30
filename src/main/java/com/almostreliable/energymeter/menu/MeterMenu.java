package com.almostreliable.energymeter.menu;

import com.almostreliable.energymeter.block.component.GraphHandler;
import com.almostreliable.energymeter.block.component.IoConfig;
import com.almostreliable.energymeter.block.component.IoConfig.IoSettingWithPriority;
import com.almostreliable.energymeter.block.component.RedstoneHandler;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.ConnectionStatus;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.MeasureMode;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;
import com.almostreliable.energymeter.core.Registration;
import com.almostreliable.energymeter.network.menu.handler.BooleanDataHandler;
import com.almostreliable.energymeter.network.menu.handler.DelegateDataHandler;
import com.almostreliable.energymeter.network.menu.handler.EnumDataHandler;
import com.almostreliable.energymeter.network.menu.handler.FloatDataHandler;
import com.almostreliable.energymeter.network.menu.handler.IntegerDataHandler;
import com.almostreliable.energymeter.network.menu.handler.LongDataHandler;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MeterMenu extends SynchronizedContainerMenu<MeterBlockEntity> {

    private final IoConfig ioConfig = new IoConfig();
    private final GraphHandler graphHandler = new GraphHandler();
    private final RedstoneHandler redstoneHandler = new RedstoneHandler(() -> {});
    private float graphProgress;
    private boolean graphPaused;
    private TransferMode transferMode = TransferMode.SPLIT;
    private MeasureMode measureMode = MeasureMode.INSTANT;
    private int measureInterval;
    private int zeroTolerance;
    private long transferLimit;
    private long totalEnergy;
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    public MeterMenu(int wid, Inventory playerInventory, MeterBlockEntity blockEntity) {
        super(Registration.METER_MENU.get(), wid, playerInventory, blockEntity);
    }

    @Override
    public void setupDataHandlers() {
        menuSynchronizer.addDataHandler(new DelegateDataHandler(blockEntity.getIoConfig(), () -> ioConfig));
        menuSynchronizer.addDataHandler(new DelegateDataHandler(blockEntity.getRedstoneHandler(), () -> redstoneHandler));
        menuSynchronizer.addDataHandler(new DelegateDataHandler(blockEntity.getGraphHandler(), () -> graphHandler));
        menuSynchronizer.addDataHandler(new FloatDataHandler(blockEntity.getGraphHandler()::getProgress, v -> this.graphProgress = v));
        menuSynchronizer.addDataHandler(new BooleanDataHandler(blockEntity.getGraphHandler()::isPaused, v -> this.graphPaused = v));
        menuSynchronizer.addDataHandler(new EnumDataHandler<>(
            blockEntity::getTransferMode,
            v -> this.transferMode = v,
            TransferMode.values()
        ));
        menuSynchronizer.addDataHandler(new EnumDataHandler<>(
            blockEntity::getMeasureMode,
            v -> this.measureMode = v,
            MeasureMode.values()
        ));
        menuSynchronizer.addDataHandler(new IntegerDataHandler(blockEntity::getMeasureInterval, v -> this.measureInterval = v));
        menuSynchronizer.addDataHandler(new IntegerDataHandler(blockEntity::getZeroTolerance, v -> this.zeroTolerance = v));
        menuSynchronizer.addDataHandler(new LongDataHandler(blockEntity::getTransferLimit, v -> this.transferLimit = v));
        menuSynchronizer.addDataHandler(new LongDataHandler(blockEntity::getTotalEnergy, v -> this.totalEnergy = v));
        menuSynchronizer.addDataHandler(new EnumDataHandler<>(
            blockEntity::getConnectionStatus,
            v -> this.connectionStatus = v,
            ConnectionStatus.values()
        ));
    }

    public BlockState getBlockState() {
        return blockEntity.getBlockState();
    }

    // already synchronized through the block entity for displaying in the BER
    public double getEnergyRate() {
        return blockEntity.getEnergyRate();
    }

    // region syncing client getters
    @OnlyIn(Dist.CLIENT)
    public IoSettingWithPriority getIoSetting(Direction direction) {
        return ioConfig.getSetting(direction);
    }

    @OnlyIn(Dist.CLIENT)
    public GraphHandler getGraphHandler() {
        return graphHandler;
    }

    @OnlyIn(Dist.CLIENT)
    public float getGraphProgress() {
        return graphProgress;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean isGraphPaused() {
        return graphPaused;
    }

    @OnlyIn(Dist.CLIENT)
    public TransferMode getTransferMode() {
        return transferMode;
    }

    @OnlyIn(Dist.CLIENT)
    public MeasureMode getMeasureMode() {
        return measureMode;
    }

    @OnlyIn(Dist.CLIENT)
    public int getMeasureInterval() {
        return measureInterval;
    }

    @OnlyIn(Dist.CLIENT)
    public int getZeroTolerance() {
        return zeroTolerance;
    }

    @OnlyIn(Dist.CLIENT)
    public long getTransferLimit() {
        return transferLimit;
    }

    @OnlyIn(Dist.CLIENT)
    public long getTotalEnergy() {
        return totalEnergy;
    }

    @OnlyIn(Dist.CLIENT)
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public RedstoneHandler getRedstoneHandler() {
        return redstoneHandler;
    }
    // endregion
}
