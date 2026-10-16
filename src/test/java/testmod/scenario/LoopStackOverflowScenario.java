package testmod.scenario;

import com.almostreliable.energymeter.block.component.MeterEnergyHandler;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import testmod.TestUtils;
import testmod.TestUtils.PlotBuilder;
import testmod.content.EnergyReceiverBlockEntity;

import java.util.function.Supplier;

/**
 * This scenario tests a bug reported <a href="https://github.com/AlmostReliable/energymeter/issues/46">here</a>.
 * <p>
 * Feeding energy from the meter back into its own input causes a stack overflow.
 * <p>
 * The test cable has no buffer and no guard against re-entry, so without a guard in the meter, energy
 * would bounce between the meter and the cable until the stack overflows.
 * <p>
 * This was fixed by adding a guard against re-entry in {@link MeterEnergyHandler}.
 */
public class LoopStackOverflowScenario {

    private static final BlockPos RECEIVER_POS = TestUtils.DEFAULT_POS.east().south();
    private static final BlockPos SECOND_METER_POS = TestUtils.DEFAULT_POS.below();

    public static void loopWithoutConsumer(GameTestHelper helper) {
        // set up the plot
        var plotResult = setupLoopPlot(helper);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunctionsWithResult().get(Direction.WEST);

        // push energy towards the meter from the input side and store the result
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(helper, () -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(0, energyAccepted, "energy accepted");
        helper.assertValueEqual(0L, meterBlockEntity.getEnergyHandler().getEnergyPerInterval(), "energy per interval");

        helper.succeed();
    }

    public static void loopWithConsumer(GameTestHelper helper) {
        // set up the plot
        var plotResult = setupLoopPlot(helper);
        EnergyReceiverBlockEntity receiverBlockEntity = TestUtils.placeReceiver(helper, RECEIVER_POS);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunctionsWithResult().get(Direction.WEST);
        EnergyHandler receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, RECEIVER_POS, null);

        // limit the receiver so the cable offers the remaining energy back to the meter input
        int energyMaxReceive = 400;
        receiverBlockEntity.setMaxReceive(energyMaxReceive);

        // push energy towards the meter from the input side and store the result
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(helper, () -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyMaxReceive, energyAccepted, "energy accepted");
        helper.assertValueEqual(energyMaxReceive, receiverEnergyBlockCap.getAmountAsInt(), "receiver energy");
        helper.assertValueEqual(
            (long) energyMaxReceive,
            meterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            "energy per interval"
        );

        // push energy again to check that the guard was released
        energyAccepted = withoutOverflow(helper, () -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyMaxReceive, energyAccepted, "second energy accepted");
        helper.assertValueEqual(
            energyMaxReceive * 2,
            receiverEnergyBlockCap.getAmountAsInt(),
            "second receiver energy"
        );
        helper.assertValueEqual(
            energyMaxReceive * 2L,
            meterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            "second energy per interval"
        );

        helper.succeed();
    }

    public static void loopRollback(GameTestHelper helper) {
        // set up the plot
        var plotResult = setupLoopPlot(helper);
        EnergyReceiverBlockEntity receiverBlockEntity = TestUtils.placeReceiver(helper, RECEIVER_POS);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunctionsWithResult().get(Direction.WEST);
        EnergyHandler receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, RECEIVER_POS, null);

        // limit the receiver so the cable offers the remaining energy back to the meter input
        int energyMaxReceive = 400;
        receiverBlockEntity.setMaxReceive(energyMaxReceive);

        // simulate pushing energy towards the meter from the input side and store the result
        int energyPerTick = 1_000;
        int energySimulated = withoutOverflow(helper, () -> inputEnergyFunction.apply(energyPerTick, true));

        // check the values
        helper.assertValueEqual(energyMaxReceive, energySimulated, "energy simulated");
        helper.assertValueEqual(0, receiverEnergyBlockCap.getAmountAsInt(), "receiver energy");
        helper.assertValueEqual(0L, meterBlockEntity.getEnergyHandler().getEnergyPerInterval(), "energy per interval");

        helper.succeed();
    }

    public static void loopEnergyQueries(GameTestHelper helper) {
        // set up the plot
        setupLoopPlot(helper);
        EnergyReceiverBlockEntity receiverBlockEntity = TestUtils.placeReceiver(helper, RECEIVER_POS);

        EnergyHandler inputEnergyCap = TestUtils.getEnergyCap(helper, TestUtils.DEFAULT_POS, Direction.WEST);
        EnergyHandler receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, RECEIVER_POS, null);

        // partially fill the receiver
        int receiverCapacity = 5_000;
        int receiverEnergy = 1_000;
        receiverBlockEntity.setCapacity(receiverCapacity);
        TestUtils.insertEnergy(receiverEnergyBlockCap, receiverEnergy, false);

        // query the energy metadata from the input side
        long energyAmount = withoutOverflow(helper, inputEnergyCap::getAmountAsLong);
        long energyCapacity = withoutOverflow(helper, inputEnergyCap::getCapacityAsLong);

        // check the values
        helper.assertValueEqual((long) receiverEnergy, energyAmount, "energy amount");
        helper.assertValueEqual((long) receiverCapacity, energyCapacity, "energy capacity");

        helper.succeed();
    }

    public static void loopTwoMeters(GameTestHelper helper) {
        // set up the plot with two meters that feed each other through cables
        PlotBuilder.Result firstPlotResult = PlotBuilder.create(helper)
            .input(Direction.WEST)
            .cableOutput(Direction.EAST)
            .build();
        PlotBuilder.Result secondPlotResult = PlotBuilder.create(helper, SECOND_METER_POS)
            .input(Direction.EAST)
            .cableOutput(Direction.WEST)
            .build();
        TestUtils.placeCables(helper, SECOND_METER_POS.east(), TestUtils.DEFAULT_POS.west());
        BlockPos receiverPos = SECOND_METER_POS.west().north();
        EnergyReceiverBlockEntity receiverBlockEntity = TestUtils.placeReceiver(helper, receiverPos);

        MeterBlockEntity firstMeterBlockEntity = firstPlotResult.meterBlockEntity();
        MeterBlockEntity secondMeterBlockEntity = secondPlotResult.meterBlockEntity();
        var inputEnergyFunction = firstPlotResult.inputEnergyFunctionsWithResult().get(Direction.WEST);
        EnergyHandler receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, receiverPos, null);

        // limit the receiver so the cable offers the remaining energy back to the first meter
        int energyMaxReceive = 400;
        receiverBlockEntity.setMaxReceive(energyMaxReceive);

        // push energy towards the first meter from the input side and store the result
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(helper, () -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyMaxReceive, energyAccepted, "energy accepted");
        helper.assertValueEqual(energyMaxReceive, receiverEnergyBlockCap.getAmountAsInt(), "receiver energy");
        helper.assertValueEqual(
            (long) energyMaxReceive,
            firstMeterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            "first energy per interval"
        );
        helper.assertValueEqual(
            (long) energyMaxReceive,
            secondMeterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            "second energy per interval"
        );

        helper.succeed();
    }

    public static void sharedInputNetwork(GameTestHelper helper) {
        // set up the plot with two inputs on the same cable network
        var plotResult = PlotBuilder.create(helper)
            .inputs(Direction.WEST, Direction.NORTH)
            .output(Direction.EAST)
            .build();
        BlockPos cablePos = TestUtils.DEFAULT_POS.west().north();
        TestUtils.placeCables(helper, TestUtils.DEFAULT_POS.west(), cablePos, TestUtils.DEFAULT_POS.north());

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        EnergyReceiverBlockEntity outputEnergyBlockEntity = plotResult.outputEnergyBlockEntities().get(Direction.EAST);
        EnergyHandler outputEnergyBlockCap = plotResult.outputEnergyBlockCaps().get(Direction.EAST);
        EnergyHandler cableEnergyCap = TestUtils.getEnergyCap(helper, cablePos, null);

        // limit the output block so a single input can't forward all energy
        int energyMaxReceive = 600;
        outputEnergyBlockEntity.setMaxReceive(energyMaxReceive);

        // push energy into the cable network, which offers it to both inputs in sequence
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(
            helper,
            () -> TestUtils.insertEnergy(cableEnergyCap, energyPerTick, false)
        );

        // check the values
        helper.assertValueEqual(energyPerTick, energyAccepted, "energy accepted");
        helper.assertValueEqual(energyPerTick, outputEnergyBlockCap.getAmountAsInt(), "output energy");
        helper.assertValueEqual(
            (long) energyPerTick,
            meterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            "energy per interval"
        );

        helper.succeed();
    }

    private static PlotBuilder.Result setupLoopPlot(GameTestHelper helper) {
        var plotResult = PlotBuilder.create(helper)
            .input(Direction.WEST)
            .cableOutput(Direction.EAST)
            .build();

        BlockPos meterPos = TestUtils.DEFAULT_POS;
        TestUtils.placeCables(
            helper,
            meterPos.east().north(),
            meterPos.north(),
            meterPos.west().north(),
            meterPos.west()
        );

        return plotResult;
    }

    private static <T> T withoutOverflow(GameTestHelper helper, Supplier<T> action) {
        try {
            return action.get();
        } catch (StackOverflowError e) {
            throw new GameTestAssertException(
                Component.literal("energy loop caused a stack overflow"),
                (int) helper.getTick()
            );
        }
    }
}
