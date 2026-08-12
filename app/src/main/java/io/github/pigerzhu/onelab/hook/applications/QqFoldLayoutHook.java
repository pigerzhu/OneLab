package io.github.pigerzhu.onelab.hook.applications;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.app.Activity;
import android.app.Application;
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

/**
 * Authoritatively controls QQ's native Fold split despite push-environment spoofing.
 */
public final class QqFoldLayoutHook {
    private static final String TAG = "OneLab/QqFoldLayout";
    private static final int LARGE_SCREEN_DP = 600;

    private static final Object INSTALL_LOCK = new Object();
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicBoolean LOGGED_ACTIVE = new AtomicBoolean();

    private QqFoldLayoutHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(lpparam.processName)) return;
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
            long startedAt = System.nanoTime();
            AtomicBoolean enabled = new AtomicBoolean(isEnabled(context));
            Method splitGate = QqFoldGateLocator.find(
                    context.getApplicationInfo().sourceDir, classLoader);
            QqFoldStateOverride foldState = QqFoldStateOverride.create(classLoader);
            hookSplitGate(splitGate, enabled);
            foldState.apply(enabled.get());
            observeEnabledSetting(context, enabled, foldState);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            Log.i(TAG, "Installed semantic QQ Fold gate in " + elapsedMs
                    + " ms: " + splitGate.getDeclaringClass().getName()
                    + "#" + splitGate.getName());
        } catch (Throwable throwable) {
            synchronized (INSTALL_LOCK) {
                INSTALLED_LOADERS.remove(classLoader);
            }
            Log.e(TAG, "Semantic QQ Fold gate installation failed", throwable);
            XposedBridge.log(TAG + ": installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static void hookSplitGate(Method splitGate, AtomicBoolean enabled) {
        XposedBridge.hookMethod(splitGate, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length != 2
                        || !(param.args[0] instanceof Activity)) {
                    return;
                }
                Activity activity = (Activity) param.args[0];
                boolean active = enabled.get()
                        && isExpanded(activity)
                        && !activity.isInMultiWindowMode();
                param.setResult(active);
                if (active) logActive();
            }
        });
    }

    private static boolean isExpanded(Activity activity) {
        Configuration configuration = activity.getResources().getConfiguration();
        return configuration.screenWidthDp >= LARGE_SCREEN_DP
                && configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP;
    }

    private static void observeEnabledSetting(
            Context context,
            AtomicBoolean enabled,
            QqFoldStateOverride foldState
    ) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_QQ_FOLD_LAYOUT),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        boolean active = isEnabled(context);
                        enabled.set(active);
                        try {
                            foldState.apply(active);
                        } catch (Throwable throwable) {
                            Log.e(TAG, "Failed to update QQ Fold state", throwable);
                            XposedBridge.log(TAG + ": failed to update QQ Fold state");
                            XposedBridge.log(throwable);
                        }
                    }
                });
    }

    private static boolean isEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(), SettingsKeys.KEY_ENABLE_QQ_FOLD_LAYOUT, 0);
    }

    private static void logActive() {
        if (LOGGED_ACTIVE.compareAndSet(false, true)) {
            Log.i(TAG, "QQ native Fold classification enabled");
        }
    }
}
