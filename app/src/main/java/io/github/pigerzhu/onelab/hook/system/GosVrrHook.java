package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.hook.core.HookConstants;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Keeps only PUBG's GOS game-requested refresh-rate update at 120 Hz. */
public final class GosVrrHook {
    private static final String VRR_CORE_CLASS =
            "com.samsung.android.game.gos.feature.vrr.b";

    private GosVrrHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> vrrCore = XposedHelpers.findClass(VRR_CORE_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(vrrCore, "n", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length != 2
                            || !(param.args[0] instanceof Integer)
                            || !(param.args[1] instanceof String)) {
                        return;
                    }
                    int requestedHz = ((Integer) param.args[0]).intValue();
                    String packageName = (String) param.args[1];
                    int normalizedHz = GosVrrPolicy.normalize(packageName, requestedHz);
                    if (normalizedHz != requestedHz) {
                        param.args[0] = Integer.valueOf(normalizedHz);
                        Log.i(HookConstants.TAG, "Clamped PUBG GOS VRR request "
                                + requestedHz + "Hz to 120Hz");
                    }
                }
            });
            Log.i(HookConstants.TAG, "Hooked GOS VRR game-request path");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": GOS VRR hook failed");
            XposedBridge.log(t);
        }
    }
}
