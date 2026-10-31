package com.almostreliable.energymeter.block.component;

import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.network.menu.DataHandler;
import com.almostreliable.energymeter.util.EnumExtension;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.redstone.Redstone;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class RedstoneHandler implements DataHandler {

    public static final long DEFAULT_UPPER_BOUND = 100_000;
    private static final double ROUNDING_TOLERANCE = 1e-9;
    private static final RedstoneFunction[] REDSTONE_FUNCTIONS = RedstoneFunction.values();
    private static final RedstoneScale[] REDSTONE_SCALES = RedstoneScale.values();
    private static final ControlBehavior[] CONTROL_BEHAVIORS = ControlBehavior.values();

    private final Runnable changeListener;

    // settings
    private RedstoneFunction function = RedstoneFunction.OFF;
    private RedstoneScale scale = RedstoneScale.LINEAR;
    private ControlBehavior controlBehavior = ControlBehavior.SWITCH;
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

    public void serialize(ValueOutput output) {
        output.putString(Constants.FUNCTION_ID, function.name());
        output.putString(Constants.SCALE_ID, scale.name());
        output.putString(Constants.CONTROL_BEHAVIOR_ID, controlBehavior.name());
        output.putLong(Constants.LOWER_BOUND_ID, lowerBound);
        output.putLong(Constants.UPPER_BOUND_ID, upperBound);
        output.putBoolean(Constants.INVERTED_ID, inverted);
        output.putBoolean(Constants.THRESHOLD_ACTIVE_ID, thresholdActive);
        output.putInt(Constants.OUTPUT_SIGNAL_ID, outputSignal);
    }

    public void deserialize(ValueInput input) {
        input.getString(Constants.FUNCTION_ID).ifPresent(value -> function = RedstoneFunction.valueOf(value));
        input.getString(Constants.SCALE_ID).ifPresent(value -> scale = RedstoneScale.valueOf(value));
        input.getString(Constants.CONTROL_BEHAVIOR_ID).ifPresent(value -> controlBehavior = ControlBehavior.valueOf(value));
        lowerBound = input.getLongOr(Constants.LOWER_BOUND_ID, lowerBound);
        upperBound = input.getLongOr(Constants.UPPER_BOUND_ID, upperBound);
        inverted = input.getBooleanOr(Constants.INVERTED_ID, inverted);
        thresholdActive = input.getBooleanOr(Constants.THRESHOLD_ACTIVE_ID, thresholdActive);
        outputSignal = input.getIntOr(Constants.OUTPUT_SIGNAL_ID, outputSignal);
    }

    public boolean refreshOutput(double energyRate) {
        var signal = switch (function) {
            case OFF, CONTROL -> 0;
            case ANALOG -> calculateAnalogSignal(energyRate);
            case THRESHOLD -> calculateThresholdSignal(energyRate);
        };

        var isInverted = inverted && function.emitsSignal();
        return setOutputSignal(isInverted ? Redstone.SIGNAL_MAX - signal : signal);
    }

    public boolean refreshInput(int signal) {
        inputDirty = false;
        if (inputSignal == signal) return false;

        inputSignal = signal;
        changed = true;
        return true;
    }

    public boolean needsInputRefresh() {
        return inputDirty && function == RedstoneFunction.CONTROL;
    }

    public void markInputDirty() {
        inputDirty = true;
    }

    public long getEffectiveTransferLimit(long transferLimit, TransferMode transferMode) {
        var isThrottling = function == RedstoneFunction.CONTROL && controlBehavior == ControlBehavior.THROTTLE;
        var isLimitApplied = transferLimit > 0 && (transferMode != TransferMode.CONSUME || isThrottling);
        var unscaledTransferLimit = isLimitApplied ? transferLimit : Long.MAX_VALUE;
        if (function != RedstoneFunction.CONTROL) return unscaledTransferLimit;

        if (!isThrottling) {
            var isPowered = inputSignal > 0;
            return isPowered == inverted ? unscaledTransferLimit : 0;
        }

        var throughputLevel = inverted ? inputSignal : Redstone.SIGNAL_MAX - inputSignal;
        if (throughputLevel == 0) return 0;
        if (!isLimitApplied) return Long.MAX_VALUE;

        // split to prevent an overflow for limits close to the maximum
        var scaledQuotient = transferLimit / Redstone.SIGNAL_MAX * throughputLevel;
        var scaledRemainder = transferLimit % Redstone.SIGNAL_MAX * throughputLevel / Redstone.SIGNAL_MAX;
        return scaledQuotient + scaledRemainder;
    }

    private boolean setOutputSignal(int signal) {
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
        buffer.writeByte(controlBehavior.ordinal());
        buffer.writeVarLong(lowerBound);
        buffer.writeVarLong(upperBound);
        buffer.writeBoolean(inverted);
        buffer.writeByte(inputSignal);
        buffer.writeByte(outputSignal);
        changed = false;
    }

    @Override
    public void decode(FriendlyByteBuf buffer) {
        function = REDSTONE_FUNCTIONS[buffer.readByte()];
        scale = REDSTONE_SCALES[buffer.readByte()];
        controlBehavior = CONTROL_BEHAVIORS[buffer.readByte()];
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

    public ControlBehavior getControlBehavior() {
        return controlBehavior;
    }

    public void setControlBehavior(ControlBehavior controlBehavior) {
        this.controlBehavior = controlBehavior;
        setChanged();
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
        setChanged();
    }

    public int getInputSignal() {
        return function == RedstoneFunction.CONTROL ? inputSignal : 0;
    }

    public int getOutputSignal() {
        return outputSignal;
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

    public enum ControlBehavior {
        SWITCH, // any signal blocks the transfer
        THROTTLE // the signal strength scales down the transfer limit
    }
}
