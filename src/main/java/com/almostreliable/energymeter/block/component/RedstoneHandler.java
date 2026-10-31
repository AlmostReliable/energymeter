package com.almostreliable.energymeter.block.component;

import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.network.menu.DataHandler;
import com.almostreliable.energymeter.util.EnumExtension;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.redstone.Redstone;
import net.neoforged.neoforge.common.util.INBTSerializable;

import org.jetbrains.annotations.UnknownNullability;

public class RedstoneHandler implements INBTSerializable<CompoundTag>, DataHandler {

    public static final long DEFAULT_UPPER_BOUND = 100_000;
    private static final double ROUNDING_TOLERANCE = 1e-9;

    private final Runnable changeListener;

    // settings
    private RedstoneFunction function = RedstoneFunction.OFF;
    private RedstoneScale scale = RedstoneScale.LINEAR;
    private long lowerBound;
    private long upperBound = DEFAULT_UPPER_BOUND;
    private boolean inverted;

    // state
    private boolean thresholdActive;
    private int inputSignal;
    private int outputSignal;
    private boolean inputDirty = true;
    private boolean changed;

    public RedstoneHandler(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    @UnknownNullability
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putString(Constants.FUNCTION_ID, function.name());
        tag.putString(Constants.SCALE_ID, scale.name());
        tag.putLong(Constants.LOWER_BOUND_ID, lowerBound);
        tag.putLong(Constants.UPPER_BOUND_ID, upperBound);
        tag.putBoolean(Constants.INVERTED_ID, inverted);
        tag.putBoolean(Constants.THRESHOLD_ACTIVE_ID, thresholdActive);
        tag.putInt(Constants.OUTPUT_SIGNAL_ID, outputSignal);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains(Constants.FUNCTION_ID)) function = RedstoneFunction.valueOf(tag.getString(Constants.FUNCTION_ID));
        if (tag.contains(Constants.SCALE_ID)) scale = RedstoneScale.valueOf(tag.getString(Constants.SCALE_ID));
        if (tag.contains(Constants.LOWER_BOUND_ID)) lowerBound = tag.getLong(Constants.LOWER_BOUND_ID);
        if (tag.contains(Constants.UPPER_BOUND_ID)) upperBound = tag.getLong(Constants.UPPER_BOUND_ID);
        if (tag.contains(Constants.INVERTED_ID)) inverted = tag.getBoolean(Constants.INVERTED_ID);
        if (tag.contains(Constants.THRESHOLD_ACTIVE_ID)) thresholdActive = tag.getBoolean(Constants.THRESHOLD_ACTIVE_ID);
        if (tag.contains(Constants.OUTPUT_SIGNAL_ID)) outputSignal = tag.getInt(Constants.OUTPUT_SIGNAL_ID);
    }

    public boolean refreshOutput(double energyRate) {
        var signal = switch (function) {
            case OFF, CONTROL -> 0;
            case ANALOG -> calculateAnalogSignal(energyRate);
            case THRESHOLD -> calculateThresholdSignal(energyRate);
        };

        var isInverted = inverted && function.emitsSignal();
        return setSignal(isInverted ? Redstone.SIGNAL_MAX - signal : signal);
    }

    public void refreshInput(int signal) {
        inputDirty = false;
        if (inputSignal == signal) return;

        inputSignal = signal;
        changed = true;
    }

    public boolean needsInputRefresh() {
        return inputDirty && function == RedstoneFunction.CONTROL;
    }

    public void markInputDirty() {
        inputDirty = true;
    }

    public boolean isTransferBlocked() {
        if (function != RedstoneFunction.CONTROL) return false;

        var isPowered = inputSignal > 0;
        return isPowered != inverted;
    }

    private boolean setSignal(int signal) {
        if (outputSignal == signal) return false;
        outputSignal = signal;
        changed = true;
        return true;
    }

    private int calculateAnalogSignal(double energyRate) {
        // logarithm of zero is undefined, clamp lower bound to 1
        var effectiveLowerBound = scale == RedstoneScale.LOGARITHMIC ? Math.max(lowerBound, 1) : lowerBound;

        if (effectiveLowerBound >= upperBound) {
            return energyRate >= upperBound ? Redstone.SIGNAL_MAX : 0;
        }
        if (energyRate < effectiveLowerBound) return 0;

        var signalModifier = calculateAnalogSignalModifier(energyRate, effectiveLowerBound);
        var signalLevel = signalModifier * Redstone.SIGNAL_MAX;
        var signal = (int) Math.floor(signalLevel + ROUNDING_TOLERANCE); // ignore floating point errors for rates on level boundaries

        return Math.min(signal, Redstone.SIGNAL_MAX);
    }

    private double calculateAnalogSignalModifier(double energyRate, long lowerBound) {
        return switch (scale) {
            case LINEAR -> {
                var rateAboveLowerBound = energyRate - lowerBound;
                var rangeSize = upperBound - lowerBound;
                yield rateAboveLowerBound / rangeSize;
            }
            case LOGARITHMIC -> {
                var decadesAboveLowerBound = Math.log10(energyRate / lowerBound);
                var decadesInRange = Math.log10((double) upperBound / lowerBound);
                yield decadesAboveLowerBound / decadesInRange;
            }
        };
    }

    private int calculateThresholdSignal(double energyRate) {
        if (energyRate >= upperBound) {
            thresholdActive = true;
        } else if (energyRate < lowerBound || lowerBound >= upperBound) {
            thresholdActive = false;
        }

        return thresholdActive ? Redstone.SIGNAL_MAX : 0;
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(function.ordinal());
        buffer.writeByte(scale.ordinal());
        buffer.writeVarLong(lowerBound);
        buffer.writeVarLong(upperBound);
        buffer.writeBoolean(inverted);
        buffer.writeByte(inputSignal);
        buffer.writeByte(outputSignal);
        changed = false;
    }

    @Override
    public void decode(FriendlyByteBuf buffer) {
        function = RedstoneFunction.values()[buffer.readByte()];
        scale = RedstoneScale.values()[buffer.readByte()];
        lowerBound = buffer.readVarLong();
        upperBound = buffer.readVarLong();
        inverted = buffer.readBoolean();
        inputSignal = buffer.readByte();
        outputSignal = buffer.readByte();
    }

    @Override
    public boolean hasChanged() {
        return changed;
    }

    private void setChanged() {
        changed = true;
        changeListener.run();
    }

    public RedstoneFunction getFunction() {
        return function;
    }

    public void setFunction(RedstoneFunction function) {
        this.function = function;
        setChanged();
    }

    public long getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(long lowerBound) {
        this.lowerBound = Math.max(lowerBound, 0);
        setChanged();
    }

    public long getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(long upperBound) {
        this.upperBound = Math.max(upperBound, 0);
        setChanged();
    }

    public RedstoneScale getScale() {
        return scale;
    }

    public void setScale(RedstoneScale scale) {
        this.scale = scale;
        setChanged();
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
        setChanged();
    }

    public int getOutputSignal() {
        return outputSignal;
    }

    public int getInputSignal() {
        return inputSignal;
    }

    public enum RedstoneFunction {
        OFF, // no signal, redstone does not connect
        ANALOG, // maps the rate between the bounds to a signal strength
        THRESHOLD, // full signal from the upper bound until the rate drops below the lower bound
        CONTROL; // reads a signal to block the transfer, emits nothing

        public boolean emitsSignal() {
            return this == ANALOG || this == THRESHOLD;
        }
    }

    public enum RedstoneScale implements EnumExtension<RedstoneScale> {
        LINEAR,
        LOGARITHMIC
    }
}
