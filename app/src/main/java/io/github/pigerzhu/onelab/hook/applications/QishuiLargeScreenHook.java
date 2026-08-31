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
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

/** Enables Qishui Music's native Pad gates only for the current large window. */
public final class QishuiLargeScreenHook {
    private static final String TAG = "OneLab/QishuiLargeScreen";
    private static final String PAD_UTILS = "com.luna.common.arch.util.pad.PadUtils";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private QishuiLargeScreenHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(lpparam.processName)) return;
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Application) || param.args.length == 0
                        || !(param.args[0] instanceof Context)
                        || !INSTALLED.compareAndSet(false, true)) {
                    return;
                }
                Context context = ((Context) param.args[0]).getApplicationContext();
                installForContext(context, context.getClassLoader());
            }
        });
    }

    private static void installForContext(Context context, ClassLoader classLoader) {
        AtomicBoolean padEnabled = new AtomicBoolean(isPadEnabled(context));
        AtomicBoolean playerEnabled = new AtomicBoolean(isPlayerEnabled(context));
        int[] windowDp = currentWindowDp(context);
        observeSettings(context, padEnabled, playerEnabled);
        observeWindow(context, windowDp);

        try {
            XposedHelpers.findAndHookMethod(
                    PAD_UTILS,
                    classLoader,
                    "isPad",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (QishuiLargeScreenPolicy.shouldForcePad(
                                    padEnabled.get(), windowDp[0], windowDp[1])) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    });
            Log.i(TAG, "Installed stable PadUtils gate");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": PadUtils gate unavailable: " + t);
        }

        try {
            Method playerGate = QishuiLargeScreenLocator.findPlayerLayoutGate(
                    context.getApplicationInfo().sourceDir, classLoader);
            XposedBridge.hookMethod(playerGate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (QishuiLargeScreenPolicy.shouldForcePlayerLayout(
                            padEnabled.get(), playerEnabled.get(), windowDp[0], windowDp[1])) {
                        param.setResult(Boolean.TRUE);
                    }
                }
            });
            Log.i(TAG, "Installed player-layout gate from stable pre-inflation route");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": player-layout gate unavailable: " + t);
        }
    }

    private static void observeSettings(
            Context context, AtomicBoolean padEnabled, AtomicBoolean playerEnabled) {
        ContentResolver resolver = context.getContentResolver();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                padEnabled.set(isPadEnabled(context));
                playerEnabled.set(isPlayerEnabled(context));
            }
        };
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_QISHUI_LARGE_SCREEN),
                false,
                observer);
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_QISHUI_PAD_PLAYER_LAYOUT),
                false,
                observer);
    }

    private static void observeWindow(Context context, int[] windowDp) {
        context.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration config) {
                windowDp[0] = config.screenWidthDp;
                windowDp[1] = config.screenHeightDp;
            }

            @Override
            public void onLowMemory() {
            }
        });
    }

    private static int[] currentWindowDp(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return new int[]{config.screenWidthDp, config.screenHeightDp};
    }

    private static boolean isPadEnabled(Context context) {
        return HookUtils.globalEnabled(context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_QISHUI_LARGE_SCREEN, 0);
    }

    private static boolean isPlayerEnabled(Context context) {
        return HookUtils.globalEnabled(context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_QISHUI_PAD_PLAYER_LAYOUT, 0);
    }
}
