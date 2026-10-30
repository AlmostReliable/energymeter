package com.almostreliable.energymeter.util;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Grammar:<p>
 * expression := term (('+' | '-') term)*<p>
 * term       := power (('*' | '/') power)*<p>
 * power      := factor ('^' power)?<p>
 * factor     := NUMBER SUFFIX? | '(' expression ')' | '-' factor<p>
 * SUFFIX     := 'k' | 'M' | 'G' | 'T' | 'P' | 'E' (case-insensitive, powers of 1000)
 */
public final class MathExpressionParser {

    private MathExpressionParser() {}

    public static Optional<BigDecimal> parse(@Nullable String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        try {
            List<Token> tokens = new Tokenizer(input).tokenize();
            Parser parser = new Parser(tokens);
            BigDecimal result = parser.parseExpression();
            if (parser.hasRemaining()) return Optional.empty();
            return Optional.of(result);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean isSuffix(char ch) {
        return Tokenizer.getSuffixExponent(ch) > 0;
    }

    private enum TokenType {
        NUMBER,
        PLUS, MINUS, MUL, DIV, POW,
        LPAREN, RPAREN,
        EOF
    }

    private record Token(TokenType type, @Nullable BigDecimal value) {

        private Token(TokenType type) {
            this(type, null);
        }
    }

    private static final class Tokenizer {

        private final String input;
        private int pos;

        private Tokenizer(String input) {
            this.input = input;
        }

        private List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();
            while (pos < input.length()) {
                char ch = input.charAt(pos);

                if (Character.isWhitespace(ch)) {
                    pos++;
                    continue;
                }

                switch (ch) {
                    case '+' -> tokens.add(new Token(TokenType.PLUS));
                    case '-' -> tokens.add(new Token(TokenType.MINUS));
                    case '*' -> tokens.add(new Token(TokenType.MUL));
                    case '/' -> tokens.add(new Token(TokenType.DIV));
                    case '^' -> tokens.add(new Token(TokenType.POW));
                    case '(' -> tokens.add(new Token(TokenType.LPAREN));
                    case ')' -> tokens.add(new Token(TokenType.RPAREN));
                    default -> {
                        if (Character.isDigit(ch) || ch == '.') {
                            tokens.add(readNumber());
                            continue;
                        }
                        throw new IllegalArgumentException("unexpected character: " + ch);
                    }
                }
                pos++;
            }
            tokens.add(new Token(TokenType.EOF));
            return tokens;
        }

        private Token readNumber() {
            int start = pos;
            boolean hasDot = false;

            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (Character.isDigit(ch)) {
                    pos++;
                } else if (ch == '.' && !hasDot) {
                    hasDot = true;
                    pos++;
                } else {
                    break;
                }
            }

            var value = new BigDecimal(input.substring(start, pos));
            if (pos < input.length()) {
                var exponent = getSuffixExponent(input.charAt(pos));
                if (exponent > 0) {
                    pos++;
                    value = value.movePointRight(3 * exponent);
                }
            }

            return new Token(TokenType.NUMBER, value);
        }

        private static int getSuffixExponent(char ch) {
            return switch (Character.toLowerCase(ch)) {
                case 'k' -> 1;
                case 'm' -> 2;
                case 'g' -> 3;
                case 't' -> 4;
                case 'p' -> 5;
                case 'e' -> 6;
                default -> 0;
            };
        }
    }

    private static final class Parser {

        private static final MathContext DIV_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

        private final List<Token> tokens;
        private int index;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private boolean hasRemaining() {
            return peek().type() != TokenType.EOF;
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token consume() {
            Token token = tokens.get(index);
            index++;
            return token;
        }

        private BigDecimal parseExpression() {
            BigDecimal result = parseTerm();
            while (true) {
                TokenType type = peek().type();
                if (type == TokenType.PLUS) {
                    consume();
                    result = result.add(parseTerm());
                } else if (type == TokenType.MINUS) {
                    consume();
                    result = result.subtract(parseTerm());
                } else {
                    return result;
                }
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal result = parsePower();
            while (true) {
                TokenType type = peek().type();
                if (type == TokenType.MUL) {
                    consume();
                    result = result.multiply(parsePower());
                } else if (type == TokenType.DIV) {
                    consume();
                    BigDecimal divisor = parsePower();
                    if (divisor.signum() == 0) {
                        throw new ArithmeticException("division by zero");
                    }
                    result = result.divide(divisor, DIV_CONTEXT);
                } else {
                    return result;
                }
            }
        }

        private BigDecimal parsePower() {
            BigDecimal base = parseFactor();
            if (peek().type() == TokenType.POW) {
                consume();
                BigDecimal exponent = parsePower(); // right-associative
                return pow(base, exponent);
            }
            return base;
        }

        private BigDecimal parseFactor() {
            Token token = peek();

            switch (token.type()) {
                case MINUS -> {
                    consume();
                    return parseFactor().negate();
                }
                case NUMBER -> {
                    consume();
                    if (token.value() == null) {
                        throw new IllegalArgumentException("invalid number");
                    }
                    return token.value();
                }
                case LPAREN -> {
                    consume();
                    BigDecimal result = parseExpression();
                    if (consume().type() != TokenType.RPAREN) {
                        throw new IllegalArgumentException("mismatched parentheses");
                    }
                    return result;
                }
                default -> {
                    // no-op
                }
            }

            throw new IllegalArgumentException("unexpected token: " + token.type());
        }

        private static BigDecimal pow(BigDecimal base, BigDecimal exponent) {
            int exp;
            try {
                exp = exponent.intValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("exponent must be an integer");
            }

            if (exp < 0) {
                return BigDecimal.ONE.divide(base.pow(-exp), DIV_CONTEXT);
            }
            return base.pow(exp);
        }
    }
}
