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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

/** Enables Kuaishou's native tablet routes only while the current window is large. */
public final class KuaishouLargeScreenHook {
    private static final String TAG = "OneLab/KuaishouLargeScreen";
    private static final Object INSTALL_LOCK = new Object();
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicBoolean LOGGED_ACTIVE = new AtomicBoolean();

    private KuaishouLargeScreenHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Application)
                        || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof Context)) {
                    return;
                }
                Context context = (Context) KuaishouAttachTarget.select(
                        param.thisObject, param.args[0]);
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

        try {
            Method gate = KuaishouLargeScreenLocator.find(
                    context.getApplicationInfo().sourceDir, classLoader);
            XposedBridge.hookMethod(gate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!enabled.get()) return;
                    param.setResult(largeWindow.get());
                    if (LOGGED_ACTIVE.compareAndSet(false, true)) {
                        Log.i(TAG, "Applied native tablet gate for current window");
                    }
                }
            });
            Log.i(TAG, "Installed native tablet gate from stable detail route");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": tablet gate unavailable: " + t);
        }
    }

    private static void observeSetting(Context context, AtomicBoolean enabled) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_KUAISHOU_LARGE_SCREEN),
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
                largeWindow.set(KuaishouLargeScreenPolicy.isLargeWindow(
                        newConfig.screenWidthDp, newConfig.screenHeightDp));
            }

            @Override
            public void onLowMemory() {
            }
        });
    }

    private static boolean isLargeWindow(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return KuaishouLargeScreenPolicy.isLargeWindow(
                config.screenWidthDp, config.screenHeightDp);
    }

    private static boolean isEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_KUAISHOU_LARGE_SCREEN,
                0);
    }
}
