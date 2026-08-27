package io.github.pigerzhu.onelab.hook.samsung;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;

/**
 * Applies the configured ratio at the source for apps using the public AndroidX
 * Window Activity Embedding API.
 */
public final class ActivityEmbeddingRatioHook {
    private static final String TAG = "OneLab/ActivityEmbeddingRatio";

    private static final Object INSTALL_LOCK = new Object();
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    private static volatile Float ratio;
    private static volatile boolean observerRegistered;

    private ActivityEmbeddingRatioHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        install(lpparam, false);
    }

    public static void installIfConfigured(XC_LoadPackage.LoadPackageParam lpparam) {
        install(lpparam, true);
    }

    private static void install(
            XC_LoadPackage.LoadPackageParam lpparam,
            boolean requireConfiguredRatio
    ) {
        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = (Context) param.args[0];
                        if (requireConfiguredRatio
                                && !hasConfiguredRatio(
                                context.getContentResolver(), lpparam.packageName)) {
                            return;
                        }
                        installForClassLoader(
                                context,
                                context.getClassLoader(),
                                lpparam.packageName);
                    }
                });
    }

    private static boolean hasConfiguredRatio(ContentResolver resolver, String packageName) {
        Map<String, Float> values = SplitViewRatioOverrides.parse(
                Settings.Global.getString(resolver, KEY_SPLIT_VIEW_RATIO_OVERRIDES));
        return values.containsKey(packageName);
    }

    private static void installForClassLoader(
            Context context,
            ClassLoader classLoader,
            String packageName
    ) {
        if (classLoader == null) return;
        synchronized (INSTALL_LOCK) {
            if (!INSTALLED_LOADERS.add(classLoader)) return;
        }

        initialize(context.getContentResolver(), packageName);
        boolean installed = installSplitAttributesBuilder(classLoader);
        installed |= installLegacySplitRule(classLoader);
        for (String builderClassName : ActivityEmbeddingRatioTargets.builderClassNames()) {
            installed |= installRatioRuleBuilder(classLoader, builderClassName);
        }
        if (installed) {
            XposedBridge.log(TAG + ": installed for " + packageName);
        } else {
            XposedBridge.log(TAG + ": standard AndroidX API unavailable for " + packageName);
        }
    }

    private static boolean installSplitAttributesBuilder(ClassLoader classLoader) {
        Class<?> builderClass = XposedHelpers.findClassIfExists(
                "androidx.window.embedding.SplitAttributes$Builder",
                classLoader);
        Class<?> splitTypeClass = XposedHelpers.findClassIfExists(
                "androidx.window.embedding.SplitAttributes$SplitType",
                classLoader);
        if (builderClass == null || splitTypeClass == null) return false;

        XposedBridge.hookAllMethods(builderClass, "build", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Float current = ratio;
                if (current == null) return;
                try {
                    Object splitType = XposedHelpers.callStaticMethod(
                            splitTypeClass,
                            "ratio",
                            current);
                    XposedHelpers.callMethod(param.thisObject, "setSplitType", splitType);
                } catch (Throwable throwable) {
                    logFailureOnce("SplitAttributes", throwable);
                }
            }
        });
        return true;
    }

    private static boolean installLegacySplitRule(ClassLoader classLoader) {
        Class<?> splitRuleClass = XposedHelpers.findClassIfExists(
                "androidx.window.embedding.SplitRule",
                classLoader);
        if (splitRuleClass == null) return false;

        XposedBridge.hookAllConstructors(splitRuleClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Float current = ratio;
                if (current == null
                        || param.args.length != 4
                        || !(param.args[0] instanceof Integer)
                        || !(param.args[1] instanceof Integer)
                        || !(param.args[2] instanceof Float)
                        || !(param.args[3] instanceof Integer)) {
                    return;
                }
                param.args[2] = current;
            }
        });
        return true;
    }

    private static boolean installRatioRuleBuilder(
            ClassLoader classLoader,
            String className
    ) {
        Class<?> builderClass = XposedHelpers.findClassIfExists(className, classLoader);
        if (builderClass == null || !hasFloatSetter(builderClass, "setSplitRatio")) {
            return false;
        }

        XposedBridge.hookAllMethods(builderClass, "build", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Float current = ratio;
                if (current == null) return;
                try {
                    XposedHelpers.callMethod(param.thisObject, "setSplitRatio", current);
                } catch (Throwable throwable) {
                    logFailureOnce(className, throwable);
                }
            }
        });
        return true;
    }

    private static boolean hasFloatSetter(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (methodName.equals(method.getName())
                    && parameters.length == 1
                    && parameters[0] == float.class) {
                return true;
            }
        }
        return false;
    }

    private static void initialize(ContentResolver resolver, String packageName) {
        refresh(resolver, packageName);
        if (observerRegistered) return;
        synchronized (ActivityEmbeddingRatioHook.class) {
            if (observerRegistered) return;
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(KEY_SPLIT_VIEW_RATIO_OVERRIDES),
                    false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            refresh(resolver, packageName);
                        }
                    });
            observerRegistered = true;
        }
    }

    private static void refresh(ContentResolver resolver, String packageName) {
        Map<String, Float> values = SplitViewRatioOverrides.parse(
                Settings.Global.getString(resolver, KEY_SPLIT_VIEW_RATIO_OVERRIDES));
        ratio = values.get(packageName);
    }

    private static void logFailureOnce(String path, Throwable throwable) {
        if (!LOGGED_FAILURE.compareAndSet(false, true)) return;
        XposedBridge.log(TAG + ": " + path + " update failed");
        XposedBridge.log(throwable);
    }
}
