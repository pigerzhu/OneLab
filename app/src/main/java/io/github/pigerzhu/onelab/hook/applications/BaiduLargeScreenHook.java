package io.github.pigerzhu.onelab.hook.applications;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

/** Enables Baidu's native expanded-window, PadHome, and Fold dialog branches. */
public final class BaiduLargeScreenHook {
    private static final String TAG = "OneLab/BaiduLargeScreen";
    private static final String WINDOW_UTILS =
            "com.baidu.android.util.devices.MagicWindowUtilsKt";
    private static final String WINDOW_TYPE =
            "com.baidu.android.util.devices.AdaptiveWindowType";
    private static final String DEVICE_UTILS =
            "com.baidu.android.util.devices.DeviceUtils";
    private static final String DIALOG_RUNTIME =
            "com.baidu.android.ext.widget.ioc.BasicDialogRuntime";

    private static final Object INSTALL_LOCK = new Object();
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Class<?>> INSTALLED_DIALOG_IOC_CLASSES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicBoolean LOGGED_ACTIVE = new AtomicBoolean();

    private BaiduLargeScreenHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
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

        AtomicBoolean enabled = new AtomicBoolean(isEnabled(context));
        AtomicBoolean largeWindow = new AtomicBoolean(isLargeWindow(context));
        observeSetting(context, enabled);
        observeWindowConfiguration(context, largeWindow);

        int installed = 0;
        installed += installAdaptiveWindowHook(classLoader, enabled, largeWindow) ? 1 : 0;
        installed += installTabletDeviceHook(classLoader, enabled, largeWindow) ? 1 : 0;
        installed += installDialogHooks(classLoader, enabled, largeWindow) ? 1 : 0;
        Log.i(TAG, "Installed " + installed + "/3 native large-screen gates");
    }

    private static boolean installAdaptiveWindowHook(
            ClassLoader classLoader,
            AtomicBoolean enabled,
            AtomicBoolean largeWindow) {
        try {
            Class<?> utils = classLoader.loadClass(WINDOW_UTILS);
            Class<?> type = classLoader.loadClass(WINDOW_TYPE);
            Object expanded = XposedHelpers.getStaticObjectField(type, "EXPANDED");
            XposedBridge.hookAllMethods(utils, "getAdaptiveWindowType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!isActive(enabled, largeWindow)) return;
                    if (param.args != null && param.args.length == 2
                            && param.args[0] instanceof Integer
                            && param.args[1] instanceof Integer
                            && !BaiduWindowPolicy.isLargeWindow(
                                    (Integer) param.args[0], (Integer) param.args[1])) {
                        return;
                    }
                    param.setResult(expanded);
                    logActiveOnce();
                }
            });
            return true;
        } catch (Throwable t) {
            logUnavailable("adaptive window", t);
            return false;
        }
    }

    private static boolean installTabletDeviceHook(
            ClassLoader classLoader,
            AtomicBoolean enabled,
            AtomicBoolean largeWindow) {
        try {
            Class<?> deviceUtils = classLoader.loadClass(DEVICE_UTILS);
            XposedHelpers.findAndHookMethod(deviceUtils, "isTabletDevice", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (isActive(enabled, largeWindow)) param.setResult(true);
                }
            });
            return true;
        } catch (Throwable t) {
            logUnavailable("tablet classification", t);
            return false;
        }
    }

    private static boolean installDialogHooks(
            ClassLoader classLoader,
            AtomicBoolean enabled,
            AtomicBoolean largeWindow) {
        try {
            Class<?> runtime = classLoader.loadClass(DIALOG_RUNTIME);
            XposedBridge.hookAllMethods(runtime, "getBasicDialogIOC", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object ioc = param.getResult();
                    if (ioc != null) installDialogIocClass(ioc.getClass(), enabled, largeWindow);
                }
            });
            return true;
        } catch (Throwable t) {
            logUnavailable("dialog IOC", t);
            return false;
        }
    }

    private static void installDialogIocClass(
            Class<?> iocClass,
            AtomicBoolean enabled,
            AtomicBoolean largeWindow) {
        synchronized (INSTALL_LOCK) {
            if (!INSTALLED_DIALOG_IOC_CLASSES.add(iocClass)) return;
        }
        XC_MethodHook forceEnabled = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (isActive(enabled, largeWindow)) param.setResult(true);
            }
        };
        hookOptional(iocClass, "isFoldNewStyle", forceEnabled);
        hookOptional(iocClass, "isPadHome", forceEnabled);
        hookOptional(iocClass, "isPadSideBar", forceEnabled);
    }

    private static void hookOptional(Class<?> type, String method, XC_MethodHook callback) {
        try {
            XposedBridge.hookAllMethods(type, method, callback);
        } catch (Throwable t) {
            logUnavailable("dialog " + method, t);
        }
    }

    private static boolean isActive(
            AtomicBoolean enabled, AtomicBoolean largeWindow) {
        return enabled.get() && largeWindow.get();
    }

    private static void observeSetting(Context context, AtomicBoolean enabled) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_BAIDU_LARGE_SCREEN),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        enabled.set(isEnabled(context));
                    }
                });
    }

    private static void observeWindowConfiguration(
            Context context, AtomicBoolean largeWindow) {
        context.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                largeWindow.set(BaiduWindowPolicy.isLargeWindow(
                        newConfig.screenWidthDp, newConfig.screenHeightDp));
            }

            @Override
            public void onLowMemory() {
            }
        });
    }

    private static boolean isLargeWindow(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return BaiduWindowPolicy.isLargeWindow(config.screenWidthDp, config.screenHeightDp);
    }

    private static boolean isEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_BAIDU_LARGE_SCREEN,
                0);
    }

    private static void logActiveOnce() {
        if (LOGGED_ACTIVE.compareAndSet(false, true)) {
            Log.i(TAG, "Enabled native EXPANDED and PadHome branches");
        }
    }

    private static void logUnavailable(String gate, Throwable t) {
        XposedBridge.log(TAG + ": " + gate + " unavailable: " + t);
    }
}
