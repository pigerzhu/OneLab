package io.github.pigerzhu.onelab.hook.applications;

import java.lang.reflect.Method;
import java.util.List;

final class XhsCommentHookTargets {
    final List<Method> routeGates;
    final List<Method> dialogFactories;
    final Method screenGate;

    XhsCommentHookTargets(List<Method> routeGates, List<Method> dialogFactories,
            Method screenGate) {
        this.routeGates = List.copyOf(routeGates);
        this.dialogFactories = List.copyOf(dialogFactories);
        this.screenGate = screenGate;
    }

    static <T> T requireUnique(String role, List<T> candidates) {
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "Expected one XHS " + role + ", found " + candidates.size());
        }
        return candidates.get(0);
    }

    static <T> List<T> requireRouteGates(List<T> candidates) {
        if (candidates.isEmpty() || candidates.size() > 2) {
            throw new IllegalStateException(
                    "Expected one or two XHS route gates, found " + candidates.size());
        }
        return List.copyOf(candidates);
    }

    static <T> List<T> requireFactories(List<T> candidates) {
        if (candidates.isEmpty() || candidates.size() > 2) {
            throw new IllegalStateException(
                    "Expected one or two XHS dialog factories, found " + candidates.size());
        }
        return List.copyOf(candidates);
    }
}
