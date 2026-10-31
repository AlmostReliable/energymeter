package testmod.test;

import com.almostreliable.energymeter.block.MeterBlock;
import com.almostreliable.energymeter.block.component.RedstoneHandler;
import com.almostreliable.energymeter.block.component.RedstoneHandler.ControlBehavior;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneFunction;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneScale;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.ConnectionStatus;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import testmod.TestMod;
import testmod.TestUtils;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@SuppressWarnings("NewMethodNamingConvention")
@GameTestHolder(TestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class MeterRedstoneTests {

    private static final int MEASURE_INTERVAL = 5;

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void signal(GameTestHelper helper) {
        var redstoneHandler = new RedstoneHandler(() -> {});

        // test that off emits nothing, even when inverted
        redstoneHandler.setInverted(true);
        assertSignal(helper, redstoneHandler, 0, 0);
        assertSignal(helper, redstoneHandler, 1_000_000, 0);
        redstoneHandler.setInverted(false);

        // test linear step semantics, one level per 15k FE/t
        redstoneHandler.setFunction(RedstoneFunction.ANALOG);
        redstoneHandler.setLowerBound(0);
        redstoneHandler.setUpperBound(225_000);
        assertSignal(helper, redstoneHandler, 0, 0);
        assertSignal(helper, redstoneHandler, 14_999, 0);
        assertSignal(helper, redstoneHandler, 15_000, 1);
        assertSignal(helper, redstoneHandler, 112_500, 7);
        assertSignal(helper, redstoneHandler, 224_999, 14);
        assertSignal(helper, redstoneHandler, 225_000, 15);
        assertSignal(helper, redstoneHandler, 1_000_000, 15);

        // test linear with lower bound
        redstoneHandler.setLowerBound(1_000);
        redstoneHandler.setUpperBound(16_000);
        assertSignal(helper, redstoneHandler, 500, 0);
        assertSignal(helper, redstoneHandler, 1_000, 0);
        assertSignal(helper, redstoneHandler, 2_000, 1);
        assertSignal(helper, redstoneHandler, 16_000, 15);

        // test logarithmic, one level per decade with a lower bound of zero treated as one
        redstoneHandler.setScale(RedstoneScale.LOGARITHMIC);
        redstoneHandler.setLowerBound(0);
        redstoneHandler.setUpperBound(1_000_000_000_000_000L);
        assertSignal(helper, redstoneHandler, 0, 0);
        assertSignal(helper, redstoneHandler, 1, 0);
        assertSignal(helper, redstoneHandler, 9, 0);
        assertSignal(helper, redstoneHandler, 10, 1);
        assertSignal(helper, redstoneHandler, 1_000, 3);
        assertSignal(helper, redstoneHandler, 999_999, 5);
        assertSignal(helper, redstoneHandler, 1_000_000, 6);
        assertSignal(helper, redstoneHandler, 1_000_000_000_000_000L, 15);

        // test degenerate bounds acting as a threshold at the upper bound
        redstoneHandler.setLowerBound(5_000);
        redstoneHandler.setUpperBound(5_000);
        assertSignal(helper, redstoneHandler, 4_999, 0);
        assertSignal(helper, redstoneHandler, 5_000, 15);
        redstoneHandler.setScale(RedstoneScale.LINEAR);
        redstoneHandler.setLowerBound(10_000);
        assertSignal(helper, redstoneHandler, 4_999, 0);
        assertSignal(helper, redstoneHandler, 7_000, 15);

        // test invert
        redstoneHandler.setLowerBound(0);
        redstoneHandler.setUpperBound(15_000);
        redstoneHandler.setInverted(true);
        assertSignal(helper, redstoneHandler, 0, 15);
        assertSignal(helper, redstoneHandler, 4_000, 11);
        assertSignal(helper, redstoneHandler, 15_000, 0);
        redstoneHandler.setInverted(false);

        // test threshold hysteresis
        redstoneHandler.setFunction(RedstoneFunction.THRESHOLD);
        redstoneHandler.setLowerBound(5_000);
        redstoneHandler.setUpperBound(10_000);
        assertSignal(helper, redstoneHandler, 7_000, 0);
        assertSignal(helper, redstoneHandler, 10_000, 15);
        assertSignal(helper, redstoneHandler, 7_000, 15);
        assertSignal(helper, redstoneHandler, 5_000, 15);
        assertSignal(helper, redstoneHandler, 4_999, 0);
        assertSignal(helper, redstoneHandler, 7_000, 0);

        // test inverted threshold
        redstoneHandler.setInverted(true);
        assertSignal(helper, redstoneHandler, 7_000, 15);
        assertSignal(helper, redstoneHandler, 12_000, 0);
        assertSignal(helper, redstoneHandler, 7_000, 0);

        // test threshold without hysteresis for degenerate bounds
        redstoneHandler.setInverted(false);
        redstoneHandler.setLowerBound(10_000);
        assertSignal(helper, redstoneHandler, 9_999, 0);
        assertSignal(helper, redstoneHandler, 10_000, 15);
        assertSignal(helper, redstoneHandler, 9_999, 0);

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void redstone_connection(GameTestHelper helper) {
        // set up the plot
        var meterBlockEntity = TestUtils.setupMeter(helper);
        var sideDustPos = placeDust(helper, Direction.SOUTH);
        var frontDustPos = placeDust(helper, Direction.NORTH);

        var redstoneHandler = meterBlockEntity.getRedstoneHandler();

        // test that no connection occurs while disabled
        assertDustConnection(helper, sideDustPos, Direction.NORTH, RedstoneSide.NONE, "side dust should not connect while disabled");

        // test that a function connects every face except the front
        redstoneHandler.setFunction(RedstoneFunction.ANALOG);
        helper.assertTrue(meterBlockEntity.getBlockState().getValue(MeterBlock.REDSTONE), "redstone should be enabled");
        assertDustConnection(helper, sideDustPos, Direction.NORTH, RedstoneSide.SIDE, "side dust should connect while enabled");
        assertDustConnection(helper, frontDustPos, Direction.SOUTH, RedstoneSide.NONE, "front dust should never connect");

        // test that switching between functions keeps the connection
        redstoneHandler.setFunction(RedstoneFunction.THRESHOLD);
        assertDustConnection(helper, sideDustPos, Direction.NORTH, RedstoneSide.SIDE, "side dust should stay connected");

        // test that off disconnects again
        redstoneHandler.setFunction(RedstoneFunction.OFF);
        assertDustConnection(helper, sideDustPos, Direction.NORTH, RedstoneSide.NONE, "side dust should disconnect when disabled");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void redstone_output(GameTestHelper helper) {
        // set up the plot
        var plotResult = TestUtils.setupSimplePlot(helper);
        var sideDustPos = placeDust(helper, Direction.SOUTH);
        var frontDustPos = placeDust(helper, Direction.NORTH);

        var meterBlockEntity = plotResult.meterBlockEntity();
        var redstoneHandler = meterBlockEntity.getRedstoneHandler();
        var inputEnergyFunction = plotResult.inputEnergyFunction();

        // configure interval and upper bound
        meterBlockEntity.setMeasureInterval(MEASURE_INTERVAL);
        redstoneHandler.setUpperBound(1_500);

        // test that the output stays off while off
        measureRate(meterBlockEntity, inputEnergyFunction, 700);
        helper.assertValueEqual(0, redstoneHandler.getOutputSignal(), "redstone output while off");
        assertDustPower(helper, sideDustPos, 0, "side dust while off");

        // test analog output on the side but not the front
        redstoneHandler.setFunction(RedstoneFunction.ANALOG);
        helper.assertValueEqual(7, redstoneHandler.getOutputSignal(), "redstone output after enabling");
        assertDustPower(helper, sideDustPos, 7, "side dust after enabling");
        measureRate(meterBlockEntity, inputEnergyFunction, 1_200);
        assertDustPower(helper, sideDustPos, 12, "side dust with analog output");
        assertDustPower(helper, frontDustPos, 0, "front dust with analog output");

        // test threshold output with hysteresis
        redstoneHandler.setFunction(RedstoneFunction.THRESHOLD);
        redstoneHandler.setLowerBound(500);
        redstoneHandler.setUpperBound(1_000);
        assertDustPower(helper, sideDustPos, 15, "side dust above the upper bound");
        measureRate(meterBlockEntity, inputEnergyFunction, 700);
        assertDustPower(helper, sideDustPos, 15, "side dust between the bounds after being active");
        measureRate(meterBlockEntity, inputEnergyFunction, 400);
        assertDustPower(helper, sideDustPos, 0, "side dust below the lower bound");
        measureRate(meterBlockEntity, inputEnergyFunction, 700);
        assertDustPower(helper, sideDustPos, 0, "side dust between the bounds after being inactive");
        assertDustPower(helper, frontDustPos, 0, "front dust with threshold output");

        // test that off clears the output
        redstoneHandler.setFunction(RedstoneFunction.OFF);
        helper.assertValueEqual(0, redstoneHandler.getOutputSignal(), "redstone output after switching off");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void redstone_disconnected_inverted(GameTestHelper helper) {
        // set up the plot
        var meterBlockEntity = TestUtils.setupMeter(helper);
        var sideDustPos = placeDust(helper, Direction.SOUTH);

        var redstoneHandler = meterBlockEntity.getRedstoneHandler();

        // test that a meter without flow emits a full signal when inverted
        redstoneHandler.setFunction(RedstoneFunction.ANALOG);
        redstoneHandler.setInverted(true);
        helper.assertValueEqual(15, redstoneHandler.getOutputSignal(), "inverted output without flow");
        assertDustPower(helper, sideDustPos, 15, "side dust with inverted output without flow");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void redstone_control(GameTestHelper helper) {
        // set up the plot
        var plotResult = TestUtils.setupSimplePlot(helper);
        var sidePos = TestUtils.DEFAULT_POS.relative(Direction.SOUTH);
        var frontPos = TestUtils.DEFAULT_POS.relative(Direction.NORTH);

        var meterBlockEntity = plotResult.meterBlockEntity();
        var redstoneHandler = meterBlockEntity.getRedstoneHandler();
        var inputEnergyFunction = plotResult.inputEnergyFunctionWithResult();

        // test that control transfers without a signal
        redstoneHandler.setFunction(RedstoneFunction.CONTROL);
        meterBlockEntity.tick(helper.getLevel());
        assertTransferAllowed(helper, meterBlockEntity, inputEnergyFunction, "control without a signal");

        // test that a signal on a side face blocks the transfer
        helper.setBlock(sidePos, Blocks.REDSTONE_BLOCK);
        meterBlockEntity.tick(helper.getLevel());
        helper.assertValueEqual(15, redstoneHandler.getInputSignal(), "input signal with a redstone block on the side");
        assertTransferBlocked(helper, meterBlockEntity, inputEnergyFunction, "control with a redstone block on the side");

        // test that removing the signal resumes the transfer
        helper.setBlock(sidePos, Blocks.AIR);
        meterBlockEntity.tick(helper.getLevel());
        assertTransferAllowed(helper, meterBlockEntity, inputEnergyFunction, "control after removing the signal");

        // test that the front face is ignored
        helper.setBlock(frontPos, Blocks.REDSTONE_BLOCK);
        meterBlockEntity.tick(helper.getLevel());
        helper.assertValueEqual(0, redstoneHandler.getInputSignal(), "input signal with a redstone block on the front");
        assertTransferAllowed(helper, meterBlockEntity, inputEnergyFunction, "control with a redstone block on the front");
        helper.setBlock(frontPos, Blocks.AIR);

        // test that invert blocks without a signal and transfers with one
        redstoneHandler.setInverted(true);
        meterBlockEntity.tick(helper.getLevel());
        assertTransferBlocked(helper, meterBlockEntity, inputEnergyFunction, "inverted control without a signal");
        helper.setBlock(sidePos, Blocks.REDSTONE_BLOCK);
        meterBlockEntity.tick(helper.getLevel());
        assertTransferAllowed(helper, meterBlockEntity, inputEnergyFunction, "inverted control with a signal");
        redstoneHandler.setInverted(false);

        // test that consume mode is blocked as well
        meterBlockEntity.setTransferMode(TransferMode.CONSUME);
        meterBlockEntity.tick(helper.getLevel());
        assertTransferBlocked(helper, meterBlockEntity, inputEnergyFunction, "control in consume mode");
        meterBlockEntity.setTransferMode(TransferMode.SPLIT);
        helper.setBlock(sidePos, Blocks.AIR);

        // test that the meter emits nothing so powered dust does not latch
        var sideDustPos = placeDust(helper, Direction.SOUTH);
        helper.setBlock(sideDustPos.relative(Direction.WEST), Blocks.REDSTONE_BLOCK);
        meterBlockEntity.tick(helper.getLevel());
        assertDustPower(helper, sideDustPos, 15, "side dust powered by a redstone block");
        assertTransferBlocked(helper, meterBlockEntity, inputEnergyFunction, "control with powered dust");
        assertNoMeterSignal(helper, meterBlockEntity);
        helper.setBlock(sideDustPos.relative(Direction.WEST), Blocks.AIR);
        meterBlockEntity.tick(helper.getLevel());
        assertDustPower(helper, sideDustPos, 0, "side dust after removing the redstone block");
        assertTransferAllowed(helper, meterBlockEntity, inputEnergyFunction, "control after unpowering the dust");

        // test that a new meter reads an existing signal on its first tick
        helper.setBlock(sideDustPos.relative(Direction.WEST), Blocks.REDSTONE_BLOCK);
        helper.setBlock(TestUtils.DEFAULT_POS, Blocks.AIR);
        var newMeterBlockEntity = TestUtils.setupMeter(helper);
        newMeterBlockEntity.getRedstoneHandler().setFunction(RedstoneFunction.CONTROL);
        newMeterBlockEntity.tick(helper.getLevel());
        helper.assertValueEqual(15, newMeterBlockEntity.getRedstoneHandler().getInputSignal(), "input signal of a new meter");
        helper.assertTrue(newMeterBlockEntity.isTransferBlocked(), "new meter next to powered dust should be blocked");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_METER_TESTS)
    public void redstone_throttle(GameTestHelper helper) {
        var redstoneHandler = new RedstoneHandler(() -> {});
        redstoneHandler.setFunction(RedstoneFunction.CONTROL);
        redstoneHandler.setControlBehavior(ControlBehavior.THROTTLE);

        // test that the signal reduces the limit
        assertEffectiveLimit(helper, redstoneHandler, 0, 15_000, TransferMode.SPLIT, 15_000);
        assertEffectiveLimit(helper, redstoneHandler, 5, 15_000, TransferMode.SPLIT, 10_000);
        assertEffectiveLimit(helper, redstoneHandler, 15, 15_000, TransferMode.SPLIT, 0);

        // test that the inverted signal grants the limit
        redstoneHandler.setInverted(true);
        assertEffectiveLimit(helper, redstoneHandler, 0, 15_000, TransferMode.SPLIT, 0);
        assertEffectiveLimit(helper, redstoneHandler, 5, 15_000, TransferMode.SPLIT, 5_000);
        assertEffectiveLimit(helper, redstoneHandler, 15, 15_000, TransferMode.SPLIT, 15_000);
        redstoneHandler.setInverted(false);

        // test that large limits don't overflow
        var expectedLargeLimit = Long.MAX_VALUE / 15 * 10 + Long.MAX_VALUE % 15 * 10 / 15;
        assertEffectiveLimit(helper, redstoneHandler, 5, Long.MAX_VALUE, TransferMode.SPLIT, expectedLargeLimit);
        assertEffectiveLimit(helper, redstoneHandler, 0, Long.MAX_VALUE, TransferMode.SPLIT, Long.MAX_VALUE);

        // test that without a limit only the fully blocking level blocks
        assertEffectiveLimit(helper, redstoneHandler, 5, 0, TransferMode.SPLIT, Long.MAX_VALUE);
        assertEffectiveLimit(helper, redstoneHandler, 15, 0, TransferMode.SPLIT, 0);

        // test that throttle applies the limit in consume mode
        assertEffectiveLimit(helper, redstoneHandler, 5, 15_000, TransferMode.CONSUME, 10_000);

        // test that switch blocks on any signal and ignores the limit in consume mode
        redstoneHandler.setControlBehavior(ControlBehavior.SWITCH);
        assertEffectiveLimit(helper, redstoneHandler, 0, 15_000, TransferMode.SPLIT, 15_000);
        assertEffectiveLimit(helper, redstoneHandler, 5, 15_000, TransferMode.SPLIT, 0);
        assertEffectiveLimit(helper, redstoneHandler, 0, 15_000, TransferMode.CONSUME, Long.MAX_VALUE);

        // set up the plot
        var plotResult = TestUtils.setupSimplePlot(helper);
        var sidePos = TestUtils.DEFAULT_POS.relative(Direction.SOUTH);

        var meterBlockEntity = plotResult.meterBlockEntity();
        var meterRedstoneHandler = meterBlockEntity.getRedstoneHandler();
        var inputEnergyFunction = plotResult.inputEnergyFunctionWithResult();

        meterBlockEntity.setTransferLimit(15_000);
        meterRedstoneHandler.setFunction(RedstoneFunction.CONTROL);
        meterRedstoneHandler.setControlBehavior(ControlBehavior.THROTTLE);

        // test that a full signal blocks the transfer
        helper.setBlock(sidePos, Blocks.REDSTONE_BLOCK);
        meterBlockEntity.tick(helper.getLevel());
        helper.assertValueEqual(0, inputEnergyFunction.apply(20_000, true), "accepted energy with a full signal");
        helper.assertValueEqual(ConnectionStatus.BLOCKED, meterBlockEntity.getConnectionStatus(), "connection status with a full signal");

        // test that a partial signal scales the transfer
        var signalSourceState = Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(DaylightDetectorBlock.POWER, 5);
        helper.setBlock(sidePos, signalSourceState);
        meterBlockEntity.tick(helper.getLevel());
        helper.assertValueEqual(5, meterRedstoneHandler.getInputSignal(), "input signal of the signal source");
        helper.assertValueEqual(10_000, inputEnergyFunction.apply(20_000, true), "accepted energy with a partial signal");
        helper.assertValueEqual(ConnectionStatus.IDLE, meterBlockEntity.getConnectionStatus(), "connection status with a partial signal");

        // test that the limit applies per tick
        helper.assertValueEqual(6_000, inputEnergyFunction.apply(6_000, false), "accepted energy of the first insert");
        helper.assertValueEqual(4_000, inputEnergyFunction.apply(6_000, false), "accepted energy of the second insert");
        meterBlockEntity.getEnergyHandler().tick();

        // test that invert grants the signal strength
        meterRedstoneHandler.setInverted(true);
        helper.assertValueEqual(5_000, inputEnergyFunction.apply(20_000, true), "accepted energy with an inverted partial signal");
        meterRedstoneHandler.setInverted(false);

        // test that throttle applies to consume mode
        meterBlockEntity.setTransferMode(TransferMode.CONSUME);
        helper.assertValueEqual(10_000, inputEnergyFunction.apply(20_000, true), "accepted energy in consume mode");
        meterBlockEntity.setTransferMode(TransferMode.SPLIT);

        // test that a missing limit allows an unlimited transfer
        meterBlockEntity.setTransferLimit(0);
        helper.assertValueEqual(20_000, inputEnergyFunction.apply(20_000, true), "accepted energy without a limit");

        helper.succeed();
    }

    private static void assertSignal(GameTestHelper helper, RedstoneHandler redstoneHandler, double energyRate, int expected) {
        redstoneHandler.refreshOutput(energyRate);
        helper.assertValueEqual(expected, redstoneHandler.getOutputSignal(), "signal for rate " + energyRate);
    }

    private static void assertDustConnection(GameTestHelper helper, BlockPos dustPos, Direction direction, RedstoneSide expected, String message) {
        var dustState = helper.getBlockState(dustPos);
        helper.assertValueEqual(expected, dustState.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction)), message);
    }

    private static void assertDustPower(GameTestHelper helper, BlockPos dustPos, int expected, String message) {
        helper.assertValueEqual(expected, helper.getBlockState(dustPos).getValue(RedStoneWireBlock.POWER), message);
    }

    private static void assertTransferAllowed(
        GameTestHelper helper,
        MeterBlockEntity meterBlockEntity,
        BiFunction<Integer, Boolean, Integer> inputEnergyFunction,
        String message
    ) {
        helper.assertFalse(meterBlockEntity.isTransferBlocked(), message + " should not be blocked");
        helper.assertValueEqual(1_000, inputEnergyFunction.apply(1_000, true), message + " accepted energy");
        helper.assertValueEqual(ConnectionStatus.IDLE, meterBlockEntity.getConnectionStatus(), message + " connection status");
    }

    private static void assertTransferBlocked(
        GameTestHelper helper,
        MeterBlockEntity meterBlockEntity,
        BiFunction<Integer, Boolean, Integer> inputEnergyFunction,
        String message
    ) {
        helper.assertTrue(meterBlockEntity.isTransferBlocked(), message + " should be blocked");
        helper.assertValueEqual(0, inputEnergyFunction.apply(1_000, true), message + " accepted energy");
        helper.assertValueEqual(ConnectionStatus.BLOCKED, meterBlockEntity.getConnectionStatus(), message + " connection status");
    }

    private static void assertNoMeterSignal(GameTestHelper helper, MeterBlockEntity meterBlockEntity) {
        var meterState = meterBlockEntity.getBlockState();
        var meterPos = meterBlockEntity.getBlockPos();

        for (var direction : Direction.values()) {
            var signal = meterState.getSignal(helper.getLevel(), meterPos, direction);
            helper.assertValueEqual(0, signal, "meter signal towards " + direction);
        }
    }

    private static void measureRate(MeterBlockEntity meterBlockEntity, Consumer<Integer> inputEnergyFunction, int energyRate) {
        inputEnergyFunction.accept(energyRate * MEASURE_INTERVAL);
        meterBlockEntity.refreshEnergyValues();
    }

    private static BlockPos placeDust(GameTestHelper helper, Direction direction) {
        var dustPos = TestUtils.DEFAULT_POS.relative(direction);
        placeDustBlock(helper, dustPos);
        // second dust to prevent the single dust from turning into a cross that connects to every side
        placeDustBlock(helper, dustPos.relative(Direction.WEST));

        return dustPos;
    }

    private static void placeDustBlock(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.REDSTONE_WIRE);
    }

    private static void assertEffectiveLimit(
        GameTestHelper helper,
        RedstoneHandler redstoneHandler,
        int inputSignal,
        long transferLimit,
        TransferMode transferMode,
        long expected
    ) {
        redstoneHandler.refreshInput(inputSignal);
        var effectiveTransferLimit = redstoneHandler.getEffectiveTransferLimit(transferLimit, transferMode);
        helper.assertValueEqual(expected, effectiveTransferLimit, "effective limit for signal " + inputSignal + ", limit " + transferLimit);
    }
}
