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
            var portraitCandidates = bridge.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create()
                            .returnType("boolean")
                            .paramTypes("android.app.Activity", "android.content.res.Configuration")
                            .usingStrings(
                                    TikTokLargeScreenPolicy.PORTRAIT_COMMENT_GATE,
                                    "isOptCommentSplit is in split/popout/not landscape no")));
            MethodData portraitData = TikTokLargeScreenTargets.requireUnique(
                    TikTokLargeScreenPolicy.PORTRAIT_COMMENT_GATE,
                    new ArrayList<>(portraitCandidates));
            methods.put(TikTokLargeScreenPolicy.PORTRAIT_COMMENT_GATE,
                    portraitData.getMethodInstance(classLoader));
            var widthCandidates = bridge.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create()
                            .returnType("int")
                            .paramTypes()
                            .usingStrings(TikTokLargeScreenPolicy.FOLDABLE_OVERRIDE)
                            .usingNumbers(387, 350, 400, 450)));
            MethodData widthData = TikTokLargeScreenTargets.requireUnique(
                    TikTokLargeScreenPolicy.COMMENT_PANEL_WIDTH,
                    new ArrayList<>(widthCandidates));
            methods.put(TikTokLargeScreenPolicy.COMMENT_PANEL_WIDTH,
                    widthData.getMethodInstance(classLoader));
        }
        return new TikTokLargeScreenTargets(methods);
    }
}
