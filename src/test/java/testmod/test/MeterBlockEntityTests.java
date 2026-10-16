package testmod.test;

import com.almostreliable.energymeter.block.component.ForwardingEnergyStorage;
import com.almostreliable.energymeter.block.component.IoConfig;
import com.almostreliable.energymeter.block.component.IoConfig.IoSettingWithPriority;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.ConnectionStatus;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.MeasureMode;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EmptyEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import testmod.TestUtils;

public class MeterBlockEntityTests {

    public static void defaults(GameTestHelper helper) {
        MeterBlockEntity meterBlockEntity = TestUtils.setupMeter(helper);

        // test whether io configuration has correct default values
        IoConfig ioConfig = meterBlockEntity.getIoConfig();
        helper.assertFalse(ioConfig.hasInput(), "energy meter should not have input by default");
        helper.assertFalse(ioConfig.hasOutput(), "energy meter should not have output by default");
        helper.assertFalse(ioConfig.hasChanged(), "energy meter io config should not be marked as changed by default");

        // test enum defaults
        helper.assertValueEqual(meterBlockEntity.getTransferMode(), TransferMode.SPLIT, "transfer mode");
        helper.assertValueEqual(meterBlockEntity.getMeasureMode(), MeasureMode.INSTANT, "measure mode");
        helper.assertValueEqual(meterBlockEntity.getConnectionStatus(), ConnectionStatus.DISCONNECTED, "connection status");

        // test limit default
        helper.assertValueEqual(meterBlockEntity.getTransferLimit(), 0L, "transfer limit");
        helper.succeed();
    }

    public static void energy_cap(GameTestHelper helper) {
        MeterBlockEntity meterBlockEntity = TestUtils.setupMeter(helper);

        // test capability access without io configuration
        for (Direction direction : Direction.values()) {
            EnergyHandler energyCap = helper.getLevel().getCapability(
                Capabilities.Energy.BLOCK,
                helper.absolutePos(TestUtils.DEFAULT_POS),
                direction
            );
            TestUtils.assertNull(helper, energyCap, "energy meter should not expose any energy capability by default");
        }
        EnergyHandler energyCapWithoutContext = helper.getLevel().getCapability(
            Capabilities.Energy.BLOCK,
            helper.absolutePos(TestUtils.DEFAULT_POS),
            null
        );
        TestUtils.assertIdentity(
            helper,
            energyCapWithoutContext,
            EmptyEnergyHandler.INSTANCE,
            "energy meter should only expose a non-modifiable energy storage when accessed without context"
        );

        // set io configuration, west to input, east to output
        meterBlockEntity.getIoConfig().setSetting(Direction.WEST, IoSettingWithPriority.IN);
        meterBlockEntity.getIoConfig().setSetting(Direction.EAST, IoSettingWithPriority.OUT_DEFAULT);

        // test whether energy input capability exposes correct handler
        EnergyHandler inputEnergyCap = helper.getLevel().getCapability(
            Capabilities.Energy.BLOCK,
            helper.absolutePos(TestUtils.DEFAULT_POS),
            Direction.WEST
        );
        TestUtils.assertNotNull(helper, inputEnergyCap, "energy meter should expose an input energy capability");
        TestUtils.assertInstanceOf(
            helper,
            inputEnergyCap,
            ForwardingEnergyStorage.class,
            "energy meter input capability should be an energy handler"
        );

        // test whether energy input capability handles extraction and insertion correctly
        helper.assertTrue(extract(inputEnergyCap, 1_000) == 0, "energy meter input capability should not allow extraction");

        // test whether energy output capability exposes correct handler
        EnergyHandler outputEnergyCap = helper.getLevel().getCapability(
            Capabilities.Energy.BLOCK,
            helper.absolutePos(TestUtils.DEFAULT_POS),
            Direction.EAST
        );
        TestUtils.assertNotNull(helper, outputEnergyCap, "energy meter should expose an output energy capability");
        TestUtils.assertInstanceOf(
            helper,
            outputEnergyCap,
            ForwardingEnergyStorage.class,
            "energy meter output capability should be an energy handler"
        );

        // test whether energy output capability handles extraction and insertion correctly
        helper.assertTrue(extract(outputEnergyCap, 1_000) == 0, "energy meter output capability should not allow extraction");
        helper.assertTrue(
            TestUtils.insertEnergy(outputEnergyCap, 1_000, true) == 0,
            "energy meter output capability should not be able to receive energy"
        );

        helper.succeed();
    }

    public static void meter_connection(GameTestHelper helper) {
        // set up the plot
        TestUtils.setupSimplePlot(helper);
        TestUtils.setupMeter(helper, TestUtils.DEFAULT_POS.relative(Direction.WEST));

        // try to access the meter's energy capability from a direction where another meter is
        TestUtils.assertNull(
            helper,
            helper.getLevel().getCapability(
                Capabilities.Energy.BLOCK,
                helper.absolutePos(TestUtils.DEFAULT_POS),
                Direction.WEST
            ),
            "energy meter should not allow connections to other meters"
        );

        helper.succeed();
    }

    private static int extract(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(amount, transaction);
            transaction.commit();
            return extracted;
        }
    }
}
