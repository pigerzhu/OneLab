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
    private static final AtomicBoolean LOGGED_TABLET_PROMOTION = new AtomicBoolean();

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
            AtomicBoolean tabletLayoutEnabled = new AtomicBoolean(isTabletLayoutEnabled(context));
            observeSetting(context, SettingsKeys.KEY_ENABLE_BILI_FOLD_GATE, enabled);
            observeSetting(
                    context,
                    SettingsKeys.KEY_ENABLE_BILI_TABLET_LAYOUT,
                    tabletLayoutEnabled);
            installLargeScreenGate(classLoader, enabled);
            try {
                installTabletLayoutPromotion(classLoader, enabled, tabletLayoutEnabled);
                Log.i(TAG, "Hooked large-screen gate and optional tablet classification");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": tablet classification hook unavailable");
                XposedBridge.log(t);
                Log.i(TAG, "Hooked large-screen gate without tablet classification");
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

    private static void installTabletLayoutPromotion(
            ClassLoader classLoader,
            AtomicBoolean enabled,
            AtomicBoolean tabletLayoutEnabled) throws ClassNotFoundException {
        Class<?> screenAdjustClass = classLoader.loadClass(SCREEN_ADJUST_CLASS);
        Class<?> windowSizeClass = classLoader.loadClass(WINDOW_SIZE_CLASS);
        Method largeLandscape = XposedHelpers.findMethodExact(
                screenAdjustClass, "isLargeLandscape", windowSizeClass);
        Method largePortrait = XposedHelpers.findMethodExact(
                screenAdjustClass, "isLargePortrait", windowSizeClass);
        Method rawWindowSizeType = XposedHelpers.findMethodExact(
                screenAdjustClass, "getRawWindowSizeType", windowSizeClass);
        Method medium = XposedHelpers.findMethodExact(
                screenAdjustClass, "isMedium", windowSizeClass);

        XposedBridge.hookMethod(
                largeLandscape,
                new TabletClassificationHook(
                        enabled, tabletLayoutEnabled, ClassificationResult.LARGE_LANDSCAPE));
        XposedBridge.hookMethod(
                largePortrait,
                new TabletClassificationHook(
                        enabled, tabletLayoutEnabled, ClassificationResult.LARGE_PORTRAIT));
        XposedBridge.hookMethod(
                rawWindowSizeType,
                new TabletClassificationHook(
                        enabled, tabletLayoutEnabled, ClassificationResult.RAW_TYPE));
        XposedBridge.hookMethod(
                medium,
                new TabletClassificationHook(
                        enabled, tabletLayoutEnabled, ClassificationResult.MEDIUM));
    }

    private enum ClassificationResult {
        LARGE_LANDSCAPE,
        LARGE_PORTRAIT,
        RAW_TYPE,
        MEDIUM
    }

    private static final class TabletClassificationHook extends XC_MethodHook {
        private final AtomicBoolean enabled;
        private final AtomicBoolean tabletLayoutEnabled;
        private final ClassificationResult result;

        TabletClassificationHook(
                AtomicBoolean enabled,
                AtomicBoolean tabletLayoutEnabled,
                ClassificationResult result) {
            this.enabled = enabled;
            this.tabletLayoutEnabled = tabletLayoutEnabled;
            this.result = result;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (!enabled.get() || !tabletLayoutEnabled.get()
                    || param.args == null || param.args.length != 1) {
                return;
            }
            int tabletWindowType = tabletWindowType(param.args[0]);
            if (tabletWindowType == BiliWindowPolicy.TYPE_UNCHANGED) return;

            switch (result) {
                case LARGE_LANDSCAPE:
                    param.setResult(tabletWindowType
                            == BiliWindowPolicy.TYPE_LARGE_LANDSCAPE);
                    break;
                case LARGE_PORTRAIT:
                    param.setResult(tabletWindowType
                            == BiliWindowPolicy.TYPE_LARGE_PORTRAIT);
                    break;
                case RAW_TYPE:
                    param.setResult(tabletWindowType);
                    break;
                case MEDIUM:
                    param.setResult(false);
                    break;
            }
            if (LOGGED_TABLET_PROMOTION.compareAndSet(false, true)) {
                Log.i(TAG, "Promoted unfolded window to orientation-aware tablet layout");
            }
        }
    }

    private static int tabletWindowType(Object windowSizeClass) {
        if (windowSizeClass == null) return BiliWindowPolicy.TYPE_UNCHANGED;
        try {
            int widthDp = (Integer) XposedHelpers.callMethod(windowSizeClass, "getMinWidthDp");
            int heightDp = (Integer) XposedHelpers.callMethod(windowSizeClass, "getMinHeightDp");
            return BiliWindowPolicy.tabletWindowType(widthDp, heightDp);
        } catch (Throwable ignored) {
            return BiliWindowPolicy.TYPE_UNCHANGED;
        }
    }

    private static void observeSetting(Context context, String key, AtomicBoolean value) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(key),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        value.set(HookUtils.globalEnabled(
                                context.getContentResolver(), key, 0));
                    }
                });
    }

    private static boolean isEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(), SettingsKeys.KEY_ENABLE_BILI_FOLD_GATE, 0);
    }

    private static boolean isTabletLayoutEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_BILI_TABLET_LAYOUT,
                0);
    }
}
