package io.github.pigerzhu.onelab.hook.applications;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

final class KuaishouLargeScreenTargets {
    private KuaishouLargeScreenTargets() {
    }

    static Method requireUniqueGate(List<Method> candidates) {
        Method result = null;
        for (Method candidate : candidates) {
            if (!Modifier.isStatic(candidate.getModifiers())
                    || candidate.getParameterCount() != 0
                    || candidate.getReturnType() != boolean.class) {
                continue;
            }
            if (result != null && !result.equals(candidate)) {
                throw new IllegalStateException("Ambiguous Kuaishou tablet gates");
            }
            result = candidate;
        }
        if (result == null) {
            throw new IllegalStateException("Kuaishou tablet gate not found");
        }
        return result;
    }
}
