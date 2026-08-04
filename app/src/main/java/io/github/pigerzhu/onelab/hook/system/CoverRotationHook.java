package io.github.pigerzhu.onelab.hook.system;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;

import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Enables SystemUI's native per-posture rotation policy on foldables. */
public final class CoverRotationHook {
    private static final String DEFAULTS_RESOURCE_NAME =
            "config_perDeviceStateRotationLockDefaults";
    private static final String ROTATION_POLICY_CLASS =
            "com.android.internal.view.RotationPolicy";
    private static final String CONTROLLER_CALLER =
            "DeviceStateRotationLockSettingController#readPersistedSetting";
    private static final String[] POSTURE_CONVERTER_CLASSES = {
            "com.android.settingslib.devicestate.PosturesHelper",
            "com.android.settingslib.devicestate.PostureDeviceStateConverter"
    };
    private static final int CLOSED_DEVICE_STATE = 0;
    private static final int OUTER_DISPLAY_POSTURE = 0;
    private static final int UNKNOWN_POSTURE = -1;
    private static final String[] POSTURE_DEFAULTS = {
            "0:1",
            "1:0:2",
            "2:2"
    };

    private static volatile boolean initialized;
    private static volatile boolean enabledForProcess;
    private static volatile int defaultsResourceId;
    private static boolean failureLogged;
    private static boolean closedStateMappingLogged;

    private CoverRotationHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        installClosedStateMappingHook(lpparam.classLoader);
        boolean angleHookInstalled = installRotationAngleHook(lpparam.classLoader);
        if (!angleHookInstalled) {
            XposedBridge.log(HookConstants.TAG
                    + ": cover rotation wrapper is unsupported; posture policy not installed");
            return;
        }

        installApplicationContextHook();
        installDefaultsResourceHook();
        XposedBridge.log(HookConstants.TAG + ": installed SystemUI cover rotation policy");
    }

    private static void installClosedStateMappingHook(ClassLoader classLoader) {
        for (String className : POSTURE_CONVERTER_CLASSES) {
            Class<?> converterClass = XposedHelpers.findClassIfExists(className, classLoader);
            if (converterClass == null) continue;

            try {
                XposedHelpers.findAndHookMethod(
                        converterClass,
                        "deviceStateToPosture",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (!enabledForProcess) return;
                                if ((Integer) param.args[0] != CLOSED_DEVICE_STATE) return;
                                if ((Integer) param.getResult() != UNKNOWN_POSTURE) return;

                                param.setResult(OUTER_DISPLAY_POSTURE);
                                logClosedStateMappingOnce();
                            }
                        });
                return;
            } catch (Throwable throwable) {
                logFailureOnce("installing closed-state posture mapping failed", throwable);
            }
        }
    }

    private static boolean installRotationAngleHook(ClassLoader classLoader) {
        boolean installed = false;
        Class<?> oneUi8 = XposedHelpers.findClassIfExists(
                "com.android.systemui.util.wrapper.RotationPolicyWrapperImpl",
                classLoader);
        if (oneUi8 != null) {
            try {
                XposedHelpers.findAndHookMethod(
                        oneUi8,
                        "setRotationLock",
                        boolean.class,
                        String.class,
                        rotationAngleHook(classLoader, true));
                installed = true;
            } catch (Throwable throwable) {
                logFailureOnce("installing One UI 8 rotation wrapper failed", throwable);
            }
        }

        Class<?> oneUi85 = XposedHelpers.findClassIfExists(
                "com.android.systemui.rotation.impl.RotationPolicyWrapperImpl",
                classLoader);
        if (oneUi85 != null) {
            try {
                XposedHelpers.findAndHookMethod(
                        oneUi85,
                        "setRotationLock",
                        String.class,
                        boolean.class,
                        rotationAngleHook(classLoader, false));
                installed = true;
            } catch (Throwable throwable) {
                logFailureOnce("installing One UI 8.5 rotation wrapper failed", throwable);
            }
        }
        return installed;
    }

    private static XC_MethodHook rotationAngleHook(
            ClassLoader classLoader,
            boolean lockedArgumentFirst
    ) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!enabledForProcess) return;

                boolean locked = (Boolean) param.args[lockedArgumentFirst ? 0 : 1];
                String caller = (String) param.args[lockedArgumentFirst ? 1 : 0];
                if (!locked || !CONTROLLER_CALLER.equals(caller)) return;

                Object context = HookUtils.firstContextFromObject(param.thisObject);
                if (!(context instanceof Context)) return;

                try {
                    Class<?> rotationPolicy = XposedHelpers.findClass(
                            ROTATION_POLICY_CLASS, classLoader);
                    XposedHelpers.callStaticMethod(
                            rotationPolicy,
                            "setRotationLockAtAngle",
                            context,
                            true,
                            0,
                            caller);
                    param.setResult(null);
                } catch (Throwable throwable) {
                    logFailureOnce("forcing natural cover rotation failed", throwable);
                }
            }
        };
    }

    private static void installApplicationContextHook() {
        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        initialize((Context) param.args[0]);
                    }
                });
    }

    private static void initialize(Context context) {
        if (initialized) return;
        synchronized (CoverRotationHook.class) {
            if (initialized) return;
            try {
                defaultsResourceId = context.getResources().getIdentifier(
                        DEFAULTS_RESOURCE_NAME,
                        "array",
                        "android");
                if (defaultsResourceId == 0) {
                    Class<?> internalArrays = XposedHelpers.findClass(
                            "com.android.internal.R$array",
                            context.getClassLoader());
                    defaultsResourceId = XposedHelpers.getStaticIntField(
                            internalArrays,
                            DEFAULTS_RESOURCE_NAME);
                }
                enabledForProcess = Settings.Global.getInt(
                        context.getContentResolver(),
                        SettingsKeys.KEY_COVER_PORTRAIT_ONLY,
                        0) == 1;
                initialized = true;
                XposedBridge.log(HookConstants.TAG
                        + ": cover rotation active=" + enabledForProcess
                        + " resourceId=0x" + Integer.toHexString(defaultsResourceId));
            } catch (Throwable throwable) {
                logFailureOnce("initializing cover rotation policy failed", throwable);
            }
        }
    }

    private static void installDefaultsResourceHook() {
        XposedHelpers.findAndHookMethod(
                Resources.class,
                "getStringArray",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!enabledForProcess || defaultsResourceId == 0) return;
                        if ((Integer) param.args[0] != defaultsResourceId) return;
                        param.setResult(POSTURE_DEFAULTS.clone());
                    }
                });
    }

    private static void logFailureOnce(String message, Throwable throwable) {
        synchronized (CoverRotationHook.class) {
            if (failureLogged) return;
            failureLogged = true;
        }
        XposedBridge.log(HookConstants.TAG + ": " + message);
        XposedBridge.log(throwable);
    }

    private static void logClosedStateMappingOnce() {
        synchronized (CoverRotationHook.class) {
            if (closedStateMappingLogged) return;
            closedStateMappingLogged = true;
        }
        XposedBridge.log(HookConstants.TAG
                + ": mapped Samsung closed device state 0 to outer posture 0");
    }
}
