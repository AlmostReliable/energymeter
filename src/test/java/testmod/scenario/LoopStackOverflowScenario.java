package testmod.scenario;

import com.almostreliable.energymeter.block.component.IoConfig.IoSettingWithPriority;
import com.almostreliable.energymeter.block.component.MeterEnergyHandler;
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

import testmod.TestRegistration;
import testmod.TestUtils;
import testmod.content.EnergyReceiverBlockEntity;

import java.util.Objects;
import java.util.function.LongSupplier;

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

    private static final BlockPos METER_POS = TestUtils.DEFAULT_POS;

    public static void loopWithoutConsumer(GameTestHelper helper) {
        // set up the plot
        MeterBlockEntity meter = setupLoop(helper);

        EnergyHandler input = input(meter, Direction.WEST);

        // insert energy into the loop
        long inserted = withoutOverflow(helper, () -> TestUtils.insertEnergy(input, 1_000, false));

        // check that no energy was accepted or measured
        assertEquals(helper, 0, inserted, "inserted energy");
        assertEquals(helper, 0, meter.getEnergyHandler().getEnergyPerInterval(), "throughput");
        helper.succeed();
    }

    public static void loopWithConsumer(GameTestHelper helper) {
        // set up a loop with a consumer that only accepts part of the energy, so the cable offers the rest to the meter
        MeterBlockEntity meter = setupLoop(helper);
        EnergyReceiverBlockEntity receiver = placeReceiver(helper, METER_POS.east().south(), 400);
        EnergyHandler input = input(meter, Direction.WEST);

        // insert energy into the loop
        long inserted = withoutOverflow(helper, () -> TestUtils.insertEnergy(input, 1_000, false));

        // check that the energy reached the consumer and was measured once
        assertEquals(helper, 400, inserted, "inserted energy");
        assertEquals(helper, 400, amount(receiver), "consumer energy");
        assertEquals(helper, 400, meter.getEnergyHandler().getEnergyPerInterval(), "throughput");

        // insert again to check that the guard was released
        inserted = withoutOverflow(helper, () -> TestUtils.insertEnergy(input, 1_000, false));
        assertEquals(helper, 400, inserted, "second inserted energy");
        assertEquals(helper, 800, amount(receiver), "second consumer energy");
        assertEquals(helper, 800, meter.getEnergyHandler().getEnergyPerInterval(), "second throughput");
        helper.succeed();
    }

    public static void loopRollback(GameTestHelper helper) {
        // set up a loop with a consumer that only accepts part of the energy
        MeterBlockEntity meter = setupLoop(helper);
        EnergyReceiverBlockEntity receiver = placeReceiver(helper, METER_POS.east().south(), 400);
        EnergyHandler input = input(meter, Direction.WEST);

        // insert energy into the loop and let the transaction roll back
        long simulated = withoutOverflow(helper, () -> {
            try (Transaction transaction = Transaction.openRoot()) {
                return input.insert(1_000, transaction);
            }
        });
        assertEquals(helper, 400, simulated, "simulated energy");

        // check that the rollback left no state behind
        assertEquals(helper, 0, amount(receiver), "rolled-back consumer energy");
        assertEquals(helper, 0, meter.getEnergyHandler().getEnergyPerInterval(), "rolled-back throughput");
        helper.succeed();
    }

    public static void loopEnergyQueries(GameTestHelper helper) {
        // set up a loop with a partially filled consumer
        MeterBlockEntity meter = setupLoop(helper);
        EnergyReceiverBlockEntity receiver = placeReceiver(helper, METER_POS.east().south(), 1_000);
        receiver.setCapacity(5_000);
        TestUtils.insertEnergy(receiverCap(receiver), 1_000, false);
        EnergyHandler input = input(meter, Direction.WEST);

        // query the energy metadata through the loop
        long amount = withoutOverflow(helper, input::getAmountAsLong);
        long capacity = withoutOverflow(helper, input::getCapacityAsLong);

        // check that only the consumer is reported
        assertEquals(helper, 1_000, amount, "reported amount");
        assertEquals(helper, 5_000, capacity, "reported capacity");
        helper.succeed();
    }

    public static void loopTwoMeters(GameTestHelper helper) {
        // set up two meters that feed each other through cables
        MeterBlockEntity first = setupMeter(helper, METER_POS, Direction.WEST, Direction.EAST);
        MeterBlockEntity second = setupMeter(helper, METER_POS.below(), Direction.EAST, Direction.WEST);
        placeCables(helper, METER_POS.east(), METER_POS.east().below());
        placeCables(helper, METER_POS.west().below(), METER_POS.west());

        // attach a consumer that only accepts part of the energy to the cable between the meters
        EnergyReceiverBlockEntity receiver = placeReceiver(helper, METER_POS.west().below().north(), 400);
        EnergyHandler input = input(first, Direction.WEST);

        // insert energy into the loop
        long inserted = withoutOverflow(helper, () -> TestUtils.insertEnergy(input, 1_000, false));

        // check that the energy passed both meters once
        assertEquals(helper, 400, inserted, "inserted energy");
        assertEquals(helper, 400, amount(receiver), "consumer energy");
        assertEquals(helper, 400, first.getEnergyHandler().getEnergyPerInterval(), "first throughput");
        assertEquals(helper, 400, second.getEnergyHandler().getEnergyPerInterval(), "second throughput");
        helper.succeed();
    }

    public static void sharedInputNetwork(GameTestHelper helper) {
        // set up a meter with two inputs on the same cable network and a consumer that limits each insertion
        var plot = TestUtils.PlotBuilder.create(helper)
            .inputs(Direction.WEST, Direction.NORTH)
            .output(Direction.EAST)
            .build();
        MeterBlockEntity meter = plot.meterBlockEntity();
        EnergyReceiverBlockEntity receiver = plot.outputEnergyBlockEntities().get(Direction.EAST);
        receiver.setMaxReceive(600);
        BlockPos cornerPos = METER_POS.west().north();
        placeCables(helper, METER_POS.west(), cornerPos, METER_POS.north());

        // insert energy into the cable network, which offers the energy to both inputs in sequence
        EnergyHandler network = Objects.requireNonNull(
            helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(cornerPos), null)
        );
        long inserted = withoutOverflow(helper, () -> TestUtils.insertEnergy(network, 1_000, false));

        // check that both inputs forwarded energy
        assertEquals(helper, 1_000, inserted, "inserted energy");
        assertEquals(helper, 1_000, amount(receiver), "consumer energy");
        assertEquals(helper, 1_000, meter.getEnergyHandler().getEnergyPerInterval(), "throughput");
        helper.succeed();
    }

    private static MeterBlockEntity setupLoop(GameTestHelper helper) {
        MeterBlockEntity meter = setupMeter(helper, METER_POS, Direction.WEST, Direction.EAST);
        placeCables(helper, METER_POS.east(), METER_POS.east().north(), METER_POS.north(), METER_POS.west().north(), METER_POS.west());
        return meter;
    }

    private static MeterBlockEntity setupMeter(GameTestHelper helper, BlockPos pos, Direction input, Direction output) {
        helper.setBlock(pos, ModRegistration.METER_BLOCK.get());
        MeterBlockEntity meter = helper.getBlockEntity(pos, MeterBlockEntity.class);
        meter.getIoConfig().setSetting(input, IoSettingWithPriority.IN);
        meter.getIoConfig().setSetting(output, IoSettingWithPriority.OUT_DEFAULT);
        return meter;
    }

    private static void placeCables(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos, TestRegistration.ENERGY_CABLE_BLOCK.get());
        }
    }

    private static EnergyReceiverBlockEntity placeReceiver(GameTestHelper helper, BlockPos pos, int maxReceive) {
        helper.setBlock(pos, TestRegistration.ENERGY_RECEIVER_BLOCK.get());
        EnergyReceiverBlockEntity receiver = helper.getBlockEntity(pos, EnergyReceiverBlockEntity.class);
        receiver.setMaxReceive(maxReceive);
        return receiver;
    }

    private static EnergyHandler input(MeterBlockEntity meter, Direction direction) {
        return Objects.requireNonNull(meter.getEnergyCapability(direction));
    }

    private static EnergyHandler receiverCap(EnergyReceiverBlockEntity receiver) {
        return Objects.requireNonNull(receiver.getEnergyCapability(null));
    }

    private static long amount(EnergyReceiverBlockEntity receiver) {
        return receiverCap(receiver).getAmountAsLong();
    }

    private static long withoutOverflow(GameTestHelper helper, LongSupplier action) {
        try {
            return action.getAsLong();
        } catch (StackOverflowError e) {
            throw new GameTestAssertException(
                Component.literal("energy loop caused a stack overflow"),
                (int) helper.getTick()
            );
        }
    }

    private static void assertEquals(GameTestHelper helper, long expected, long actual, String subject) {
        helper.assertTrue(expected == actual, subject + ": expected " + expected + ", got " + actual);
    }
}
