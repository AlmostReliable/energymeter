package testmod.scenario;

import com.almostreliable.energymeter.block.component.EnergyHandlerHost;
import com.almostreliable.energymeter.block.component.ForwardingEnergyStorage;
import com.almostreliable.energymeter.block.component.IoConfig;
import com.almostreliable.energymeter.block.component.IoConfig.IoSettingWithPriority;
import com.almostreliable.energymeter.block.component.MeterEnergyHandler;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;

/**
 * This scenario tests a bug found during the 26.1 port review.
 * <p>
 * The input-facing {@link ForwardingEnergyStorage} reports the combined amount and capacity
 * of every output handler. After these metadata getters changed from {@code int} to {@code long},
 * their values were added without overflow protection. Enough large outputs could therefore
 * wrap either total to a negative value.
 * <p>
 * Invalid third-party handlers can also return negative metadata. Such values must not reduce
 * otherwise valid totals.
 * <p>
 * This was fixed by ignoring non-positive metadata and saturating each total at
 * {@link Long#MAX_VALUE} before an addition can overflow.
 */
public final class EnergyMetadataOverflowScenario {

    private EnergyMetadataOverflowScenario() {}

    public static void test(GameTestHelper helper) {
        // combine valid metadata with invalid negative values
        ForwardingEnergyStorage defensiveStorage = createStorage(List.of(
            metadataHandler(40, 80),
            metadataHandler(-1, -1),
            metadataHandler(2, 3)
        ));

        // verify invalid metadata does not reduce either aggregate
        helper.assertValueEqual(
            42L,
            defensiveStorage.getAmountAsLong(),
            "aggregated amount with invalid metadata"
        );
        helper.assertValueEqual(
            83L,
            defensiveStorage.getCapacityAsLong(),
            "aggregated capacity with invalid metadata"
        );

        // combine handlers whose amount and capacity exceed the largest representable long
        ForwardingEnergyStorage overflowingStorage = createStorage(List.of(
            metadataHandler(Long.MAX_VALUE - 10, Long.MAX_VALUE - 20),
            metadataHandler(6, 11),
            metadataHandler(5, 10)
        ));

        // verify both aggregates saturate instead of wrapping to a negative value
        helper.assertValueEqual(
            Long.MAX_VALUE,
            overflowingStorage.getAmountAsLong(),
            "overflowing aggregated amount"
        );
        helper.assertValueEqual(
            Long.MAX_VALUE,
            overflowingStorage.getCapacityAsLong(),
            "overflowing aggregated capacity"
        );
        helper.succeed();
    }

    private static ForwardingEnergyStorage createStorage(List<EnergyHandler> outputs) {
        MeterEnergyHandler meterHandler = new MeterEnergyHandler(new StubHost()) {
            @Override
            public Iterable<EnergyHandler> getValidOutputEnergyStorages() {
                return outputs;
            }
        };
        return new ForwardingEnergyStorage(meterHandler, () -> IoSettingWithPriority.IN);
    }

    private static EnergyHandler metadataHandler(long amount, long capacity) {
        return new EnergyHandler() {
            @Override
            public int insert(int maxAmount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int extract(int maxAmount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public long getAmountAsLong() {
                return amount;
            }

            @Override
            public long getCapacityAsLong() {
                return capacity;
            }
        };
    }

    private static final class StubHost implements EnergyHandlerHost {

        private final IoConfig ioConfig = new IoConfig();

        @Override
        public Level getLevel() {
            return null;
        }

        @Override
        public BlockPos getBlockPos() {
            return BlockPos.ZERO;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public IoConfig getIoConfig() {
            return ioConfig;
        }

        @Override
        public TransferMode getTransferMode() {
            return TransferMode.SPLIT;
        }

        @Override
        public int getMeasureInterval() {
            return 1;
        }

        @Override
        public int getZeroTolerance() {
            return 0;
        }

        @Override
        public long getTransferLimit() {
            return 0;
        }
    }
}
