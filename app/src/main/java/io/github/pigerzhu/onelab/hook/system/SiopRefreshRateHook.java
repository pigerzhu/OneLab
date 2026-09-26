package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.content.ContentResolver;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Blocks only Samsung SIOP's low-refresh-rate token while hard bypass is active. */
public final class SiopRefreshRateHook {
    private static final String BINDER_CLASS =
            "com.android.server.display.DisplayManagerService$BinderService";

    private SiopRefreshRateHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> binderClass = XposedHelpers.findClass(BINDER_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(
                    binderClass,
                    "acquireLowRefreshRateToken",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String tag = stringArgument(param.args);
                            ContentResolver resolver = HookUtils.resolverFromAnyContext(
                                    param.thisObject);
                            boolean enabled = HookUtils.globalEnabled(
                                    resolver,
                                    SettingsKeys.KEY_ENABLE_THERMAL_HARD_BYPASS,
                                    0);
                            if (SiopRefreshRatePolicy.shouldBlock(enabled, tag)) {
                                param.setResult(null);
                            }
                        }
                    });
            Log.i(HookConstants.TAG, "Hooked Samsung SIOP low-refresh token");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SIOP low-refresh hook failed");
            XposedBridge.log(t);
        }
    }

    private static String stringArgument(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof String) return (String) arg;
        }
        return null;
    }
}
