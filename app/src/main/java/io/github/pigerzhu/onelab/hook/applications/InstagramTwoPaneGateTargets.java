package io.github.pigerzhu.onelab.hook.applications;

import java.util.List;
import java.util.Map;

final class InstagramTwoPaneGateTargets {
    final Map<Long, java.lang.reflect.Method> methods;

    InstagramTwoPaneGateTargets(Map<Long, java.lang.reflect.Method> methods) {
        this.methods = Map.copyOf(methods);
    }

    static <T> T requireUnique(long key, List<T> candidates) {
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "Expected one Instagram gate for key " + key
                            + ", found " + candidates.size());
        }
        return candidates.get(0);
    }
}
