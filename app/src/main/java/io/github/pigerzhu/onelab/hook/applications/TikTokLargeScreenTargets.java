package io.github.pigerzhu.onelab.hook.applications;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

final class TikTokLargeScreenTargets {
    final Map<String, Method> methods;

    TikTokLargeScreenTargets(Map<String, Method> methods) {
        this.methods = Map.copyOf(methods);
    }

    static <T> T requireUnique(String key, List<T> candidates) {
        if (candidates.size() != 1) {
            throw new IllegalStateException("Expected one TikTok target for " + key
                    + ", found " + candidates.size());
        }
        return candidates.get(0);
    }
}
