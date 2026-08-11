package io.github.pigerzhu.onelab.hook.applications;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.pigerzhu.onelab.hook.core.DexKitUtils;

final class TikTokLargeScreenLocator {
    static {
        System.loadLibrary("dexkit");
    }

    private TikTokLargeScreenLocator() {
    }

    static TikTokLargeScreenTargets find(String apkPath, ClassLoader classLoader)
            throws Exception {
        Map<String, Method> methods = new LinkedHashMap<>();
        try (DexKitBridge bridge = DexKitUtils.open(apkPath)) {
            var candidates = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType("java.lang.Object")
                    .usingStrings(TikTokLargeScreenPolicy.COMMENTS_GATE)));
            MethodData data = TikTokLargeScreenTargets.requireUnique(
                    TikTokLargeScreenPolicy.COMMENTS_GATE, new ArrayList<>(candidates));
            methods.put(TikTokLargeScreenPolicy.COMMENTS_GATE,
                    data.getMethodInstance(classLoader));
        }
        return new TikTokLargeScreenTargets(methods);
    }
}
