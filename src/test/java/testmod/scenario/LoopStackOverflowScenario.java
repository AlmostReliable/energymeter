package testmod.scenario;

import com.almostreliable.energymeter.block.component.EnergyHandler;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import testmod.TestMod;
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
 * This was fixed by adding a guard against re-entry in {@link EnergyHandler}.
 */
@SuppressWarnings("NewMethodNamingConvention")
@GameTestHolder(TestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LoopStackOverflowScenario {

    private static final BlockPos RECEIVER_POS = TestUtils.DEFAULT_POS.east().south();
    private static final BlockPos SECOND_METER_POS = TestUtils.DEFAULT_POS.below();

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void loopWithoutConsumer(GameTestHelper helper) {
        // set up the plot
        var plotResult = setupLoopPlot(helper);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunctionsWithResult().get(Direction.WEST);

        // push energy towards the meter from the input side and store the result
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(() -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyAccepted, 0, "energy accepted");
        helper.assertValueEqual(meterBlockEntity.getEnergyHandler().getEnergyPerInterval(), 0L, "energy per interval");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void loopWithConsumer(GameTestHelper helper) {
        // set up the plot
        var plotResult = setupLoopPlot(helper);
        EnergyReceiverBlockEntity receiverBlockEntity = TestUtils.placeReceiver(helper, RECEIVER_POS);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunctionsWithResult().get(Direction.WEST);
        IEnergyStorage receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, RECEIVER_POS, null);

        // limit the receiver so the cable offers the remaining energy back to the meter input
        int energyMaxReceive = 400;
        receiverBlockEntity.setMaxReceive(energyMaxReceive);

        // push energy towards the meter from the input side and store the result
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(() -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyAccepted, energyMaxReceive, "energy accepted");
        helper.assertValueEqual(receiverEnergyBlockCap.getEnergyStored(), energyMaxReceive, "receiver energy");
        helper.assertValueEqual(
            meterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            (long) energyMaxReceive,
            "energy per interval"
        );

        // push energy again to check that the guard was released
        energyAccepted = withoutOverflow(() -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyAccepted, energyMaxReceive, "second energy accepted");
        helper.assertValueEqual(
            receiverEnergyBlockCap.getEnergyStored(),
            energyMaxReceive * 2,
            "second receiver energy"
        );
        helper.assertValueEqual(
            meterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            energyMaxReceive * 2L,
            "second energy per interval"
        );

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void loopSimulation(GameTestHelper helper) {
        // set up the plot
        var plotResult = setupLoopPlot(helper);
        EnergyReceiverBlockEntity receiverBlockEntity = TestUtils.placeReceiver(helper, RECEIVER_POS);

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        var inputEnergyFunction = plotResult.inputEnergyFunctionsWithResult().get(Direction.WEST);
        IEnergyStorage receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, RECEIVER_POS, null);

        // limit the receiver so the cable offers the remaining energy back to the meter input
        int energyMaxReceive = 400;
        receiverBlockEntity.setMaxReceive(energyMaxReceive);

        // simulate pushing energy towards the meter from the input side and store the result
        int energyPerTick = 1_000;
        int energySimulated = withoutOverflow(() -> inputEnergyFunction.apply(energyPerTick, true));

        // check the values
        helper.assertValueEqual(energySimulated, energyMaxReceive, "energy simulated");
        helper.assertValueEqual(receiverEnergyBlockCap.getEnergyStored(), 0, "receiver energy");
        helper.assertValueEqual(meterBlockEntity.getEnergyHandler().getEnergyPerInterval(), 0L, "energy per interval");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void loopEnergyQueries(GameTestHelper helper) {
        // set up the plot
        setupLoopPlot(helper);
        EnergyReceiverBlockEntity receiverBlockEntity = TestUtils.placeReceiver(helper, RECEIVER_POS);

        IEnergyStorage inputEnergyCap = TestUtils.getEnergyCap(helper, TestUtils.DEFAULT_POS, Direction.WEST);
        IEnergyStorage receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, RECEIVER_POS, null);

        // partially fill the receiver
        int receiverCapacity = 5_000;
        int receiverEnergy = 1_000;
        receiverBlockEntity.setCapacity(receiverCapacity);
        receiverEnergyBlockCap.receiveEnergy(receiverEnergy, false);

        // query the energy metadata from the input side
        int energyAmount = withoutOverflow(inputEnergyCap::getEnergyStored);
        int energyCapacity = withoutOverflow(inputEnergyCap::getMaxEnergyStored);

        // check the values
        helper.assertValueEqual(energyAmount, receiverEnergy, "energy amount");
        helper.assertValueEqual(energyCapacity, receiverCapacity, "energy capacity");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void loopTwoMeters(GameTestHelper helper) {
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
        IEnergyStorage receiverEnergyBlockCap = TestUtils.getEnergyCap(helper, receiverPos, null);

        // limit the receiver so the cable offers the remaining energy back to the first meter
        int energyMaxReceive = 400;
        receiverBlockEntity.setMaxReceive(energyMaxReceive);

        // push energy towards the first meter from the input side and store the result
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(() -> inputEnergyFunction.apply(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyAccepted, energyMaxReceive, "energy accepted");
        helper.assertValueEqual(receiverEnergyBlockCap.getEnergyStored(), energyMaxReceive, "receiver energy");
        helper.assertValueEqual(
            firstMeterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            (long) energyMaxReceive,
            "first energy per interval"
        );
        helper.assertValueEqual(
            secondMeterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            (long) energyMaxReceive,
            "second energy per interval"
        );

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void sharedInputNetwork(GameTestHelper helper) {
        // set up the plot with two inputs on the same cable network
        var plotResult = PlotBuilder.create(helper)
            .inputs(Direction.WEST, Direction.NORTH)
            .output(Direction.EAST)
            .build();
        BlockPos cablePos = TestUtils.DEFAULT_POS.west().north();
        TestUtils.placeCables(helper, TestUtils.DEFAULT_POS.west(), cablePos, TestUtils.DEFAULT_POS.north());

        MeterBlockEntity meterBlockEntity = plotResult.meterBlockEntity();
        EnergyReceiverBlockEntity outputEnergyBlockEntity = plotResult.outputEnergyBlockEntities().get(Direction.EAST);
        IEnergyStorage outputEnergyBlockCap = plotResult.outputEnergyBlockCaps().get(Direction.EAST);
        IEnergyStorage cableEnergyCap = TestUtils.getEnergyCap(helper, cablePos, null);

        // limit the output block so a single input can't forward all energy
        int energyMaxReceive = 600;
        outputEnergyBlockEntity.setMaxReceive(energyMaxReceive);

        // push energy into the cable network, which offers it to both inputs in sequence
        int energyPerTick = 1_000;
        int energyAccepted = withoutOverflow(() -> cableEnergyCap.receiveEnergy(energyPerTick, false));

        // check the values
        helper.assertValueEqual(energyAccepted, energyPerTick, "energy accepted");
        helper.assertValueEqual(outputEnergyBlockCap.getEnergyStored(), energyPerTick, "output energy");
        helper.assertValueEqual(
            meterBlockEntity.getEnergyHandler().getEnergyPerInterval(),
            (long) energyPerTick,
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

    private static <T> T withoutOverflow(Supplier<T> action) {
        try {
            return action.get();
        } catch (StackOverflowError e) {
            throw new GameTestAssertException("energy loop caused a stack overflow");
        }
    }
}
