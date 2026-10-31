package testmod.test;

import com.almostreliable.energymeter.block.MonitorBlock;
import com.almostreliable.energymeter.block.multiblock.MultiblockType;
import com.almostreliable.energymeter.core.Registration;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import testmod.TestMod;
import testmod.TestUtils;

import static com.almostreliable.energymeter.block.multiblock.MultiblockType.*;

@SuppressWarnings("NewMethodNamingConvention")
@GameTestHolder(TestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class MonitorBlockTests {

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_MONITOR_TESTS)
    public void form_single(GameTestHelper helper) {
        assertFormedTypes(helper, new MultiblockType[][]{
            {NORMAL}
        });
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_MONITOR_TESTS)
    public void form_row(GameTestHelper helper) {
        assertFormedTypes(helper, new MultiblockType[][]{
            {CORNER_L, HORIZONTAL, CORNER_R}
        });
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_MONITOR_TESTS)
    public void form_column(GameTestHelper helper) {
        assertFormedTypes(helper, new MultiblockType[][]{
            {CORNER_U},
            {VERTICAL},
            {CORNER_D}
        });
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_MONITOR_TESTS)
    public void form_square(GameTestHelper helper) {
        assertFormedTypes(helper, new MultiblockType[][]{
            {CORNER_UL, SIDE_U, CORNER_UR},
            {SIDE_L, MIDDLE, SIDE_R},
            {CORNER_DL, SIDE_D, CORNER_DR}
        });
    }

    private static void assertFormedTypes(GameTestHelper helper, MultiblockType[][] typesByRow) {
        int height = typesByRow.length;
        int width = typesByRow[0].length;
        var monitorState = Registration.MONITOR_BLOCK.get().defaultBlockState();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var blockPos = monitorPos(x, y);
                helper.setBlock(blockPos, monitorState);
            }
        }

        var relativePos = monitorPos(0, 0);
        helper.useBlock(relativePos);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var expectedType = typesByRow[height - 1 - y][x];
                var pos = monitorPos(x, y);
                helper.assertBlockProperty(pos, MonitorBlock.TYPE, expectedType);
            }
        }

        helper.succeed();
    }

    // maps front-view coordinates (x from the left, y from the bottom) to structure positions
    // x is mirrored because the monitor faces north, so its left edge is at the structure's east side
    private static BlockPos monitorPos(int x, int y) {
        return new BlockPos(2 - x, 1 + y, 1);
    }
}
