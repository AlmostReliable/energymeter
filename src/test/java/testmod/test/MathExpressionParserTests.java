package testmod.test;

import com.almostreliable.energymeter.util.MathExpressionParser;
import com.almostreliable.energymeter.util.NumberFormatter;

import net.minecraft.gametest.framework.GameTestHelper;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Optional;

public class MathExpressionParserTests {

    public static void testBasicNumbers(GameTestHelper helper) {
        assertParseEquals(helper, "42", new BigDecimal("42"));
        assertParseEquals(helper, "3.14", new BigDecimal("3.14"));
        assertParseEquals(helper, "0", BigDecimal.ZERO);
        assertParseEquals(helper, "0.5", new BigDecimal("0.5"));
        assertParseEquals(helper, ".5", new BigDecimal("0.5"));
        assertParseEquals(helper, "123.456", new BigDecimal("123.456"));

        helper.succeed();
    }

    public static void testBasicArithmetic(GameTestHelper helper) {
        // addition
        assertParseEquals(helper, "2 + 3", new BigDecimal("5"));
        assertParseEquals(helper, "10 + 20 + 30", new BigDecimal("60"));
        assertParseEquals(helper, "1.5 + 2.5", new BigDecimal("4.0"));

        // subtraction
        assertParseEquals(helper, "5 - 3", new BigDecimal("2"));
        assertParseEquals(helper, "10 - 5 - 2", new BigDecimal("3"));
        assertParseEquals(helper, "3.5 - 1.2", new BigDecimal("2.3"));

        // multiplication
        assertParseEquals(helper, "4 * 5", new BigDecimal("20"));
        assertParseEquals(helper, "2 * 3 * 4", new BigDecimal("24"));
        assertParseEquals(helper, "1.5 * 2", new BigDecimal("3.0"));

        // division
        assertParseEquals(helper, "10 / 2", new BigDecimal("5"));
        assertParseEquals(helper, "15 / 3", new BigDecimal("5"));
        assertParseEquals(helper, "7 / 2", new BigDecimal("3.5"));

        helper.succeed();
    }

    public static void testOperatorPrecedence(GameTestHelper helper) {
        // multiplication before addition
        assertParseEquals(helper, "2 + 3 * 4", new BigDecimal("14"));
        assertParseEquals(helper, "3 * 4 + 5", new BigDecimal("17"));

        // division before subtraction
        assertParseEquals(helper, "10 - 8 / 2", new BigDecimal("6"));
        assertParseEquals(helper, "20 / 4 - 2", new BigDecimal("3"));

        // mixed operations
        assertParseEquals(helper, "2 + 3 * 4 - 1", new BigDecimal("13"));
        assertParseEquals(helper, "10 / 2 + 3 * 2", new BigDecimal("11"));

        helper.succeed();
    }

    public static void testPowerOperations(GameTestHelper helper) {
        // basic power operations
        assertParseEquals(helper, "2 ^ 3", new BigDecimal("8"));
        assertParseEquals(helper, "5 ^ 2", new BigDecimal("25"));
        assertParseEquals(helper, "10 ^ 0", BigDecimal.ONE);
        assertParseEquals(helper, "3 ^ 1", new BigDecimal("3"));

        // right associativity of power
        assertParseEquals(helper, "2 ^ 3 ^ 2", new BigDecimal("512")); // 2^(3^2) = 2^9 = 512

        // power has higher precedence than multiplication
        assertParseEquals(helper, "2 * 3 ^ 2", new BigDecimal("18")); // 2 * (3^2) = 2 * 9 = 18
        assertParseEquals(helper, "2 ^ 3 * 4", new BigDecimal("32")); // (2^3) * 4 = 8 * 4 = 32

        helper.succeed();
    }

    public static void testNegativeNumbers(GameTestHelper helper) {
        // basic negative numbers
        assertParseEquals(helper, "-5", new BigDecimal("-5"));
        assertParseEquals(helper, "-3.14", new BigDecimal("-3.14"));

        // operations with negative numbers
        assertParseEquals(helper, "-5 + 3", new BigDecimal("-2"));
        assertParseEquals(helper, "5 + -3", new BigDecimal("2"));
        assertParseEquals(helper, "-5 * -3", new BigDecimal("15"));
        assertParseEquals(helper, "-10 / -2", new BigDecimal("5"));

        // double negatives
        assertParseEquals(helper, "--5", new BigDecimal("5"));
        assertParseEquals(helper, "---5", new BigDecimal("-5"));

        // negative powers
        assertParseEquals(helper, "-2 ^ 2", new BigDecimal("4"));
        assertParseEquals(helper, "-(2 ^ 2)", new BigDecimal("-4"));

        helper.succeed();
    }

    public static void testParentheses(GameTestHelper helper) {
        // basic parentheses
        assertParseEquals(helper, "(5)", new BigDecimal("5"));
        assertParseEquals(helper, "(2 + 3)", new BigDecimal("5"));

        // parentheses changing precedence
        assertParseEquals(helper, "(2 + 3) * 4", new BigDecimal("20"));
        assertParseEquals(helper, "2 * (3 + 4)", new BigDecimal("14"));
        assertParseEquals(helper, "(10 - 5) / (2 + 3)", new BigDecimal("1"));

        // nested parentheses
        assertParseEquals(helper, "((2 + 3) * 4)", new BigDecimal("20"));
        assertParseEquals(helper, "(2 * (3 + 4))", new BigDecimal("14"));
        assertParseEquals(helper, "((5 + 3) * (2 - 1))", new BigDecimal("8"));

        // parentheses with negative numbers
        assertParseEquals(helper, "(-5)", new BigDecimal("-5"));
        assertParseEquals(helper, "-(5)", new BigDecimal("-5"));
        assertParseEquals(helper, "(-2) ^ 2", new BigDecimal("4")); // (-2)^2 = 4

        helper.succeed();
    }

    public static void testComplexExpressions(GameTestHelper helper) {
        // complex mixed operations
        assertParseEquals(helper, "2 + 3 * 4 ^ 2 - 1", new BigDecimal("49")); // 2 + 3 * 16 - 1 = 49
        assertParseEquals(helper, "(2 + 3) * (4 - 1) ^ 2", new BigDecimal("45")); // 5 * 3^2 = 5 * 9 = 45
        assertParseEquals(helper, "10 / (2 + 3) - 1", new BigDecimal("1")); // 10/5 - 1 = 2 - 1 = 1

        // long expressions
        assertParseEquals(helper, "1 + 2 + 3 + 4 + 5", new BigDecimal("15"));
        assertParseEquals(helper, "2 * 3 * 4 / 2 / 3", new BigDecimal("4"));

        helper.succeed();
    }

    public static void testWhitespace(GameTestHelper helper) {
        // various whitespace scenarios
        assertParseEquals(helper, " 5 ", new BigDecimal("5"));
        assertParseEquals(helper, "2  +  3", new BigDecimal("5"));
        assertParseEquals(helper, " 2 * 3 ", new BigDecimal("6"));
        assertParseEquals(helper, "(  5  +  3  )  *  2", new BigDecimal("16"));
        assertParseEquals(helper, "2^  3", new BigDecimal("8"));

        helper.succeed();
    }

    public static void testInvalidExpressions(GameTestHelper helper) {
        // null and empty inputs
        assertParseEmpty(helper, null);
        assertParseEmpty(helper, "");
        assertParseEmpty(helper, "   ");

        // invalid syntax
        assertParseEmpty(helper, "+");
        assertParseEmpty(helper, "*");
        assertParseEmpty(helper, "/");
        assertParseEmpty(helper, "^");
        assertParseEmpty(helper, "()");
        assertParseEmpty(helper, "2 +");
        assertParseEmpty(helper, "* 5");
        assertParseEmpty(helper, "5 5");
        assertParseEmpty(helper, "2 + + 3");

        // mismatched parentheses
        assertParseEmpty(helper, "(5");
        assertParseEmpty(helper, "5)");
        assertParseEmpty(helper, "((5)");
        assertParseEmpty(helper, "(5))");

        // invalid characters
        assertParseEmpty(helper, "5a");
        assertParseEmpty(helper, "5 + a");
        assertParseEmpty(helper, "5 & 3");

        // multiple decimal points
        assertParseEmpty(helper, "5.5.5");

        // division by zero
        assertParseEmpty(helper, "5 / 0");
        assertParseEmpty(helper, "10 / (5 - 5)");

        helper.succeed();
    }

    public static void testNegativePowers(GameTestHelper helper) {
        // test negative exponents (should work with proper division)
        assertParseEquals(helper, "2 ^ -1", new BigDecimal("0.5"));
        assertParseEquals(helper, "4 ^ -2", new BigDecimal("0.0625")); // 1/16
        assertParseEquals(helper, "10 ^ -1", new BigDecimal("0.1"));

        helper.succeed();
    }

    public static void testLargeNumbers(GameTestHelper helper) {
        // test with larger numbers
        assertParseEquals(helper, "1000000 + 2000000", new BigDecimal("3000000"));
        assertParseEquals(helper, "999999 * 2", new BigDecimal("1999998"));

        helper.succeed();
    }

    public static void testPrecisionAndRounding(GameTestHelper helper) {
        // test division that requires rounding
        Optional<BigDecimal> result = MathExpressionParser.parse("1 / 3");
        helper.assertTrue(result.isPresent(), "1/3 should parse successfully");
        // the result should be approximately 0.3333... with 16 digit precision
        // noinspection OptionalGetWithoutIsPresent
        helper.assertTrue(result.get().toString().startsWith("0.333"), "1/3 should start with 0.333");

        helper.succeed();
    }

    public static void testSuffixes(GameTestHelper helper) {
        // test simple suffix usages
        assertParseEquals(helper, "1k", new BigDecimal("1000"));
        assertParseEquals(helper, "1.5k", new BigDecimal("1500"));
        assertParseEquals(helper, "2M", new BigDecimal("2000000"));
        assertParseEquals(helper, "1G", new BigDecimal("1000000000"));
        assertParseEquals(helper, "1T", new BigDecimal("1000000000000"));
        assertParseEquals(helper, "1P", new BigDecimal("1000000000000000"));
        assertParseEquals(helper, "1E", new BigDecimal("1000000000000000000"));
        assertParseEquals(helper, ".5k", new BigDecimal("500"));

        // test suffix case
        assertParseEquals(helper, "1K", new BigDecimal("1000"));
        assertParseEquals(helper, "1m", new BigDecimal("1000000"));
        assertParseEquals(helper, "1g", new BigDecimal("1000000000"));

        // test suffix usage in expressions
        assertParseEquals(helper, "2k * 3", new BigDecimal("6000"));
        assertParseEquals(helper, "(1k + 500) * 2", new BigDecimal("3000"));
        assertParseEquals(helper, "1M / 4", new BigDecimal("250000"));
        assertParseEquals(helper, "-1k", new BigDecimal("-1000"));
        assertParseEquals(helper, "1k ^ 2", new BigDecimal("1000000"));

        // test invalid suffixes
        assertParseEmpty(helper, "k");
        assertParseEmpty(helper, "5kk");
        assertParseEmpty(helper, "5 k");
        assertParseEmpty(helper, "(2)k");
        assertParseEmpty(helper, "1k5");
        assertParseEmpty(helper, "5x");

        helper.succeed();
    }

    public static void testCompactFormatRoundTrip(GameTestHelper helper) {
        assertCompactFormat(helper, 0, "0");
        assertCompactFormat(helper, 999, "999");
        assertCompactFormat(helper, 1_000, "1k");
        assertCompactFormat(helper, 1_500, "1.5k");
        assertCompactFormat(helper, 100_000, "100k");
        assertCompactFormat(helper, 1_250_000, "1.25M");
        assertCompactFormat(helper, 123_456, "123456");
        assertCompactFormat(helper, 2_000_000_000_000L, "2T");
        assertCompactFormat(helper, Long.MAX_VALUE, String.valueOf(Long.MAX_VALUE));

        helper.succeed();
    }

    // helper methods
    private static void assertParseEquals(GameTestHelper helper, String expression, BigDecimal expected) {
        Optional<BigDecimal> result = MathExpressionParser.parse(expression);
        helper.assertTrue(result.isPresent(), "Expression '" + expression + "' should parse successfully");
        // noinspection OptionalGetWithoutIsPresent
        helper.assertTrue(
            result.get().compareTo(expected) == 0,
            "Expression '" + expression + "' should equal " + expected + " but got " + result.get()
        );
    }

    private static void assertParseEmpty(GameTestHelper helper, @Nullable String expression) {
        Optional<BigDecimal> result = MathExpressionParser.parse(expression);
        helper.assertTrue(
            result.isEmpty(),
            "Expression '" + expression + "' should fail to parse but got " + result.orElse(null)
        );
    }

    private static void assertCompactFormat(GameTestHelper helper, long value, String expected) {
        var formatted = NumberFormatter.formatCompact(value);
        helper.assertTrue(formatted.equals(expected), "Value " + value + " should format as '" + expected + "' but got '" + formatted + "'");
        assertParseEquals(helper, formatted, BigDecimal.valueOf(value));
    }
}
