package testmod;

import com.almostreliable.energymeter.block.component.IoConfig.IoSettingWithPriority;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.core.ModRegistration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import testmod.content.EnergyReceiverBlockEntity;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class TestUtils {

    public static final String EMPTY_STRUCTURE = "empty_test_structure";
    public static final String BATCH_METER_TESTS = "meter_tests";
    public static final String BATCH_LOGIC_TESTS = "logic_tests";
    public static final String BATCH_SCENARIOS = "scenarios";
    public static final BlockPos DEFAULT_POS = new BlockPos(1, 2, 1);

    private TestUtils() {}

    public static void assertNull(GameTestHelper helper, @Nullable Object o, String failureMessage) {
        helper.assertTrue(o == null, failureMessage);
    }

    public static void assertNotNull(GameTestHelper helper, @Nullable Object o, String failureMessage) {
        helper.assertTrue(o != null, failureMessage);
    }

    public static void assertInstanceOf(GameTestHelper helper, Object o, Class<?> clazz, String failureMessage) {
        helper.assertTrue(clazz.isInstance(o), failureMessage);
    }

    public static void assertIdentity(GameTestHelper helper, @Nullable Object o, Object expected, String failureMessage) {
        helper.assertTrue(o == expected, failureMessage);
    }

    public static int getRandomEnergyRate() {
        return 1_000 + (int) (Math.random() * 9_000);
    }

    public static int getRandomDivisibleEnergyRate(int divisor) {
        var randomEnergyRate = getRandomEnergyRate();
        return randomEnergyRate - (randomEnergyRate % divisor);
    }

    public static MeterBlockEntity setupMeter(GameTestHelper helper) {
        return setupMeter(helper, DEFAULT_POS);
    }

    public static MeterBlockEntity setupMeter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModRegistration.METER_BLOCK.get());
        return helper.getBlockEntity(pos, MeterBlockEntity.class);
    }

    public static EnergyReceiverBlockEntity placeReceiver(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TestRegistration.ENERGY_RECEIVER_BLOCK.get());
        return helper.getBlockEntity(pos, EnergyReceiverBlockEntity.class);
    }

    public static void placeCables(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos, TestRegistration.ENERGY_CABLE_BLOCK.get());
        }
    }

    public static EnergyHandler getEnergyCap(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        var capability = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
        if (capability == null) {
            throw new GameTestAssertException(
                Component.literal("expected an energy capability at " + pos + " on side " + side),
                (int) helper.getTick()
            );
        }
        return capability;
    }

    public static int insertEnergy(EnergyHandler handler, int amount, boolean simulate) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            if (!simulate) transaction.commit();
            return inserted;
        }
    }

    public static SimplePlotResult setupSimplePlot(GameTestHelper helper) {
        var plotResult = PlotBuilder.create(helper).input(Direction.WEST).output(Direction.EAST).build();

        return new SimplePlotResult(
            plotResult.meterBlockEntity,
            plotResult.inputEnergyFunctions.get(Direction.WEST),
            plotResult.inputEnergyFunctionsWithResult.get(Direction.WEST),
            plotResult.outputEnergyBlockEntities.get(Direction.EAST),
            plotResult.outputEnergyBlockCaps.get(Direction.EAST)
        );
    }

    public record SimplePlotResult(
        MeterBlockEntity meterBlockEntity,
        Consumer<Integer> inputEnergyFunction,
        BiFunction<Integer, Boolean, Integer> inputEnergyFunctionWithResult,
        EnergyReceiverBlockEntity outputEnergyBlockEntity,
        EnergyHandler outputEnergyBlockCap
    ) {}

    public static final class PlotBuilder {

        private final GameTestHelper helper;
        private final BlockPos meterPos;
        private final MeterBlockEntity meterBlockEntity;
        private final Map<Direction, BiFunction<Integer, Boolean, Integer>> inputEnergyFunctionsWithResult = new EnumMap<>(Direction.class);
        private final Map<Direction, EnergyReceiverBlockEntity> outputEnergyBlockEntities = new EnumMap<>(Direction.class);
        private final Map<Direction, EnergyHandler> outputEnergyBlockCaps = new EnumMap<>(Direction.class);

        private PlotBuilder(GameTestHelper helper, BlockPos meterPos, MeterBlockEntity meterBlockEntity) {
            this.helper = helper;
            this.meterPos = meterPos;
            this.meterBlockEntity = meterBlockEntity;
        }

        public static PlotBuilder create(GameTestHelper helper) {
            return create(helper, DEFAULT_POS);
        }

        public static PlotBuilder create(GameTestHelper helper, BlockPos meterPos) {
            MeterBlockEntity meterBlockEntity = setupMeter(helper, meterPos);
            return new PlotBuilder(helper, meterPos, meterBlockEntity);
        }

        public PlotBuilder input(Direction inputDirection) {
            // set io input configuration
            meterBlockEntity.getIoConfig().setSetting(inputDirection, IoSettingWithPriority.IN);

            // get the registered energy capability of the meter on the configured side
            var capability = getEnergyCap(helper, meterPos, inputDirection);
            inputEnergyFunctionsWithResult.put(
                inputDirection,
                (energy, simulate) -> insertEnergy(capability, energy, simulate)
            );

            return this;
        }

        public PlotBuilder inputs(Direction... inputDirections) {
            for (Direction inputDirection : inputDirections) {
                input(inputDirection);
            }

            return this;
        }

        public PlotBuilder output(Direction outputDirection, int priority) {
            // set io output configuration
            meterBlockEntity.getIoConfig().setSetting(outputDirection, IoSettingWithPriority.priorityOutput(priority));

            // place the test energy block on the configured side
            BlockPos outputPos = meterPos.relative(outputDirection);
            EnergyReceiverBlockEntity outputEnergyBlockEntity = placeReceiver(helper, outputPos);

            // test whether the energy block is empty
            EnergyHandler outputEnergyBlockCap = getEnergyCap(helper, outputPos, null);
            helper.assertTrue(outputEnergyBlockCap.getAmountAsInt() == 0, "output energy block should be empty");

            outputEnergyBlockEntities.put(outputDirection, outputEnergyBlockEntity);
            outputEnergyBlockCaps.put(outputDirection, outputEnergyBlockCap);

            return this;
        }

        public PlotBuilder output(Direction outputDirection) {
            return output(outputDirection, 1);
        }

        public PlotBuilder cableOutput(Direction outputDirection) {
            // set io output configuration
            meterBlockEntity.getIoConfig().setSetting(outputDirection, IoSettingWithPriority.OUT_DEFAULT);

            // place a test cable on the configured side
            placeCables(helper, meterPos.relative(outputDirection));

            return this;
        }

        public PlotBuilder outputs(Direction... outputDirections) {
            for (Direction outputDirection : outputDirections) {
                output(outputDirection);
            }

            return this;
        }

        public Result build() {
            assert outputEnergyBlockEntities.size() == outputEnergyBlockCaps.size();

            var inputEnergyFunctions = new EnumMap<Direction, Consumer<Integer>>(Direction.class);
            for (var entry : inputEnergyFunctionsWithResult.entrySet()) {
                var inputDirection = entry.getKey();
                var inputEnergyFunction = entry.getValue();
                inputEnergyFunctions.put(inputDirection, energy -> inputEnergyFunction.apply(energy, false));
            }

            return new Result(
                meterBlockEntity,
                inputEnergyFunctions,
                inputEnergyFunctionsWithResult,
                outputEnergyBlockEntities,
                outputEnergyBlockCaps
            );
        }

        public record Result(
            MeterBlockEntity meterBlockEntity,
            Map<Direction, Consumer<Integer>> inputEnergyFunctions,
            Map<Direction, BiFunction<Integer, Boolean, Integer>> inputEnergyFunctionsWithResult,
            Map<Direction, EnergyReceiverBlockEntity> outputEnergyBlockEntities,
            Map<Direction, EnergyHandler> outputEnergyBlockCaps
        ) {}
    }
}
