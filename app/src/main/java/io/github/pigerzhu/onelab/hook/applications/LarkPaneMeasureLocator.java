package io.github.pigerzhu.onelab.hook.applications;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class LarkPaneMeasureLocator {
    private LarkPaneMeasureLocator() {
    }

    static Targets findUnique(Class<?> groupClass, Class<?> widthClass) {
        Method left = null;
        Method right = null;
        for (Method method : groupClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != int.class) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 3
                    && parameters[0] == int.class
                    && parameters[2] == boolean.class) {
                if (left != null) return null;
                left = method;
            } else if (parameters.length == 2
                    && parameters[0] == int.class
                    && parameters[1] == widthClass) {
                if (right != null) return null;
                right = method;
            }
        }
        return left == null || right == null ? null : new Targets(left, right);
    }

    static final class Targets {
        final Method left;
        final Method right;

        Targets(Method left, Method right) {
            this.left = left;
            this.right = right;
        }
    }
}
