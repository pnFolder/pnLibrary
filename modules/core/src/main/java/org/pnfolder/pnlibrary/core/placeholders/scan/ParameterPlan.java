package org.pnfolder.pnlibrary.core.placeholders.scan;

import org.pnfolder.pnlibrary.core.placeholders.Failures;
import org.pnfolder.pnlibrary.core.placeholders.Call;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** How one parameter of a placeholder method is filled from the call. */
abstract class ParameterPlan {
    abstract Object value(Call call);

    /** The viewer who asks. */
    static ParameterPlan viewer() {
        return new ParameterPlan() {
            @Override
            Object value(Call call) {
                return call.getViewer();
            }
        };
    }

    /** The route of the address. */
    static ParameterPlan route() {
        return new ParameterPlan() {
            @Override
            Object value(Call call) {
                return call.getRoute();
            }
        };
    }

    /** The argument of the path slot called {@code name}. */
    static ParameterPlan one(final String name) {
        return new ParameterPlan() {
            @Override
            Object value(Call call) {
                return call.slot(name).getArgument();
            }
        };
    }

    /** A {@code @Group} class built from the arguments of the slots called {@code names}. */
    static ParameterPlan many(final Constructor<?> constructor, final List<String> names) {
        return new ParameterPlan() {
            @Override
            Object value(Call call) {
                List<Object> arguments = new ArrayList<>();
                for (String name : names) {
                    arguments.add(call.slot(name).getArgument());
                }
                try {
                    return constructor.newInstance(arguments.toArray());
                } catch (InvocationTargetException failure) {
                    throw Failures.unchecked(failure.getTargetException());
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        };
    }
}
