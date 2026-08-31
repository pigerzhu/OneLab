package io.github.pigerzhu.onelab.hook.applications;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.pigerzhu.onelab.hook.core.DexKitUtils;

/** Locates Qishui's player-layout gate through a stable pre-inflation call site. */
final class QishuiLargeScreenLocator {
    private static final String PAD_OPTIMIZE_CONFIG =
            "com.luna.common.arch.ab.PadOptimizeConfig";
    private static final String PLAYER_PREINFLATER =
            "com.luna.biz.playing.init.preinflate.PlayingPreInflaterInitTask";

    static {
        System.loadLibrary("dexkit");
    }

    private QishuiLargeScreenLocator() {
    }

    static Method findPlayerLayoutGate(String apkPath, ClassLoader classLoader) throws Exception {
        try (DexKitBridge bridge = DexKitUtils.open(apkPath)) {
            MethodDataList callers = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().declaredClass(PLAYER_PREINFLATER)));
            Map<String, MethodData> candidates = new LinkedHashMap<>();
            for (MethodData caller : callers) {
                for (MethodData invoked : caller.getInvokes()) {
                    if (PAD_OPTIMIZE_CONFIG.equals(invoked.getClassName())
                            && "boolean".equals(invoked.getReturnTypeName())
                            && invoked.getParamTypeNames().isEmpty()) {
                        candidates.put(invoked.getDescriptor(), invoked);
                    }
                }
            }
            if (candidates.size() != 1) {
                throw new IllegalStateException(
                        "Expected one Qishui player-layout gate, found " + candidates.size());
            }
            return candidates.values().iterator().next().getMethodInstance(classLoader);
        }
    }
}
