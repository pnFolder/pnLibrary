package org.pnfolder.pnlibrary.core.placeholders.scan;

import org.pnfolder.pnlibrary.core.placeholders.address.ArgumentTypes;
import org.pnfolder.pnlibrary.core.placeholders.address.ArgumentSpec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Group;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Route;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;

/** Reads the parameters of a placeholder method: how each one is filled and which path parts they add to the address. */
final class ParameterPlans {
    private final List<ParameterPlan> plans = new ArrayList<>();
    private final List<PhPart> segments = new ArrayList<>();
    private final String where;
    private final Set<String> used;
    private final Function<Class<?>, ArgumentConverter<Object>> lookup;

    private ParameterPlans(String where, Set<String> used, Function<Class<?>, ArgumentConverter<Object>> lookup) {
        this.where = where;
        this.used = used;
        this.lookup = lookup;
    }

    /** Reads the parameters of {@code member}; the names of its slots are added to {@code used}. */
    static ParameterPlans read(Member member, String where, Set<String> used, Function<Class<?>, ArgumentConverter<Object>> lookup) {
        ParameterPlans result = new ParameterPlans(where, used, lookup);
        boolean bare = member.getPlaceholder().bare();
        if (!bare) {
            result.segments.add(PhPart.word(PhPart.checkWord(member.getName())));
        }
        Parameter[] parameters = member.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            result.add(parameters[index], index);
        }
        result.require(!bare || !result.segments.isEmpty(), "a bare placeholder needs at least one @Argument, or it has no address");
        return result;
    }

    List<ParameterPlan> getPlans() {
        return plans;
    }

    /** The fixed word of the member (none when it is bare) followed by one slot for every argument. */
    List<PhPart> getSegments() {
        return segments;
    }

    private void add(Parameter parameter, int index) {
        Class<?> type = parameter.getType();
        if (type == ViewerInfo.class) {
            require(parameter.isAnnotationPresent(Viewer.class), "a ViewerInfo parameter needs @Viewer");
            plans.add(ParameterPlan.viewer());
        } else if (type == RouteInfo.class) {
            require(parameter.isAnnotationPresent(Route.class), "a RouteInfo parameter needs @Route");
            plans.add(ParameterPlan.route());
        } else if (type == ArgumentValue.class) {
            require(parameter.isAnnotationPresent(Argument.class), "an ArgumentValue parameter needs @Argument");
            plans.add(ParameterPlan.one(slot(parameter, index)));
        } else if (parameter.isAnnotationPresent(Group.class)) {
            plans.add(group(type));
        } else {
            throw new IllegalStateException(
                    where + ": parameter " + (index + 1) + " must be a ViewerInfo, a RouteInfo, an ArgumentValue<T> or a class with @Group");
        }
    }

    private String slot(Parameter parameter, int index) {
        Argument options = parameter.getAnnotation(Argument.class);
        ArgumentSpec spec = ArgumentTypes.specFor(ArgumentTypes.argumentTypeOf(parameter.getParameterizedType(), where), options, where, lookup);
        String name = pathName(options == null ? null : options.name(), parameter, index);
        segments.add(PhPart.slot(name, spec.getPathType()));
        return name;
    }

    private ParameterPlan group(Class<?> type) {
        Constructor<?>[] constructors = type.getConstructors();
        if (constructors.length != 1) {
            throw new IllegalStateException(where + ": " + type.getSimpleName() + " needs one public constructor");
        }
        List<String> names = new ArrayList<>();
        Parameter[] parts = constructors[0].getParameters();
        for (int i = 0; i < parts.length; i++) {
            Parameter part = parts[i];
            require(part.getType() == ArgumentValue.class, type.getSimpleName() + " may only have ArgumentValue parameters");
            require(part.isAnnotationPresent(Argument.class), type.getSimpleName() + " parameters need @Argument");
            names.add(slot(part, i));
        }
        return ParameterPlan.many(constructors[0], names);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(where + ": " + message);
        }
    }

    private String pathName(String wanted, Parameter parameter, int index) {
        String raw;
        if (wanted != null && !wanted.isEmpty()) {
            raw = wanted;
        } else if (parameter.isNamePresent()) {
            raw = parameter.getName();
        } else {
            raw = "arg" + (used.size() + index + 1);
        }
        String base = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (base.isEmpty()) {
            base = "arg";
        }
        String name = base;
        int counter = 2;
        while (!used.add(name)) {
            name = base + counter++;
        }
        return name;
    }
}
