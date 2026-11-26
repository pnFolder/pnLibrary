package org.pnfolder.pnlibrary.api.scenario.expression;

import java.util.ArrayList;
import java.util.List;

import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.format;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.normalize;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.number;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.required;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.truthy;

/** Recursive-descent parser of {@link Expression}; every rule is one precedence level, loosest first. */
final class Parser {
    private static final String[] COMPARISONS = {"<=", ">=", "<", ">"};
    private static final String[] PRODUCTS = {"*", "/", "%"};

    private final String text;
    private int position;

    Parser(String text) {
        this.text = text;
    }

    Expression.Node parseAll() {
        if (ExpressionValues.isBlank(text)) {
            throw new ExpressionException("The expression is empty");
        }
        Expression.Node node = ternary();
        skipSpaces();
        if (position < text.length()) {
            throw fail("Unexpected '" + text.charAt(position) + "'");
        }
        return node;
    }

    private ExpressionException fail(String message) {
        return new ExpressionException(message + " at position " + (position + 1) + " of '" + text + "'");
    }

    private void skipSpaces() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private boolean accept(String token) {
        skipSpaces();
        if (!text.startsWith(token, position)) {
            return false;
        }
        position += token.length();
        return true;
    }

    /** Accepts {@code token} only when no longer operator starts here, so {@code <} does not eat {@code <=}. */
    private boolean acceptExact(String token, String... longer) {
        skipSpaces();
        for (String other : longer) {
            if (text.startsWith(other, position)) {
                return false;
            }
        }
        return accept(token);
    }

    private Expression.Node ternary() {
        Expression.Node condition = or();
        if (!acceptExact("?")) {
            return condition;
        }
        Expression.Node yes = ternary();
        if (!accept(":")) {
            throw fail("Expected ':' in a condition");
        }
        Expression.Node no = ternary();
        return c -> truthy(condition.eval(c)) ? yes.eval(c) : no.eval(c);
    }

    private Expression.Node or() {
        Expression.Node left = and();
        while (accept("||")) {
            Expression.Node a = left;
            Expression.Node b = and();
            left = c -> truthy(a.eval(c)) || truthy(b.eval(c));
        }
        return left;
    }

    private Expression.Node and() {
        Expression.Node left = equality();
        while (accept("&&")) {
            Expression.Node a = left;
            Expression.Node b = equality();
            left = c -> truthy(a.eval(c)) && truthy(b.eval(c));
        }
        return left;
    }

    private Expression.Node equality() {
        Expression.Node left = comparison();
        while (true) {
            boolean negate;
            if (accept("==")) {
                negate = false;
            } else if (accept("!=")) {
                negate = true;
            } else {
                return left;
            }
            Expression.Node a = left;
            Expression.Node b = comparison();
            left = c -> same(a.eval(c), b.eval(c)) != negate;
        }
    }

    private Expression.Node comparison() {
        Expression.Node left = additive();
        while (true) {
            String op = acceptComparison();
            if (op == null) {
                return left;
            }
            Expression.Node a = left;
            Expression.Node b = additive();
            left = c -> {
                int order = compare(a.eval(c), b.eval(c));
                switch (op) {
                    case "<":
                        return order < 0;
                    case "<=":
                        return order <= 0;
                    case ">":
                        return order > 0;
                    default:
                        return order >= 0;
                }
            };
        }
    }

    private String acceptComparison() {
        for (String op : COMPARISONS) {
            if (op.length() == 1 ? acceptExact(op, "<=", ">=") : accept(op)) {
                return op;
            }
        }
        return null;
    }

    private Expression.Node additive() {
        Expression.Node left = multiplicative();
        while (true) {
            boolean plus;
            if (accept("+")) {
                plus = true;
            } else if (acceptExact("-")) {
                plus = false;
            } else {
                return left;
            }
            Expression.Node a = left;
            Expression.Node b = multiplicative();
            left = c -> add(a.eval(c), b.eval(c), plus);
        }
    }

    private static Object add(Object x, Object y, boolean plus) {
        if (!plus) {
            return required(x) - required(y);
        }
        if (number(x) == null || number(y) == null) {
            return format(x) + format(y);
        }
        return required(x) + required(y);
    }

    private Expression.Node multiplicative() {
        Expression.Node left = power();
        while (true) {
            String op = acceptProduct();
            if (op == null) {
                return left;
            }
            Expression.Node a = left;
            Expression.Node b = power();
            left = c -> multiply(required(a.eval(c)), required(b.eval(c)), op);
        }
    }

    private String acceptProduct() {
        for (String op : PRODUCTS) {
            if (accept(op)) {
                return op;
            }
        }
        return null;
    }

    private static Object multiply(double x, double y, String op) {
        if (op.equals("*")) {
            return x * y;
        }
        if (y == 0.0) {
            throw new ExpressionException("Division by zero");
        }
        return op.equals("/") ? x / y : x % y;
    }

    private Expression.Node power() {
        Expression.Node base = unary();
        if (!accept("^")) {
            return base;
        }
        Expression.Node exponent = power();
        return c -> Math.pow(required(base.eval(c)), required(exponent.eval(c)));
    }

    private Expression.Node unary() {
        if (acceptExact("!", "!=")) {
            Expression.Node inner = unary();
            return c -> !truthy(inner.eval(c));
        }
        if (accept("-")) {
            Expression.Node inner = unary();
            return c -> -required(inner.eval(c));
        }
        return primary();
    }

    private Expression.Node primary() {
        skipSpaces();
        if (position >= text.length()) {
            throw fail("The expression ends too early");
        }
        char ch = text.charAt(position);
        if (ch == '(') {
            position++;
            Expression.Node inner = ternary();
            if (!accept(")")) {
                throw fail("Expected ')'");
            }
            return inner;
        }
        if (Character.isDigit(ch) || (ch == '.' && position + 1 < text.length() && Character.isDigit(text.charAt(position + 1)))) {
            return numberLiteral();
        }
        if (ch == '\'' || ch == '"') {
            return stringLiteral(ch);
        }
        if (ch == '{') {
            return bracketed('}');
        }
        if (ch == '%') {
            return bracketed('%');
        }
        if (Character.isLetter(ch) || ch == '_' || ch == '~') {
            return nameOrCall();
        }
        throw fail("Unexpected '" + ch + "'");
    }

    private Expression.Node numberLiteral() {
        int start = position;
        while (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
            position++;
        }
        Double value = ExpressionValues.parseDouble(text.substring(start, position));
        if (value == null) {
            position = start;
            throw fail("Bad number");
        }
        return c -> value;
    }

    private Expression.Node stringLiteral(char quote) {
        position++;
        int start = position;
        while (position < text.length() && text.charAt(position) != quote) {
            position++;
        }
        if (position >= text.length()) {
            throw fail("A text is not closed");
        }
        String value = text.substring(start, position);
        position++;
        return c -> value;
    }

    /** {@code {name}}, {@code {plugin:key}} or {@code %external%}: the whole token is read by the context, as in texts. */
    private Expression.Node bracketed(char close) {
        int start = position;
        position++;
        int end = text.indexOf(close, position);
        if (end < 0) {
            position = start;
            throw fail("Expected '" + close + "'");
        }
        position = end + 1;
        String token = text.substring(start, position);
        return c -> normalize(c.read(token));
    }

    private Expression.Node nameOrCall() {
        String name = readName();
        skipSpaces();
        if (position < text.length() && text.charAt(position) == '(') {
            position++;
            return call(name, readArguments(name));
        }
        switch (name) {
            case "true":
                return c -> true;
            case "false":
                return c -> false;
            case "null":
                return c -> null;
            default:
                return c -> normalize(c.read(name));
        }
    }

    private String readName() {
        int start = position;
        String owner = "";
        int probe = position;
        while (probe < text.length() && (Character.isLetterOrDigit(text.charAt(probe)) || "_-/~".indexOf(text.charAt(probe)) >= 0)) {
            probe++;
        }
        if (probe > position && probe < text.length() && text.charAt(probe) == '@') {
            owner = text.substring(position, probe) + "@";
            position = probe + 1;
            start = position;
        } else if (text.charAt(position) == '~') {
            throw fail("Unexpected '~'");
        }
        while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position))
                || text.charAt(position) == '_' || text.charAt(position) == '.')) {
            position++;
        }
        return owner + text.substring(start, position);
    }

    private List<Expression.Node> readArguments(String name) {
        List<Expression.Node> args = new ArrayList<>();
        if (accept(")")) {
            return args;
        }
        do {
            args.add(ternary());
        } while (accept(","));
        if (!accept(")")) {
            throw fail("Expected ')' after the arguments of " + name + "()");
        }
        return args;
    }

    private static Expression.Node call(String name, List<Expression.Node> args) {
        return c -> {
            ExpressionFunction function = c.function(name);
            if (function == null) {
                function = ExpressionFunctions.find(name);
            }
            if (function == null) {
                throw new ExpressionException("Unknown function " + name + "()");
            }
            List<Object> values = new ArrayList<>(args.size());
            for (Expression.Node arg : args) {
                values.add(normalize(arg.eval(c)));
            }
            return normalize(function.call(c, values));
        };
    }

    private static boolean same(Object a, Object b) {
        Double x = number(a);
        Double y = number(b);
        if (x != null && y != null && !(a instanceof Boolean) && !(b instanceof Boolean)) {
            return x.doubleValue() == y.doubleValue();
        }
        return format(a).equals(format(b));
    }

    private static int compare(Object a, Object b) {
        Double x = number(a);
        Double y = number(b);
        if (x != null && y != null) {
            return x.compareTo(y);
        }
        return format(a).compareToIgnoreCase(format(b));
    }
}
