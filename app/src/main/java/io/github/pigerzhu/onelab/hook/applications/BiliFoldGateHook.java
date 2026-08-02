package io.github.pigerzhu.onelab.hook.applications;

import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;

/** Lets Bilibili evaluate its own Fold large-screen gate without forcing any layout policy. */
public final class BiliFoldGateHook {
    private static final String TAG = "OneLab/BiliFoldGate";
    private static final String KCONFIG_CLASS = "kntr.base.config.KConfig";
    private static final String CONFIG_METHOD = "config";
    private static final String LARGE_SCREEN_KEY = "dd_screen_adjust_xiaomi_864";
    private static final String SCREEN_ADJUST_CLASS =
            "kntr.common.screen.adjust.KScreenAdjustUtilsKt";
    private static final String WINDOW_SIZE_CLASS = "androidx.window.core.layout.WindowSizeClass";

    private static final Object INSTALL_LOCK = new Object();
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicBoolean LOGGED_REWRITE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_LANDSCAPE_PROMOTION = new AtomicBoolean();

    private BiliFoldGateHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 1
                        || !(param.args[0] instanceof Context)) {
                    return;
                }
                Context context = (Context) param.args[0];
                installForClassLoader(context, context.getClassLoader());
            }
        });
    }

    private static void installForClassLoader(Context context, ClassLoader classLoader) {
        if (classLoader == null) return;
        synchronized (INSTALL_LOCK) {
            if (!INSTALLED_LOADERS.add(classLoader)) return;
        }

        try {
            AtomicBoolean enabled = new AtomicBoolean(isEnabled(context));
            observeEnabledSetting(context, enabled);
            installLargeScreenGate(classLoader, enabled);
            try {
                installFoldLandscapePromotion(classLoader, enabled);
                Log.i(TAG, "Hooked large-screen gate and Fold landscape classification");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Fold landscape classification hook unavailable");
                XposedBridge.log(t);
                Log.i(TAG, "Hooked large-screen gate without landscape promotion");
            }
        } catch (Throwable t) {
            synchronized (INSTALL_LOCK) {
                INSTALLED_LOADERS.remove(classLoader);
            }
            XposedBridge.log(TAG + ": KConfig hook installation failed");
            XposedBridge.log(t);
        }
    }

    private static void installLargeScreenGate(ClassLoader classLoader, AtomicBoolean enabled)
            throws ClassNotFoundException {
        Class<?> configClass = classLoader.loadClass(KCONFIG_CLASS);
        XposedHelpers.findAndHookMethod(
                configClass,
                CONFIG_METHOD,
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!enabled.get()
                                || !LARGE_SCREEN_KEY.equals(param.args[0])
                                || !"off".equals(param.getResult())) {
                            return;
                        }
                        param.setResult("large");
                        if (LOGGED_REWRITE.compareAndSet(false, true)) {
                            Log.i(TAG, LARGE_SCREEN_KEY + " off -> large");
                        }
                    }
                });
    }

    private static void installFoldLandscapePromotion(
            ClassLoader classLoader, AtomicBoolean enabled) throws ClassNotFoundException {
        Class<?> screenAdjustClass = classLoader.loadClass(SCREEN_ADJUST_CLASS);
        Class<?> windowSizeClass = classLoader.loadClass(WINDOW_SIZE_CLASS);
        Method largeLandscape = XposedHelpers.findMethodExact(
                screenAdjustClass, "isLargeLandscape", windowSizeClass);
        Method rawWindowSizeType = XposedHelpers.findMethodExact(
                screenAdjustClass, "getRawWindowSizeType", windowSizeClass);
        Method medium = XposedHelpers.findMethodExact(
                screenAdjustClass, "isMedium", windowSizeClass);

        XposedBridge.hookMethod(
                largeLandscape, new LandscapeClassificationHook(enabled, true));
        XposedBridge.hookMethod(
                rawWindowSizeType, new LandscapeClassificationHook(enabled, 3));
        XposedBridge.hookMethod(
                medium, new LandscapeClassificationHook(enabled, false));
    }

    private static final class LandscapeClassificationHook extends XC_MethodHook {
        private final AtomicBoolean enabled;
        private final Object promotedResult;

        LandscapeClassificationHook(AtomicBoolean enabled, Object promotedResult) {
            this.enabled = enabled;
            this.promotedResult = promotedResult;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (!enabled.get() || param.args == null || param.args.length != 1
                    || !isFoldLandscapeWindow(param.args[0])) {
                return;
            }
            param.setResult(promotedResult);
            if (LOGGED_LANDSCAPE_PROMOTION.compareAndSet(false, true)) {
                Log.i(TAG, "Promoted unfolded landscape window to Large Landscape");
            }
        }
    }

    private static boolean isFoldLandscapeWindow(Object windowSizeClass) {
        if (windowSizeClass == null) return false;
        try {
            int widthDp = (Integer) XposedHelpers.callMethod(windowSizeClass, "getMinWidthDp");
            int heightDp = (Integer) XposedHelpers.callMethod(windowSizeClass, "getMinHeightDp");
            return BiliWindowPolicy.shouldPromoteLandscape(widthDp, heightDp);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void observeEnabledSetting(Context context, AtomicBoolean enabled) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_BILI_FOLD_GATE),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        enabled.set(isEnabled(context));
                    }
                });
    }

    private static boolean isEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(), SettingsKeys.KEY_ENABLE_BILI_FOLD_GATE, 0);
    }
}
