package testmod.test;

import com.almostreliable.energymeter.block.MeterBlock;
import com.almostreliable.energymeter.block.component.RedstoneHandler;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneFunction;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneScale;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

import testmod.TestUtils;

import java.util.function.Consumer;

public class MeterRedstoneTests {

    private static final int MEASURE_INTERVAL = 5;

    public static void signal(GameTestHelper helper) {
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

    public static void redstone_connection(GameTestHelper helper) {
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

    public static void redstone_output(GameTestHelper helper) {
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

    public static void redstone_disconnected_inverted(GameTestHelper helper) {
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

    private static void assertSignal(GameTestHelper helper, RedstoneHandler redstoneHandler, double energyRate, int expected) {
        redstoneHandler.refreshSignal(energyRate);
        helper.assertValueEqual(expected, redstoneHandler.getOutputSignal(), "signal for rate " + energyRate);
    }

    private static void assertDustConnection(GameTestHelper helper, BlockPos dustPos, Direction direction, RedstoneSide expected, String message) {
        var dustState = helper.getBlockState(dustPos);
        helper.assertValueEqual(expected, dustState.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction)), message);
    }

    private static void assertDustPower(GameTestHelper helper, BlockPos dustPos, int expected, String message) {
        helper.assertValueEqual(expected, helper.getBlockState(dustPos).getValue(RedStoneWireBlock.POWER), message);
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
}
