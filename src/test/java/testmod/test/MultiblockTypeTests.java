package testmod.test;

import com.almostreliable.energymeter.block.multiblock.MultiblockType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import testmod.TestMod;
import testmod.TestUtils;

import java.util.EnumSet;
import java.util.Set;

import static com.almostreliable.energymeter.block.multiblock.MultiblockType.*;

@SuppressWarnings("NewMethodNamingConvention")
@GameTestHolder(TestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class MultiblockTypeTests {

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_LOGIC_TESTS)
    public void from_borders(GameTestHelper helper) {
        var allBorders = MultiblockType.fromBorders(true, true, true, true);
        var noBorders = MultiblockType.fromBorders(false, false, false, false);
        var upDownBorders = MultiblockType.fromBorders(false, false, true, true);
        var leftUpBorders = MultiblockType.fromBorders(true, false, true, false);

        helper.assertValueEqual(NORMAL, allBorders, "type with all borders");
        helper.assertValueEqual(MIDDLE, noBorders, "type without borders");
        helper.assertValueEqual(HORIZONTAL, upDownBorders, "type with up and down borders");
        helper.assertValueEqual(CORNER_UL, leftUpBorders, "type with left and up borders");

        Set<MultiblockType> types = EnumSet.noneOf(MultiblockType.class);
        for (int mask = 0; mask < 16; mask++) {
            var type = MultiblockType.fromBorders((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
            types.add(type);
        }

        helper.assertValueEqual(MultiblockType.values().length, types.size(), "distinct types for all border combinations");
        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_LOGIC_TESTS)
    public void face_types(GameTestHelper helper) {
        helper.assertValueEqual(NORMAL, NORMAL.getUpDownFaceType(), "up/down face of normal");
        helper.assertValueEqual(NORMAL, NORMAL.getLeftRightFaceType(), "left/right face of normal");
        helper.assertValueEqual(HORIZONTAL, SIDE_U.getUpDownFaceType(), "up/down face of side_u");
        helper.assertValueEqual(VERTICAL, SIDE_L.getLeftRightFaceType(), "left/right face of side_l");
        helper.assertValueEqual(CORNER_L, CORNER_UL.getUpDownFaceType(), "up/down face of corner_ul");
        helper.assertValueEqual(CORNER_U, CORNER_UL.getLeftRightFaceType(), "left/right face of corner_ul");
        helper.assertValueEqual(NORMAL, CORNER_U.getUpDownFaceType(), "up/down face of corner_u");
        helper.assertValueEqual(CORNER_D, CORNER_DR.getLeftRightFaceType(), "left/right face of corner_dr");

        helper.succeed();
    }

    @GameTest(template = TestUtils.EMPTY_STRUCTURE, batch = TestUtils.BATCH_LOGIC_TESTS)
    public void sheet_cells(GameTestHelper helper) {
        // a single block takes its quarters from the four outer corner cells
        assertSheetCells(helper, NORMAL, 0, 2, 0, 2);
        // a fully connected block takes every quarter from the center cell
        assertSheetCells(helper, MIDDLE, 1, 1, 1, 1);
        // the middle of a one block tall row combines the top and bottom edge cells
        assertSheetCells(helper, HORIZONTAL, 1, 1, 0, 2);
        assertSheetCells(helper, CORNER_UL, 0, 1, 0, 1);

        helper.succeed();
    }

    private static void assertSheetCells(
        GameTestHelper helper,
        MultiblockType type,
        int leftColumn,
        int rightColumn,
        int topRow,
        int bottomRow
    ) {
        helper.assertValueEqual(leftColumn, type.getSheetColumn(false), "left sheet column of " + type);
        helper.assertValueEqual(rightColumn, type.getSheetColumn(true), "right sheet column of " + type);
        helper.assertValueEqual(topRow, type.getSheetRow(false), "top sheet row of " + type);
        helper.assertValueEqual(bottomRow, type.getSheetRow(true), "bottom sheet row of " + type);
    }
}
