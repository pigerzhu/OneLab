package io.github.pigerzhu.onelab.hook.applications;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodDataList;

import io.github.pigerzhu.onelab.hook.core.DexKitUtils;

import java.lang.reflect.Method;

/** Locates QQ's native split gate by behavior rather than obfuscated symbols. */
final class QqFoldGateLocator {
    static {
        System.loadLibrary("dexkit");
    }

    private QqFoldGateLocator() {
    }

    static Method find(String apkPath, ClassLoader classLoader) throws Exception {
        try (DexKitBridge bridge = DexKitUtils.open(apkPath)) {
            MethodDataList matches = bridge.findMethod(
                    FindMethod.create()
                            .searchPackages("com.tencent.mobileqq")
                            .matcher(MethodMatcher.create()
                                    .returnType("boolean")
                                    .paramTypes("android.app.Activity", "int")
                                    .usingStrings(
                                            "isSplitViewMode = false",
                                            "isInMultiWindowMode = true",
                                            "canShowSplitView")));
            if (matches.size() != 1) {
                throw new IllegalStateException(
                        "Expected one QQ split gate, found " + matches.size());
            }
            return matches.get(0).getMethodInstance(classLoader);
        }
    }
}
