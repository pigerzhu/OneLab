package io.github.pigerzhu.onelab.hook.applications;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.pigerzhu.onelab.hook.core.DexKitUtils;

/** Locates Kuaishou's tablet gate from a stable detail-page routing contract. */
final class KuaishouLargeScreenLocator {
    private static final String DETAIL_ACTIVITY =
            "com.yxcorp.gifshow.detail.PhotoDetailActivity";
    private static final String DETAIL_PARAM =
            "com.kwai.feature.api.feed.detail.router.PhotoDetailParam";

    static {
        System.loadLibrary("dexkit");
    }

    private KuaishouLargeScreenLocator() {
    }

    static Method find(String apkPath, ClassLoader classLoader) throws Exception {
        try (DexKitBridge bridge = DexKitUtils.open(apkPath)) {
            MethodData entry = requireUniqueEntry(bridge.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create()
                            .declaredClass(DETAIL_ACTIVITY)
                            .returnType("java.lang.Class")
                            .paramTypes(DETAIL_PARAM))));

            Map<String, MethodData> candidates = new LinkedHashMap<>();
            for (MethodData invoked : entry.getInvokes()) {
                if ("boolean".equals(invoked.getReturnTypeName())
                        && invoked.getParamTypeNames().isEmpty()) {
                    candidates.put(invoked.getDescriptor(), invoked);
                }
            }
            List<Method> methods = new ArrayList<>(candidates.size());
            for (MethodData candidate : candidates.values()) {
                methods.add(candidate.getMethodInstance(classLoader));
            }
            return KuaishouLargeScreenTargets.requireUniqueGate(methods);
        }
    }

    private static MethodData requireUniqueEntry(MethodDataList candidates) {
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "Expected one Kuaishou detail route, found " + candidates.size());
        }
        return candidates.get(0);
    }
}
