package io.github.pigerzhu.onelab.hook.applications;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;

/** Enables Instagram's existing Reels two-pane comments experiment. */
public final class InstagramTwoPaneCommentsHook {
    private static final String TAG = "OneLab/InstagramTwoPane";

    private static final AtomicBoolean INSTALL_STARTED = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_LARGE_SCREEN_GATE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_COMMENTS_GATE = new AtomicBoolean();

    private InstagramTwoPaneCommentsHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!InstagramMobileConfigPolicy.isMainProcess(
                lpparam.packageName, lpparam.processName)) {
            return;
        }
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null
                        || param.args.length != 1
                        || !(param.args[0] instanceof Context)
                        || !INSTALL_STARTED.compareAndSet(false, true)) {
                    return;
                }
                Context context = (Context) param.args[0];
                AtomicBoolean enabled = new AtomicBoolean(isEnabled(context));
                observeEnabledSetting(context, enabled);
                installForClassLoader(context, enabled);
            }
        });
    }

    private static void installForClassLoader(Context context, AtomicBoolean enabled) {
        try {
            InstagramTwoPaneGateTargets targets = InstagramTwoPaneCommentsLocator.find(
                    context.getApplicationInfo().sourceDir, context.getClassLoader());
            for (var entry : targets.methods.entrySet()) {
                long key = entry.getKey();
                hookGate(entry.getValue(), enabled, key);
            }
            log("installed semantic Instagram gate hooks=" + targets.methods.size());
        } catch (Throwable throwable) {
            log("semantic gate location failed open");
            XposedBridge.log(throwable);
        }
    }

    private static void hookGate(Method method, AtomicBoolean enabled, long key) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!InstagramMobileConfigPolicy.shouldForce(enabled.get(), key)) return;
                    param.setResult(true);
                    logGateOnce(key);
                } catch (Throwable throwable) {
                    log("callback failed open");
                    XposedBridge.log(throwable);
                }
            }
        });
    }

    private static boolean isEnabled(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    SettingsKeys.KEY_ENABLE_INSTAGRAM_TWO_PANE_COMMENTS,
                    0) != 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void observeEnabledSetting(Context context, AtomicBoolean enabled) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_INSTAGRAM_TWO_PANE_COMMENTS),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        enabled.set(isEnabled(context));
                    }
                });
    }

    private static void logGateOnce(long key) {
        AtomicBoolean logged = key == InstagramMobileConfigPolicy.ADAPTIVE_LARGE_SCREEN_GATE
                ? LOGGED_LARGE_SCREEN_GATE
                : LOGGED_COMMENTS_GATE;
        if (logged.compareAndSet(false, true)) log("forced key=" + key);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
