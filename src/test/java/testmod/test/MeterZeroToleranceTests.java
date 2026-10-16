package testmod.test;

import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;

import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import testmod.TestMod;
import testmod.TestUtils;
import testmod.TestUtils.SimplePlotResult;

public class MeterZeroToleranceTests {

    public static void test(GameTestHelper helper) {
        // set up the plot
        SimplePlotResult plotResult = TestUtils.setupSimplePlot(helper);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunction();
        EnergyHandler outputEnergyBlockCap = plotResult.outputEnergyBlockCap();

        // set transfer mode to split
        meterBlockEntity.setTransferMode(TransferMode.SPLIT);

        // set measure interval and ensure it is set correctly
        int measureInterval = 15;
        meterBlockEntity.setMeasureInterval(measureInterval);
        helper.assertValueEqual(measureInterval, meterBlockEntity.getMeasureInterval(), "measure interval should be set correctly");

        // set zero tolerance and ensure it is set correctly
        int zeroTolerance = 5;
        meterBlockEntity.setZeroTolerance(zeroTolerance);
        helper.assertValueEqual(zeroTolerance, meterBlockEntity.getZeroTolerance(), "zero tolerance should be set correctly");

        // push energy towards the meter from the input side for 8 ticks
        int energyPerTick = 1000;
        for (int i = 0; i < 8; i++) {
            inputEnergyFunction.accept(energyPerTick);
            meterBlockEntity.getEnergyHandler().tick();
        }

        // check if the energy handler tracked the energy
        long energyPerInterval = meterBlockEntity.getEnergyHandler().getEnergyPerInterval();
        long expectedEnergyPerInterval = energyPerTick * 8L;
        helper.assertTrue(
            energyPerInterval == expectedEnergyPerInterval,
            String.format("expected tracked energy of %s, but was %s", expectedEnergyPerInterval, energyPerInterval)
        );

        // tick the energy handler for 5 more ticks simulating no energy transfer
        for (int i = 0; i < 5; i++) {
            meterBlockEntity.getEnergyHandler().tick();
        }

        // check if the energy handler did not reset
        energyPerInterval = meterBlockEntity.getEnergyHandler().getEnergyPerInterval();
        helper.assertTrue(
            energyPerInterval == expectedEnergyPerInterval,
            String.format("expected tracked energy of %s, but was %s", expectedEnergyPerInterval, energyPerInterval)
        );

        // tick the energy handler one more tick that should exceed the zero tolerance
        meterBlockEntity.getEnergyHandler().tick();

        // check if the interval restarted
        energyPerInterval = meterBlockEntity.getEnergyHandler().getEnergyPerInterval();
        helper.assertTrue(
            energyPerInterval == 0,
            "expected tracked energy to be zero after exceeding zero tolerance, but was " + energyPerInterval
        );

        helper.succeed();
    }
}
