package io.github.pigerzhu.onelab.hook.system;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class GosVrrTargetResolver {
    private static final String[] CORE_CLASS_CANDIDATES = {
            "com.samsung.android.game.gos.feature.vrr.a",
            "com.samsung.android.game.gos.feature.vrr.b"
    };

    private GosVrrTargetResolver() {
    }

    static Method resolve(ClassLoader classLoader) {
        List<Class<?>> candidates = new ArrayList<>();
        for (String className : CORE_CLASS_CANDIDATES) {
            try {
                candidates.add(Class.forName(className, false, classLoader));
            } catch (ClassNotFoundException | LinkageError ignored) {
                // GOS obfuscation differs between firmware generations.
            }
        }
        return findUpdateMethod(candidates.toArray(new Class<?>[0]));
    }

    static Method findUpdateMethod(Class<?>... candidates) {
        for (Class<?> candidate : candidates) {
            Method match = null;
            for (Method method : candidate.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != Void.TYPE
                        || parameters.length != 2
                        || parameters[0] != Integer.TYPE
                        || parameters[1] != String.class) {
                    continue;
                }
                if (match != null) {
                    match = null;
                    break;
                }
                match = method;
            }
            if (match != null) {
                return match;
            }
        }
        return null;
    }
}
