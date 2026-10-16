package com.almostreliable.energymeter.block.component;

import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.google.common.primitives.Ints;

import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class EnergyHandler {

    private final EnergyHandlerHost host;
    private final Map<Direction, ForwardingEnergyStorage> forwardingEnergyStorage = new EnumMap<>(Direction.class);
    private final Map<Direction, BlockCapabilityCache<IEnergyStorage, Direction>> outputCache = new EnumMap<>(Direction.class);

    private long energyPerTick; // tracks energy per tick from all sources to apply the transfer limit
    private long energyPerInterval;
    private double lastIntervalAverage;
    private int ticksWithoutEnergy;
    private boolean delegating; // set while forwarding to track loops

    public EnergyHandler(EnergyHandlerHost host) {
        this.host = host;

        for (Direction direction : Direction.values()) {
            forwardingEnergyStorage.put(direction, new ForwardingEnergyStorage(this, () -> host.getIoConfig().getSetting(direction)));
        }
    }

    public IEnergyStorage getEnergyStorage(Direction direction) {
        return forwardingEnergyStorage.get(direction);
    }

    public MeasuredEnergy calculateAndRestartCycle(boolean factorInLast) {
        int interval = host.getMeasureInterval();

        double average;
        if (factorInLast) {
            average = (energyPerInterval + lastIntervalAverage) / (interval + 1);
        } else {
            average = (double) energyPerInterval / interval;
        }

        lastIntervalAverage = average;
        var result = new MeasuredEnergy(energyPerInterval, average);
        energyPerInterval = 0;
        energyPerTick = 0;
        ticksWithoutEnergy = 0;

        return result;
    }

    public void clearOutputCacheAndReset() {
        outputCache.clear();
        energyPerInterval = 0;
        energyPerTick = 0;
        ticksWithoutEnergy = 0;
    }

    public void clearOutputCache(Direction direction) {
        outputCache.remove(direction);
    }

    public int forwardEnergy(int amount, boolean simulate) {
        if (amount <= 0 || delegating) return 0;

        delegating = true;
        try {
            return forwardEnergyToOutputs(amount, simulate);
        } finally {
            delegating = false;
        }
    }

    private int forwardEnergyToOutputs(int amount, boolean simulate) {
        if (host.getTransferMode() == TransferMode.CONSUME) {
            if (!simulate) energyPerInterval += amount;
            return amount;
        }

        var energyToForward = amount;
        var transferLimit = host.getTransferLimit();
        if (transferLimit > 0) {
            long remainingLimit = transferLimit - energyPerTick;
            if (remainingLimit <= 0) return 0;
            energyToForward = Math.min(energyToForward, Ints.saturatedCast(remainingLimit));
        }

        MaxEnergyPerOutputResult maxEnergyPerOutputResult = calculateMaxEnergyPerOutput(energyToForward);
        var maxEnergyPerOutput = maxEnergyPerOutputResult.maxEnergyPerOutput;
        long maxEnergyPerOutputSum = maxEnergyPerOutputResult.maxEnergyPerOutputSum;

        if (maxEnergyPerOutputSum <= 0) return 0;
        if (simulate) return Ints.saturatedCast(Math.min(maxEnergyPerOutputSum, energyToForward));

        if (maxEnergyPerOutputSum <= energyToForward) {
            var energyForwarded = fillOutputsWithMaxEnergy(maxEnergyPerOutput);
            energyPerInterval += energyForwarded;
            energyPerTick += energyForwarded;
            return energyForwarded;
        }

        int energyForwarded = 0;

        if (host.getTransferMode() == TransferMode.SPLIT) {
            energyForwarded = splitEnergyBetweenOutputs(maxEnergyPerOutput, energyToForward);
        } else if (host.getTransferMode() == TransferMode.TRANSFER) {
            energyForwarded = transferEnergyToOutputs(maxEnergyPerOutput, energyToForward);
        }

        energyPerInterval += energyForwarded;
        energyPerTick += energyForwarded;
        return energyForwarded;
    }

    public void tick() {
        if (energyPerTick > 0) {
            ticksWithoutEnergy = 0;
            energyPerTick = 0;
            return;
        }

        ticksWithoutEnergy++;
        if (ticksWithoutEnergy > host.getZeroTolerance()) {
            energyPerInterval = 0;
            calculateAndRestartCycle(false);
        }
    }

    private MaxEnergyPerOutputResult calculateMaxEnergyPerOutput(int maxEnergyToForward) {
        List<EnergyPerOutputEntry> maxEnergyPerOutput = new ArrayList<>();
        long maxEnergyPerOutputSum = 0;

        for (IEnergyStorage neighborEnergyStorage : getValidOutputEnergyStorages()) {
            int maxAcceptedEnergy = neighborEnergyStorage.receiveEnergy(maxEnergyToForward, true);
            if (maxAcceptedEnergy > 0) {
                maxEnergyPerOutput.add(new EnergyPerOutputEntry(neighborEnergyStorage, maxAcceptedEnergy));
                maxEnergyPerOutputSum += maxAcceptedEnergy;
            }
        }

        return new MaxEnergyPerOutputResult(maxEnergyPerOutput, maxEnergyPerOutputSum);
    }

    public Iterable<IEnergyStorage> getValidOutputEnergyStorages() {
        List<IEnergyStorage> outputEnergyStorages = new ArrayList<>();

        host.getIoConfig().forEachOutput(direction -> {
            var capabilityCache = getOrSetupCache(direction);
            IEnergyStorage outputEnergyStorage = capabilityCache.getCapability();
            if (outputEnergyStorage == null) return;

            outputEnergyStorages.add(outputEnergyStorage);
        });

        return outputEnergyStorages;
    }

    int sumOutputs(Function<IEnergyStorage, Integer> energyGetter) {
        if (delegating) return 0;

        delegating = true;
        try {
            long result = 0;

            for (IEnergyStorage neighborEnergyStorage : getValidOutputEnergyStorages()) {
                result += energyGetter.apply(neighborEnergyStorage);
            }

            return Ints.saturatedCast(result);
        } finally {
            delegating = false;
        }
    }

    private BlockCapabilityCache<IEnergyStorage, Direction> getOrSetupCache(Direction direction) {
        var cache = outputCache.get(direction);
        if (cache != null) return cache;

        if (!(host.getLevel() instanceof ServerLevel level)) {
            throw new IllegalStateException("energy handler cache accessed too early or from client");
        }

        cache = BlockCapabilityCache.create(
            Capabilities.EnergyStorage.BLOCK,
            level,
            host.getBlockPos().relative(direction),
            direction.getOpposite(),
            () -> !host.isRemoved(),
            () -> clearOutputCache(direction)
        );

        outputCache.put(direction, cache);
        return cache;
    }

    private int fillOutputsWithMaxEnergy(List<EnergyPerOutputEntry> maxEnergyPerOutput) {
        int energyForwarded = 0;
        for (EnergyPerOutputEntry outputEntry : maxEnergyPerOutput) {
            IEnergyStorage neighborEnergyStorage = outputEntry.energyStorage;
            int energyToReceiveMax = outputEntry.maxEnergy;
            energyForwarded += neighborEnergyStorage.receiveEnergy(energyToReceiveMax, false);
        }
        return energyForwarded;
    }

    private int splitEnergyBetweenOutputs(List<EnergyPerOutputEntry> outputs, int maxEnergyToForward) {
        if (outputs.isEmpty() || maxEnergyToForward <= 0) return 0;

        int energyToForward = maxEnergyToForward;
        var remainingOutputs = new ArrayList<>(outputs);
        var outputAllocations = new HashMap<EnergyPerOutputEntry, Integer>();

        while (!remainingOutputs.isEmpty() && energyToForward > 0) {
            int equalSplit = Math.max(1, energyToForward / remainingOutputs.size());

            boolean anyOutputFull = false;
            for (var it = remainingOutputs.iterator(); it.hasNext(); ) {
                var entry = it.next();
                var maxEnergy = entry.maxEnergy();

                if (maxEnergy <= equalSplit) {
                    outputAllocations.put(entry, maxEnergy);
                    energyToForward -= maxEnergy;
                    it.remove();
                    anyOutputFull = true;
                }
            }

            if (!anyOutputFull) {
                // all remaining outputs can accept the equal split
                for (var entry : remainingOutputs) {
                    int current = outputAllocations.getOrDefault(entry, 0);
                    int add = Math.min(equalSplit, entry.maxEnergy() - current);
                    outputAllocations.put(entry, current + add);
                }
                energyToForward -= equalSplit * remainingOutputs.size();
                break;
            }
        }

        // leftovers, can happen with less outputs than energy to forward
        if (energyToForward > 0) {
            Collections.shuffle(remainingOutputs); // prevent bias

            for (var entry : remainingOutputs) {
                int current = outputAllocations.getOrDefault(entry, 0);
                int capacityLeft = entry.maxEnergy() - current;

                if (capacityLeft <= 0) continue;

                int add = Math.min(capacityLeft, energyToForward);
                outputAllocations.put(entry, current + add);

                energyToForward -= add;
                if (energyToForward == 0) break;
            }
        }

        int energyForwarded = 0;
        for (var entry : outputs) {
            int amount = outputAllocations.getOrDefault(entry, 0);
            if (amount <= 0) continue;
            energyForwarded += entry.energyStorage().receiveEnergy(amount, false);
        }

        return energyForwarded;
    }

    private int transferEnergyToOutputs(List<EnergyPerOutputEntry> maxEnergyPerOutput, int maxEnergyToForward) {
        var energyToForward = maxEnergyToForward;
        var energyForwarded = 0;

        for (EnergyPerOutputEntry outputEntry : maxEnergyPerOutput) {
            if (energyToForward <= 0) return energyForwarded;
            IEnergyStorage neighborEnergyStorage = outputEntry.energyStorage;
            int energyAccepted = neighborEnergyStorage.receiveEnergy(energyToForward, false);
            energyToForward -= energyAccepted;
            energyForwarded += energyAccepted;
        }

        return energyForwarded;
    }

    boolean isConsumeMode() {
        return host.getTransferMode() == TransferMode.CONSUME;
    }

    @TestOnly
    public long getEnergyPerInterval() {
        return energyPerInterval;
    }

    @TestOnly
    public boolean hasOutputCache(Direction direction) {
        return outputCache.containsKey(direction);
    }

    public record MeasuredEnergy(long total, double average) {}

    private record EnergyPerOutputEntry(IEnergyStorage energyStorage, int maxEnergy) {}

    private record MaxEnergyPerOutputResult(List<EnergyPerOutputEntry> maxEnergyPerOutput, long maxEnergyPerOutputSum) {}
}
