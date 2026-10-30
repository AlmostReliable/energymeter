package com.almostreliable.energymeter.client.screen;

import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.network.action.TextValueClientAction;

import com.google.common.primitives.Ints;

import java.util.function.BiConsumer;

public enum MeterTextBoxType implements TextValueClientAction.ValueConsumer<MeterBlockEntity> {
    TRANSFER_LIMIT(MeterBlockEntity::setTransferLimit),
    MEASURE_INTERVAL((be, value) -> be.setMeasureInterval(Ints.saturatedCast(value))),
    ZERO_TOLERANCE((be, value) -> be.setZeroTolerance(Ints.saturatedCast(value))),
    REDSTONE_LOWER_BOUND((be, value) -> be.getRedstoneHandler().setLowerBound(value)),
    REDSTONE_UPPER_BOUND((be, value) -> be.getRedstoneHandler().setUpperBound(value));

    private final BiConsumer<MeterBlockEntity, Long> valueUpdater;

    MeterTextBoxType(BiConsumer<MeterBlockEntity, Long> valueUpdater) {
        this.valueUpdater = valueUpdater;
    }

    @Override
    public void updateValue(MeterBlockEntity blockEntity, long value) {
        valueUpdater.accept(blockEntity, value);
    }
}
