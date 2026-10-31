package com.almostreliable.energymeter.block.component;

import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

public interface EnergyHandlerHost {

    @Nullable
    Level getLevel();

    BlockPos getBlockPos();

    boolean isRemoved();

    IoConfig getIoConfig();

    TransferMode getTransferMode();

    int getMeasureInterval();

    int getZeroTolerance();

    long getTransferLimit();

    boolean isTransferBlocked();
}
