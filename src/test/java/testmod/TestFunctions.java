package testmod;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import org.jspecify.annotations.Nullable;

import testmod.scenario.EnergyMetadataOverflowScenario;
import testmod.scenario.ExcessEnergyVoidingScenario;
import testmod.scenario.SplitNoDoubleInsertScenario;
import testmod.test.MathExpressionParserTests;
import testmod.test.MeterBlockEntityTests;
import testmod.test.MeterBlockTests;
import testmod.test.MeterConsumeTests;
import testmod.scenario.LoopStackOverflowScenario;
import testmod.test.MeterSplitTests;
import testmod.test.MeterTransferLimitTests;
import testmod.test.MeterTransferTests;
import testmod.test.MeterTransactionTests;
import testmod.test.MeterZeroToleranceTests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class TestFunctions {

    private static final int MAX_TICKS = 100;

    private static final Map<Identifier, Consumer<GameTestHelper>> FUNCTIONS = new LinkedHashMap<>();
    private static final List<TestEntry> ENTRIES = new ArrayList<>();

    static {
        var logic = TestUtils.BATCH_LOGIC_TESTS;
        register(logic, "test_basic_numbers", MathExpressionParserTests::testBasicNumbers);
        register(logic, "test_basic_arithmetic", MathExpressionParserTests::testBasicArithmetic);
        register(logic, "test_operator_precedence", MathExpressionParserTests::testOperatorPrecedence);
        register(logic, "test_power_operations", MathExpressionParserTests::testPowerOperations);
        register(logic, "test_negative_numbers", MathExpressionParserTests::testNegativeNumbers);
        register(logic, "test_parentheses", MathExpressionParserTests::testParentheses);
        register(logic, "test_complex_expressions", MathExpressionParserTests::testComplexExpressions);
        register(logic, "test_whitespace", MathExpressionParserTests::testWhitespace);
        register(logic, "test_invalid_expressions", MathExpressionParserTests::testInvalidExpressions);
        register(logic, "test_negative_powers", MathExpressionParserTests::testNegativePowers);
        register(logic, "test_large_numbers", MathExpressionParserTests::testLargeNumbers);
        register(logic, "test_precision_and_rounding", MathExpressionParserTests::testPrecisionAndRounding);

        var meter = TestUtils.BATCH_METER_TESTS;
        register(meter, "neighbor_update_cache_invalidation", MeterBlockTests::neighbor_update_cache_invalidation);

        register(meter, "defaults", MeterBlockEntityTests::defaults);
        register(meter, "energy_cap", MeterBlockEntityTests::energy_cap);
        register(meter, "meter_connection", MeterBlockEntityTests::meter_connection);

        register(meter, "consume_single", MeterConsumeTests::consume_single);
        register(meter, "consume_trio", MeterConsumeTests::consume_trio);

        register(meter, "loop_without_consumer", LoopStackOverflowScenario::loopWithoutConsumer);
        register(meter, "loop_with_consumer", LoopStackOverflowScenario::loopWithConsumer);
        register(meter, "loop_rollback", LoopStackOverflowScenario::loopRollback);
        register(meter, "loop_energy_queries", LoopStackOverflowScenario::loopEnergyQueries);
        register(meter, "loop_two_meters", LoopStackOverflowScenario::loopTwoMeters);
        register(meter, "shared_input_network", LoopStackOverflowScenario::sharedInputNetwork);

        register(meter, "split_one_to_one", MeterSplitTests::split_one_to_one);
        register(meter, "split_three_to_one", MeterSplitTests::split_three_to_one);
        register(meter, "split_one_to_three", MeterSplitTests::split_one_to_three);
        register(meter, "split_two_to_two", MeterSplitTests::split_two_to_two);
        register(meter, "split_uneven_energy", MeterSplitTests::split_uneven_energy);

        register(meter, "limit_transfer_one_to_one", MeterTransferLimitTests::limit_transfer_one_to_one);
        register(meter, "limit_split_one_to_three", MeterTransferLimitTests::limit_split_one_to_three);
        register(meter, "limit_transfer_two_to_one", MeterTransferLimitTests::limit_transfer_two_to_one);
        register(meter, "limit_split_two_to_two", MeterTransferLimitTests::limit_split_two_to_two);

        register(meter, "transfer_one_to_one", MeterTransferTests::transfer_one_to_one);
        register(meter, "transfer_three_to_one", MeterTransferTests::transfer_three_to_one);
        register(meter, "transfer_one_to_three", MeterTransferTests::transfer_one_to_three);
        register(meter, "transfer_two_to_two", MeterTransferTests::transfer_two_to_two);

        register(meter, "transaction_simulation_rollback", MeterTransactionTests::valid_input_simulation_rolls_back);
        register(meter, "transaction_root_rollback", MeterTransactionTests::root_rollback);
        register(meter, "transaction_child_commit_root_rollback", MeterTransactionTests::child_commit_root_rollback);
        register(meter, "transaction_child_rollback_root_commit", MeterTransactionTests::child_rollback_root_commit);
        register(meter, "transaction_repeated_root_inserts", MeterTransactionTests::repeated_inserts_in_root_commit);
        register(meter, "transaction_multi_output_rollback", MeterTransactionTests::partial_multi_output_rollback);
        register(meter, "transaction_consume_rollback", MeterTransactionTests::consume_mode_rollback);
        register(meter, "transaction_transfer_limit_rollback", MeterTransactionTests::transfer_limit_rollback);

        register(meter, "zero_tolerance", MeterZeroToleranceTests::test);

        var scenarios = TestUtils.BATCH_SCENARIOS;
        register(scenarios, "energy_metadata_overflow", EnergyMetadataOverflowScenario::test);
        register(scenarios, "excess_energy_voiding", ExcessEnergyVoidingScenario::test);
        register(scenarios, "split_no_double_insert", SplitNoDoubleInsertScenario::test);
    }

    private TestFunctions() {}

    @Nullable
    public static Consumer<GameTestHelper> get(Identifier id) {
        return FUNCTIONS.get(id);
    }

    public static void registerTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new LinkedHashMap<>();

        for (TestEntry entry : ENTRIES) {
            Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(
                entry.batch(),
                batch -> event.registerEnvironment(Identifier.fromNamespaceAndPath(TestMod.MOD_ID, batch))
            );

            event.registerTest(entry.id(), new TestModTestInstance(entry.id(), testData(environment)));
        }
    }

    private static void register(String batch, String name, Consumer<GameTestHelper> function) {
        Identifier id = Identifier.fromNamespaceAndPath(TestMod.MOD_ID, name);
        if (FUNCTIONS.put(id, function) != null) {
            throw new IllegalStateException("duplicate test function: " + id);
        }
        ENTRIES.add(new TestEntry(batch, id));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(Holder<TestEnvironmentDefinition<?>> environment) {
        return new TestData<>(
            environment,
            Identifier.fromNamespaceAndPath(TestMod.MOD_ID, TestUtils.EMPTY_STRUCTURE),
            MAX_TICKS,
            0,
            true
        );
    }

    private record TestEntry(String batch, Identifier id) {}
}
