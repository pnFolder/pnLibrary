package org.pnfolder.pnlibrary.api.scenario.expression;

import net.kyori.adventure.key.Key;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.world.ItemMatcher;
import org.pnfolder.pnlibrary.api.world.Position;
import org.pnfolder.pnlibrary.api.world.WorldKeys;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.endsWithIgnoreCase;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.format;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.required;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.startsWithIgnoreCase;
import static org.pnfolder.pnlibrary.api.scenario.expression.ExpressionValues.truthy;

/** The functions of the expression language itself, which plugins cannot redefine. */
final class ExpressionFunctions {
    private static final Map<String, ExpressionFunction> BUILTIN = create();

    private ExpressionFunctions() {
    }

    static ExpressionFunction find(String name) {
        return BUILTIN.get(name);
    }

    static boolean contains(String name) {
        return BUILTIN.containsKey(name);
    }

    private static ExpressionFunction arity(int low, int high, String name, ExpressionFunction function) {
        return (context, arguments) -> {
            if (arguments.size() < low || arguments.size() > high) {
                throw new ExpressionException(name + "() takes " + low + ".." + high + " arguments but got " + arguments.size());
            }
            return function.call(context, arguments);
        };
    }

    private static Map<String, ExpressionFunction> create() {
        Map<String, ExpressionFunction> map = new HashMap<>();
        map.put("min", (c, a) -> fold(a, "min", true));
        map.put("max", (c, a) -> fold(a, "max", false));
        map.put("abs", arity(1, 1, "abs", (c, a) -> Math.abs(required(a.get(0)))));
        map.put("floor", arity(1, 1, "floor", (c, a) -> Math.floor(required(a.get(0)))));
        map.put("ceil", arity(1, 1, "ceil", (c, a) -> Math.ceil(required(a.get(0)))));
        map.put("sqrt", arity(1, 1, "sqrt", (c, a) -> Math.sqrt(required(a.get(0)))));
        map.put("pow", arity(2, 2, "pow", (c, a) -> Math.pow(required(a.get(0)), required(a.get(1)))));
        map.put("round", arity(1, 2, "round", (c, a) -> round(a)));
        map.put("clamp", arity(3, 3, "clamp", (c, a) -> clamp(required(a.get(0)), required(a.get(1)), required(a.get(2)))));
        map.put("random", arity(2, 2, "random", (c, a) -> random(a)));
        map.put("chance", arity(1, 1, "chance", (c, a) -> ThreadLocalRandom.current().nextDouble() < required(a.get(0))));
        map.put("len", arity(1, 1, "len", (c, a) -> (double) format(a.get(0)).length()));
        map.put("upper", arity(1, 1, "upper", (c, a) -> format(a.get(0)).toUpperCase(Locale.ROOT)));
        map.put("lower", arity(1, 1, "lower", (c, a) -> format(a.get(0)).toLowerCase(Locale.ROOT)));
        map.put("contains", arity(2, 2, "contains", (c, a) -> ExpressionValues.containsIgnoreCase(format(a.get(0)), format(a.get(1)))));
        map.put("startsWith", arity(2, 2, "startsWith", (c, a) -> startsWithIgnoreCase(format(a.get(0)), format(a.get(1)))));
        map.put("endsWith", arity(2, 2, "endsWith", (c, a) -> endsWithIgnoreCase(format(a.get(0)), format(a.get(1)))));
        map.put("has", arity(1, 1, "has", (c, a) -> c.value(format(a.get(0))) != null));
        map.put("if", arity(3, 3, "if", (c, a) -> truthy(a.get(0)) ? a.get(1) : a.get(2)));
        ExpressionFunction permission = arity(1, 1, "permission",
                (c, a) -> AudienceExtensions.hasPermission(c.getPlayer(), format(a.get(0))));
        map.put("permission", permission);
        map.put("perm", permission);
        map.put("level", (c, a) -> orZero(c.getPlayer().getExperienceLevel()));
        map.put("exp", (c, a) -> orZero(c.getPlayer().getExperiencePoints()));
        map.put("items", arity(1, 1, "items", (c, a) -> items(c.getPlayer().countItems(matcher(format(a.get(0)))))));
        map.put("distance", arity(4, 4, "distance", (c, a) -> distance(c, a)));
        return map;
    }

    private static Object fold(List<Object> arguments, String name, boolean smallest) {
        if (arguments.isEmpty()) {
            throw new ExpressionException(name + "() needs a value");
        }
        double result = required(arguments.get(0));
        for (int i = 1; i < arguments.size(); i++) {
            double next = required(arguments.get(i));
            result = smallest ? Math.min(result, next) : Math.max(result, next);
        }
        return result;
    }

    private static Object round(List<Object> arguments) {
        double scale = Math.pow(10.0, arguments.size() > 1 ? required(arguments.get(1)) : 0.0);
        return Math.round(required(arguments.get(0)) * scale) / scale;
    }

    private static Object clamp(double value, double low, double high) {
        if (low > high) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + high + " is less than minimum " + low + ".");
        }
        if (value < low) {
            return low;
        }
        return value > high ? high : value;
    }

    private static Object random(List<Object> arguments) {
        long low = (long) required(arguments.get(0));
        long high = (long) required(arguments.get(1));
        if (high < low) {
            throw new ExpressionException("random() needs low <= high");
        }
        return (double) ThreadLocalRandom.current().nextLong(low, high + 1);
    }

    private static Object orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static ItemMatcher matcher(String material) {
        Key key = WorldKeys.minecraftKey(material);
        return new ItemMatcher(key, null, null, null, true);
    }

    private static Object items(Integer count) {
        return (double) (count != null ? count : 0);
    }

    private static Object distance(RequirementContext context, List<Object> a) {
        Position here = context.getPlayer().getPosition();
        Position there = new Position(format(a.get(0)), required(a.get(1)), required(a.get(2)), required(a.get(3)));
        Double blocks = here != null ? here.distanceTo(there) : null;
        return blocks != null ? blocks : Double.POSITIVE_INFINITY;
    }
}
