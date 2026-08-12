package io.github.pigerzhu.onelab.hook.applications;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import io.github.pigerzhu.onelab.hook.core.DexKitUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Locates XHS comment layout gates by their stable call relationships. */
final class XhsCommentHookLocator {
    private static final String COMMENT_SPI =
            "com.xingin.matrix.comment.spi.CommentSpiProxyImpl";
    private static final String OPEN_COMMENT = "openCommentListDialog";
    private static final String LANDSCAPE_DIALOG =
            "com.xingin.matrix.comment.dialog.VideoCommentLandscapeDialog";

    static {
        System.loadLibrary("dexkit");
    }

    private XhsCommentHookLocator() {
    }

    static XhsCommentHookTargets find(String apkPath, ClassLoader classLoader) throws Exception {
        // Installation runs once per process; close the APK mapping immediately after discovery.
        try (DexKitBridge bridge = DexKitUtils.open(apkPath)) {
            MethodData openComment = XhsCommentHookTargets.requireUnique(
                    "comment entry", bridge.findMethod(
                            FindMethod.create().matcher(MethodMatcher.create()
                                    .declaredClass(COMMENT_SPI)
                                    .name(OPEN_COMMENT))));

            ClassData argumentsType = openComment.getParamTypes().get(1);
            MethodDataList routeData = argumentsType.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create()
                            .returnType("boolean")
                            .paramTypes()
                            .addCaller(openComment.getDescriptor())));
            List<MethodData> routeCandidates = XhsCommentHookTargets.requireRouteGates(
                    new ArrayList<>(routeData));

            MethodDataList allFactoryData = bridge.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create()
                            .returnType("android.app.Dialog")
                            .addInvoke(MethodMatcher.create()
                                    .declaredClass(LANDSCAPE_DIALOG)
                                    .name("<init>"))));
            List<MethodData> factoryCandidates = new ArrayList<>();
            for (MethodData candidate : allFactoryData) {
                int count = candidate.getParamCount();
                if (count >= 15 && count <= 17) factoryCandidates.add(candidate);
            }
            factoryCandidates = XhsCommentHookTargets.requireFactories(factoryCandidates);

            Map<String, MethodData> screenCandidates = new LinkedHashMap<>();
            for (MethodData factory : factoryCandidates) {
                for (MethodData invoked : factory.getInvokes()) {
                    if ("boolean".equals(invoked.getReturnTypeName())
                            && invoked.getParamTypeNames().equals(
                                    List.of("android.content.Context"))) {
                        screenCandidates.put(invoked.getDescriptor(), invoked);
                    }
                }
            }
            MethodData screenGate = XhsCommentHookTargets.requireUnique(
                    "screen gate", new ArrayList<>(screenCandidates.values()));

            return new XhsCommentHookTargets(
                    toMethods(routeCandidates, classLoader),
                    toMethods(factoryCandidates, classLoader),
                    screenGate.getMethodInstance(classLoader));
        }
    }

    private static List<Method> toMethods(List<MethodData> data, ClassLoader classLoader)
            throws NoSuchMethodException {
        List<Method> methods = new ArrayList<>(data.size());
        for (MethodData method : data) methods.add(method.getMethodInstance(classLoader));
        return methods;
    }
}
