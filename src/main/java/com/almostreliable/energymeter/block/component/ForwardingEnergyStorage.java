package com.almostreliable.energymeter.block.component;

import com.almostreliable.energymeter.block.component.IoConfig.IoSettingWithPriority;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.function.Supplier;

public final class ForwardingEnergyStorage implements IEnergyStorage {

    private final EnergyHandler energyHandler;
    private final Supplier<IoSettingWithPriority> settingSupplier;

    public ForwardingEnergyStorage(EnergyHandler energyHandler, Supplier<IoSettingWithPriority> settingSupplier) {
        this.energyHandler = energyHandler;
        this.settingSupplier = settingSupplier;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        if (!canReceive()) return 0;
        return energyHandler.forwardEnergy(amount, simulate);
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        if (!canReceive() || energyHandler.isConsumeMode()) return 0;
        return energyHandler.sumOutputs(IEnergyStorage::getEnergyStored);
    }

    @Override
    public int getMaxEnergyStored() {
        if (!canReceive()) return 0;
        if (energyHandler.isConsumeMode()) return Integer.MAX_VALUE;
        return energyHandler.sumOutputs(IEnergyStorage::getMaxEnergyStored);
    }

    @Override
    public boolean canReceive() {
        return settingSupplier.get().isInput();
    }

    @Override
    public boolean canExtract() {
        return false;
    }
}
