package testmod.test;

import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import testmod.TestMod;
import testmod.TestUtils;
import testmod.TestUtils.SimplePlotResult;

public class MeterConsumeTests {

    public static void consume_single(GameTestHelper helper) {
        // set up the plot
        SimplePlotResult plotResult = TestUtils.setupSimplePlot(helper);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunction();
        EnergyHandler outputEnergyBlockCap = plotResult.outputEnergyBlockCap();

        // set transfer mode to consume
        meterBlockEntity.setTransferMode(TransferMode.CONSUME);

        // check consume input
        EnergyHandler inputEnergyStorage = TestUtils.getEnergyCap(helper, TestUtils.DEFAULT_POS, Direction.WEST);
        helper.assertValueEqual(0L, inputEnergyStorage.getAmountAsLong(), "consume input stored energy");
        helper.assertValueEqual(Long.MAX_VALUE, inputEnergyStorage.getCapacityAsLong(), "consume input capacity");

        // push energy towards the meter from the input side
        int energyPerTick = TestUtils.getRandomEnergyRate();
        for (int i = 0; i < MeterBlockEntity.DEFAULT_INTERVAL; i++) {
            inputEnergyFunction.accept(energyPerTick);
        }

        // simulate a full interval
        meterBlockEntity.refreshEnergyValues();

        // check the values
        double energyRate = meterBlockEntity.getEnergyRate();
        helper.assertTrue(
            energyRate == energyPerTick,
            String.format("expected energy rate to be equal to input energy rate of %s, but was %s", energyPerTick, energyRate)
        );

        double totalEnergyTransferred = meterBlockEntity.getTotalEnergy();
        int expectedEnergyTransferred = energyPerTick * MeterBlockEntity.DEFAULT_INTERVAL;
        helper.assertTrue(
            totalEnergyTransferred == expectedEnergyTransferred,
            String.format("expected total energy of %s, but was %s", expectedEnergyTransferred, totalEnergyTransferred)
        );

        int energyStored = outputEnergyBlockCap.getAmountAsInt();
        helper.assertTrue(
            energyStored == 0,
            String.format("expected stored output energy of %s, but was %s", 0, energyStored)
        );

        helper.succeed();
    }

    public static void consume_trio(GameTestHelper helper) {
        // set up the plot
        var plotResult = TestUtils.PlotBuilder.create(helper)
            .inputs(Direction.WEST, Direction.SOUTH, Direction.EAST)
            .output(Direction.UP)
            .build();

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var westInEnergyFunction = plotResult.inputEnergyFunctions().get(Direction.WEST);
        var southInEnergyFunction = plotResult.inputEnergyFunctions().get(Direction.SOUTH);
        var eastInEnergyFunction = plotResult.inputEnergyFunctions().get(Direction.EAST);
        EnergyHandler outputEnergyBlockCap = plotResult.outputEnergyBlockCaps().get(Direction.UP);

        // set transfer mode to consume
        meterBlockEntity.setTransferMode(TransferMode.CONSUME);

        // push energy towards the meter from the input sides
        int energyPerTick = TestUtils.getRandomEnergyRate();
        for (int i = 0; i < MeterBlockEntity.DEFAULT_INTERVAL; i++) {
            westInEnergyFunction.accept(energyPerTick);
            southInEnergyFunction.accept(energyPerTick);
            eastInEnergyFunction.accept(energyPerTick);
        }

        // simulate a full interval
        meterBlockEntity.refreshEnergyValues();

        // check the values
        double energyRate = meterBlockEntity.getEnergyRate();
        int expectedEnergyRate = energyPerTick * 3;
        helper.assertTrue(
            energyRate == expectedEnergyRate,
            String.format("expected energy rate to be equal to input energy rate of %s, but was %s", expectedEnergyRate, energyRate)
        );

        double totalEnergyTransferred = meterBlockEntity.getTotalEnergy();
        int expectedEnergyTransferred = energyPerTick * MeterBlockEntity.DEFAULT_INTERVAL * 3;
        helper.assertTrue(
            totalEnergyTransferred == expectedEnergyTransferred,
            String.format("expected total energy of %s, but was %s", expectedEnergyTransferred, totalEnergyTransferred)
        );

        int energyStored = outputEnergyBlockCap.getAmountAsInt();
        helper.assertTrue(
            energyStored == 0,
            String.format("expected stored output energy of %s, but was %s", 0, energyStored)
        );

        helper.succeed();
    }
}
