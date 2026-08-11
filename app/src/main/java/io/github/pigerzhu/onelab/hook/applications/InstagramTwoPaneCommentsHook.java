package io.github.pigerzhu.onelab.hook.applications;

import android.app.Application;
import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Enables Instagram 415's existing Reels two-pane comments experiment. */
public final class InstagramTwoPaneCommentsHook {
    private static final String TAG = "OneLab/InstagramTwoPane";
    private static final String MOBILE_CONFIG_IMPL = "X.2lt";
    private static final String MOBILE_CONFIG_SOURCE = "X.0On";
    private static final String BOOLEAN_QUERY = "B9T";

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
                        || !(param.args[0] instanceof Context)) {
                    return;
                }
                if (!INSTALL_STARTED.compareAndSet(false, true)) return;
                Context context = (Context) param.args[0];
                installForClassLoader(context.getClassLoader());
            }
        });
    }

    private static void installForClassLoader(ClassLoader classLoader) {
        try {
            Class<?> configClass = XposedHelpers.findClass(MOBILE_CONFIG_IMPL, classLoader);
            Class<?> sourceClass = XposedHelpers.findClass(MOBILE_CONFIG_SOURCE, classLoader);
            XposedHelpers.findAndHookMethod(
                    configClass,
                    BOOLEAN_QUERY,
                    sourceClass,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            forceTargetGate(param);
                        }
                    });
            log("installed Instagram 415 MobileConfig hook");
        } catch (Throwable throwable) {
            log("installation failed open");
            XposedBridge.log(throwable);
        }
    }

    private static void forceTargetGate(XC_MethodHook.MethodHookParam param) {
        try {
            if (param.args == null
                    || param.args.length != 2
                    || !(param.args[1] instanceof Long)) {
                return;
            }
            long key = (Long) param.args[1];
            if (!InstagramMobileConfigPolicy.shouldForce(key)) return;

            param.setResult(true);
            logGateOnce(key);
        } catch (Throwable throwable) {
            log("callback failed open");
            XposedBridge.log(throwable);
        }
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
