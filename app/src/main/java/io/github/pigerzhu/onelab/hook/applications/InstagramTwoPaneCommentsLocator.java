package io.github.pigerzhu.onelab.hook.applications;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import io.github.pigerzhu.onelab.hook.core.DexKitUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Locates Instagram Reels gate methods by stable business keys. */
final class InstagramTwoPaneCommentsLocator {
    private static final String USER_SESSION = "com.instagram.common.session.UserSession";

    static {
        System.loadLibrary("dexkit");
    }

    private InstagramTwoPaneCommentsLocator() {
    }

    static InstagramTwoPaneGateTargets find(String apkPath, ClassLoader classLoader)
            throws Exception {
        Map<Long, Method> methods = new LinkedHashMap<>();
        try (DexKitBridge bridge = DexKitUtils.open(apkPath)) {
            for (long key : InstagramMobileConfigPolicy.gateKeys()) {
                MethodDataList candidates = bridge.findMethod(
                        FindMethod.create().matcher(MethodMatcher.create()
                                .returnType("boolean")
                                .paramTypes(USER_SESSION)
                                .usingNumbers(key)));
                MethodData method = InstagramTwoPaneGateTargets.requireUnique(
                        key, new ArrayList<>(candidates));
                methods.put(key, method.getMethodInstance(classLoader));
            }
        }
        return new InstagramTwoPaneGateTargets(methods);
    }
}
