package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.contract.GpuFrequencyTable;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class SdhmsThermalHook {
    private static final String SDHMS_BINDER_SERVICE = "com.sec.android.sdhms.b";
    private static final String SEM_MULTIWINDOW_MANAGER =
            "com.samsung.android.app.SemMultiWindowManager";
    private static final String SSRM_REQUESTER = "SSRM";
    private static final Map<Object, SdhmsHookConfig.Snapshot> SYNCED_HIDDEN_CONTROLS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object GPU_RANGE_LOCK = new Object();
    private static volatile GpuFrequencyRangeController gpuRangeController;
    private static volatile GpuFrequencyRangeController.Status gpuRangeStatus;

    private SdhmsThermalHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        hookGpuFrequencyRangeExperiment(lpparam);
        SdhmsCompatibility.Profile profile =
                SdhmsCompatibility.detect(lpparam.classLoader);
        Class<?> implClass = profile == null ? null : XposedHelpers.findClassIfExists(
                profile.serviceClassName,
                lpparam.classLoader
        );

        hookSdhmsBinderService(lpparam, implClass, profile);
        hookSsrmMultiWindowLimit(lpparam);
        if (profile == null || implClass == null) {
            Log.w(HookConstants.TAG,
                    "SDHMS implementation profile unavailable; stable hooks remain active");
            persistentLog(
                    "SDHMS implementation profile unavailable; stable hooks remain active");
            return;
        }

        Log.i(HookConstants.TAG, "Matched SDHMS profile: " + profile.label);
        persistentLog("Matched SDHMS profile: " + profile.label);
        hookSdhmsService(lpparam, implClass, profile);
        hookSdhmsThermalDeltaController(lpparam, profile);
        hookSdhmsSiopPerfCaps(lpparam, profile);
    }

    private static void hookGpuFrequencyRangeExperiment(
            XC_LoadPackage.LoadPackageParam lpparam
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.sec.android.sdhms.MainApplication",
                    lpparam.classLoader,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof Context) {
                                initializeGpuRangeController(
                                        (Context) param.thisObject, lpparam.classLoader);
                            }
                        }
                    });
            persistentLog("Hooked GPU range DVFS experiment lifecycle");
        } catch (Throwable t) {
            persistentLog("GPU range DVFS lifecycle hook failed");
            XposedBridge.log(t);
        }
    }

    private static void initializeGpuRangeController(Context context, ClassLoader classLoader) {
        synchronized (GPU_RANGE_LOCK) {
            if (gpuRangeController != null) return;
            SamsungGpuDvfsVoteBackend backend =
                    new SamsungGpuDvfsVoteBackend(context, classLoader);
            int[] frequencies = backend.getCommonSupportedFrequencies();
            SdhmsHookConfig.setRuntimeGpuFrequencies(frequencies);
            try {
                int bootCount = Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.BOOT_COUNT, -1);
                boolean published = Settings.Global.putString(context.getContentResolver(),
                        SettingsKeys.KEY_GPU_SUPPORTED_FREQUENCIES,
                        GpuFrequencyTable.serializeSnapshot(bootCount, frequencies));
                if (!published) persistentLog("GPU frequency snapshot publication failed");
            } catch (Throwable ignored) {
                persistentLog("GPU frequency snapshot publication failed");
            }
            gpuRangeController = new GpuFrequencyRangeController(backend);
        }
        ContentResolver resolver = context.getContentResolver();
        SdhmsHookConfig.observe(resolver, config -> applyGpuRangeConfig(resolver, config));
    }

    private static void applyGpuRangeConfig(
            ContentResolver resolver,
            SdhmsHookConfig.Snapshot config
    ) {
        GpuFrequencyRangeController controller = gpuRangeController;
        if (controller == null) return;
        boolean shouldApply = config.gpuRange != null && GpuFrequencyRangePolicy.shouldApply(
                config.thermalEnabled,
                config.perfCapBypassEnabled,
                config.gpuRangeExperimentEnabled);
        GpuFrequencyRangeController.Status status;
        if (shouldApply) {
            status = controller.apply(true, config.gpuRange);
        } else {
            controller.apply(false, null);
            status = config.gpuRangeExperimentEnabled && config.gpuRange == null
                    ? GpuFrequencyRangeController.Status.FREQUENCIES_UNAVAILABLE
                    : GpuFrequencyRangeController.Status.DISABLED;
        }
        if (status == gpuRangeStatus) return;
        gpuRangeStatus = status;
        try {
            Settings.Global.putString(resolver, SettingsKeys.KEY_GPU_RANGE_RUNTIME_STATUS,
                    status.name().toLowerCase(java.util.Locale.ROOT));
        } catch (Throwable ignored) {
            // Persistent LSPosed logging remains the diagnostic fallback.
        }
        switch (status) {
            case ACTIVE:
                persistentLog("GPU range DVFS active: " + config.gpuRange.minMhz()
                        + "-" + config.gpuRange.maxMhz() + "MHz");
                break;
            case MIN_UNAVAILABLE:
                persistentLog("GPU range DVFS minimum unavailable");
                break;
            case MAX_UNAVAILABLE:
                persistentLog("GPU range DVFS maximum unavailable");
                break;
            case FREQUENCIES_UNAVAILABLE:
                persistentLog("GPU range DVFS frequencies unavailable");
                break;
            case DISABLED:
                persistentLog("GPU range DVFS released");
                break;
            default:
                persistentLog("GPU range DVFS failed");
                break;
        }
    }

    private static void hookSdhmsService(
            XC_LoadPackage.LoadPackageParam lpparam,
            Class<?> implClass,
            SdhmsCompatibility.Profile profile
    ) {
        try {
            hookSdhmsPermissionGate(implClass);
            XposedBridge.hookAllMethods(implClass, "w", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ContentResolver resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                    SdhmsHookConfig.Snapshot config = SdhmsHookConfig.current(resolver);
                    if (resolver != null && config.thermalEnabled) {
                        syncSdhmsHiddenThermalControls(
                                param.thisObject, config, lpparam.classLoader, profile);
                        param.setResult(2);
                    }
                }
            });
            XposedBridge.hookAllMethods(implClass, "H", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ContentResolver resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                    SdhmsHookConfig.Snapshot config = SdhmsHookConfig.current(resolver);
                    if (resolver == null || !config.thermalEnabled
                            || param.args == null || param.args.length != 1) {
                        return;
                    }
                    syncSdhmsHiddenThermalControls(
                            param.thisObject, config, lpparam.classLoader, profile);
                    Boolean result = setCustomThermalDeltaDirect(
                            param.thisObject,
                            param.args[0],
                            lpparam.classLoader,
                            profile
                    );
                    if (result != null) {
                        param.setResult(result);
                        Log.i(HookConstants.TAG, "Applied custom SDHMS thermal delta");
                    }
                }
            });
            XposedBridge.hookAllMethods(implClass, "I", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ContentResolver resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                    SdhmsHookConfig.Snapshot config = SdhmsHookConfig.current(resolver);
                    if (resolver == null || !config.thermalEnabled
                            || param.args == null || param.args.length != 2
                            || !HookConstants.ONELAB_PACKAGE.equals(String.valueOf(param.args[0]))) {
                        return;
                    }
                    syncSdhmsHiddenThermalControls(
                            param.thisObject, config, lpparam.classLoader, profile);
                    Boolean result = setCustomThermalDeltaDirect(
                            param.thisObject,
                            param.args[1],
                            lpparam.classLoader,
                            profile
                    );
                    if (result != null) {
                        param.setResult(result);
                        Log.i(HookConstants.TAG, "Allowed OneLab custom SDHMS thermal delta call");
                    }
                }
            });
            XposedBridge.hookAllMethods(implClass, "G", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ContentResolver resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                    SdhmsHookConfig.Snapshot config = SdhmsHookConfig.current(resolver);
                    if (resolver == null || !config.thermalEnabled
                            || param.args == null || param.args.length != 1) {
                        return;
                    }
                    syncSdhmsHiddenThermalControls(
                            param.thisObject, config, lpparam.classLoader, profile);
                    Boolean result = callThermalGuardianControllerBoolean(
                            param.thisObject,
                            profile.controllerClassName,
                            profile.controllerSetFlagsMethod,
                            param.args[0]
                    );
                    if (result != null) {
                        param.setResult(result);
                        Log.i(HookConstants.TAG, "Routed SDHMS thermal control flag through ThermalGuardian controller");
                    }
                }
            });
            Log.i(HookConstants.TAG, "Hooked SDHMS ThermalGuardian gates");
            persistentLog("Hooked SDHMS ThermalGuardian gates");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS ThermalGuardian hook failed");
            XposedBridge.log(t);
        }
    }

    private static void hookSsrmMultiWindowLimit(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> managerClass = XposedHelpers.findClass(
                    SEM_MULTIWINDOW_MANAGER,
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(managerClass, "setMultiWindowEnabled", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 2
                            || !SSRM_REQUESTER.equals(String.valueOf(param.args[0]))
                            || !Boolean.FALSE.equals(param.args[1])) {
                        return;
                    }
                    ContentResolver resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                    if (SdhmsHookConfig.current(resolver).ssrmMultiWindowLimitDisabled) {
                        param.args[1] = true;
                        Log.i(HookConstants.TAG,
                                "Blocked SDHMS SSRM request to disable multi-window");
                    }
                }
            });
            Log.i(HookConstants.TAG, "Hooked SDHMS SSRM multi-window limit");
            persistentLog("Hooked SDHMS SSRM multi-window limit");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS SSRM multi-window hook failed");
            XposedBridge.log(t);
        }
    }

    private static void hookSdhmsSiopPerfCaps(
            XC_LoadPackage.LoadPackageParam lpparam,
            SdhmsCompatibility.Profile profile
    ) {
        hookSdhmsPerfCapClass(lpparam, profile.gpuCapClassName, "GPUFreqMax", true);
        hookSdhmsPerfCapClass(lpparam, profile.cpuCapClassName, "CPUFreqMax", false);
        hookSdhmsPerfCapClass(
                lpparam,
                profile.littleCpuCapClassName,
                "LittleCPUFreqMax",
                false
        );
    }

    private static void hookSdhmsPerfCapClass(
            XC_LoadPackage.LoadPackageParam lpparam,
            String className,
            String label,
            boolean gpu
    ) {
        try {
            Class<?> capClass = XposedHelpers.findClass(className, lpparam.classLoader);
            XposedBridge.hookAllMethods(capClass, "k", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    ContentResolver resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                    SdhmsHookConfig.Snapshot config = SdhmsHookConfig.current(resolver);
                    if (resolver == null || !config.thermalEnabled
                            || !config.perfCapBypassEnabled) {
                        return;
                    }
                    int requested = (Integer) param.args[0];
                    int replacement = rewriteSdhmsPerfCap(config, requested, gpu);
                    if (replacement != requested) {
                        param.args[0] = replacement;
                    }
                }
            });
            Log.i(HookConstants.TAG, "Hooked SDHMS " + label + " cap class " + className);
            persistentLog("Hooked SDHMS " + label + " cap class " + className);
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS " + label + " cap hook failed: " + className);
            XposedBridge.log(t);
        }
    }

    private static void hookSdhmsBinderService(
            XC_LoadPackage.LoadPackageParam lpparam,
            Class<?> implClass,
            SdhmsCompatibility.Profile profile
    ) {
        try {
            Class<?> binderClass = XposedHelpers.findClass(
                    SDHMS_BINDER_SERVICE,
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(binderClass, "setThermalThrottlingDelta", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length != 1) {
                    return;
                }
                Object thermalService = SdhmsCompatibility.thermalServiceFromBinder(
                        param.thisObject,
                        implClass
                );
                ContentResolver resolver = HookUtils.resolverFromAnyContext(thermalService);
                if (resolver == null) {
                    resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                }
                SdhmsHookConfig.Snapshot config = SdhmsHookConfig.current(resolver);
                if (resolver == null || !config.thermalEnabled) {
                    return;
                }
                syncSdhmsHiddenThermalControls(
                        thermalService, config, lpparam.classLoader, profile);
                Boolean result = setCustomThermalDeltaDirect(
                        thermalService,
                        param.args[0],
                        lpparam.classLoader,
                        profile
                );
                if (result != null) {
                    param.setResult(result);
                    Log.i(HookConstants.TAG, "Applied custom SDHMS thermal delta via binder");
                }
            }
        });
            XposedBridge.hookAllMethods(binderClass, "setThermalThrottlingDeltaWithPackageName", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length != 2
                        || !HookConstants.ONELAB_PACKAGE.equals(String.valueOf(param.args[0]))) {
                    return;
                }
                Object thermalService = SdhmsCompatibility.thermalServiceFromBinder(
                        param.thisObject,
                        implClass
                );
                ContentResolver resolver = HookUtils.resolverFromAnyContext(thermalService);
                if (resolver == null) {
                    resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                }
                SdhmsHookConfig.Snapshot config = SdhmsHookConfig.current(resolver);
                if (resolver == null || !config.thermalEnabled) {
                    return;
                }
                syncSdhmsHiddenThermalControls(
                        thermalService, config, lpparam.classLoader, profile);
                Boolean result = setCustomThermalDeltaDirect(
                        thermalService,
                        param.args[1],
                        lpparam.classLoader,
                        profile
                );
                if (result != null) {
                    param.setResult(result);
                    Log.i(HookConstants.TAG, "Applied custom SDHMS thermal delta with package via binder");
                }
            }
        });
            Log.i(HookConstants.TAG, "Hooked SDHMS binder thermal delta entry");
            persistentLog("Hooked SDHMS binder thermal delta entry");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS binder thermal hook failed");
            XposedBridge.log(t);
        }
    }

    private static void hookSdhmsPermissionGate(Class<?> implClass) {
        XposedBridge.hookAllMethods(implClass, "D", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ContentResolver resolver = HookUtils.resolverFromAnyContext(param.thisObject);
                if (resolver != null && SdhmsHookConfig.current(resolver).thermalEnabled
                        && HookUtils.isCallingOneLabOrShell(HookUtils.firstContextFromObject(param.thisObject))) {
                    param.setResult(Boolean.TRUE);
                    Log.i(HookConstants.TAG, "Allowed OneLab to call SDHMS thermal service");
                }
            }
        });
    }

    private static void hookSdhmsThermalDeltaController(
            XC_LoadPackage.LoadPackageParam lpparam,
            SdhmsCompatibility.Profile profile
    ) {
        try {
            Class<?> controllerClass = XposedHelpers.findClass(
                    profile.controllerClassName,
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(
                    controllerClass,
                    profile.controllerSetDeltaMethod,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length != 1 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    Object context = HookUtils.firstContextFromObject(param.thisObject);
                    ContentResolver resolver = HookUtils.resolverFromContextObject(context);
                    if (resolver == null || !SdhmsHookConfig.current(resolver).thermalEnabled
                            || !HookUtils.isCallingOneLabOrShell(context)) {
                        return;
                    }
                    Boolean result = setCustomThermalDeltaFromController(
                            param.thisObject,
                            context,
                            param.args[0],
                            profile
                    );
                    if (result != null) {
                        param.setResult(result);
                        Log.i(HookConstants.TAG, "Applied custom SDHMS thermal delta via controller");
                    }
                }
                    });
            Log.i(HookConstants.TAG, "Hooked SDHMS ThermalGuardian controller");
            persistentLog("Hooked SDHMS ThermalGuardian controller");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS ThermalGuardian controller hook failed");
            XposedBridge.log(t);
        }
    }

    private static int rewriteSdhmsPerfCap(
            SdhmsHookConfig.Snapshot config,
            int requested,
            boolean gpu
    ) {
        if (requested <= 0) {
            return requested;
        }
        if (!gpu) {
            return config.cpuCapReleaseEnabled ? -1 : requested;
        }
        return GpuFrequencyRangePolicy.rewriteGpuCap(requested, config.gpuFrequencies);
    }

    private static Boolean callThermalGuardianControllerBoolean(
            Object service,
            String controllerClassName,
            String methodName,
            Object arg
    ) {
        Object controller = SdhmsCompatibility.fieldValueByType(
                service,
                controllerClassName
        );
        if (controller == null) {
            return null;
        }
        try {
            Object result = XposedHelpers.callMethod(controller, methodName, arg);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": ThermalGuardian controller call failed: " + methodName);
            XposedBridge.log(t);
            return null;
        }
    }

    private static Boolean setCustomThermalDeltaDirect(
            Object service,
            Object arg,
            ClassLoader classLoader,
            SdhmsCompatibility.Profile profile
    ) {
        if (!(arg instanceof Integer)) {
            return null;
        }
        int delta = Math.max(-5, Math.min(6, (Integer) arg));
        Object context = HookUtils.firstContextFromObject(service);
        if (context == null) {
            context = HookUtils.invokeStaticNoArg(
                    "android.app.ActivityThread",
                    "currentApplication",
                    classLoader
            );
        }
        try {
            Class<?> temperatureClass = XposedHelpers.findClass(
                    "com.sec.android.sdhms.thermal.siop.Temperature",
                    classLoader
            );
            Object skin = temperatureClass.getMethod("valueOf", String.class).invoke(null, "SKIN");
            Method shift = findTemperatureShiftMethod(skin.getClass(), profile);
            if (shift == null) {
                return null;
            }
            shift.invoke(skin, delta * -10);
            if (context instanceof Context) {
                sendThermalDeltaChanged((Context) context, delta);
            }
            return Boolean.TRUE;
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": direct custom thermal delta failed");
            XposedBridge.log(t);
            return null;
        }
    }

    private static Method findTemperatureShiftMethod(
            Class<?> temperatureClass,
            SdhmsCompatibility.Profile profile
    ) {
        String preferred = profile == null ? null : profile.temperatureShiftMethod;
        String[] candidates = preferred == null
                ? new String[]{"p", "o"}
                : new String[]{preferred, "p", "o"};
        for (String name : candidates) {
            try {
                Method method = temperatureClass.getDeclaredMethod(name, int.class);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
                // Try the next verified generation candidate.
            }
        }
        return null;
    }

    private static Boolean setCustomThermalDeltaFromController(
            Object controller,
            Object context,
            Object arg,
            SdhmsCompatibility.Profile profile
    ) {
        if (!(arg instanceof Integer) || controller == null || !(context instanceof Context)) {
            return null;
        }
        int delta = Math.max(-5, Math.min(6, (Integer) arg));
        try {
            int current = (Integer) XposedHelpers.callMethod(
                    controller,
                    profile.controllerGetDeltaMethod
            );
            Method shift = controller.getClass().getDeclaredMethod(
                    profile.controllerShiftDeltaMethod,
                    int.class
            );
            shift.setAccessible(true);
            shift.invoke(controller, current * -1);
            shift.invoke(controller, delta);
            sendThermalDeltaChanged((Context) context, delta);
            return Boolean.TRUE;
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": custom thermal delta failed");
            XposedBridge.log(t);
            return null;
        }
    }

    @SuppressLint("WrongConstant")
    private static void sendThermalDeltaChanged(Context context, int delta) {
        Intent intent = new Intent("com.sec.android.sdhms.action.THERMAL_THROTTLING_DELTA_CHANGED");
        intent.addFlags(16777216);
        intent.putExtra("time", System.currentTimeMillis());
        intent.putExtra("delta", delta);
        context.sendBroadcast(intent);
    }

    private static void syncSdhmsHiddenThermalControls(
            Object service,
            SdhmsHookConfig.Snapshot config,
            ClassLoader classLoader,
            SdhmsCompatibility.Profile profile
    ) {
        if (service == null || SYNCED_HIDDEN_CONTROLS.get(service) == config) {
            return;
        }
        Object context = HookUtils.firstContextFromObject(service);
        if (context == null) {
            return;
        }
        try {
            if (profile == null) {
                return;
            }
            Class<?> managerClass = XposedHelpers.findClass(
                    profile.hiddenLimiterClassName,
                    classLoader
            );
            Method getInstance = managerClass.getDeclaredMethod("g", Context.class);
            getInstance.setAccessible(true);
            Object manager = getInstance.invoke(null, context);
            XposedHelpers.callMethod(manager, "k", config.brightnessLimitDisabled);
            XposedHelpers.callMethod(manager, "l", config.cpThermalMitigationDisabled);
            SYNCED_HIDDEN_CONTROLS.put(service, config);
            Log.i(HookConstants.TAG, "Synced SDHMS hidden thermal controls");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS hidden thermal sync failed");
            XposedBridge.log(t);
        }
    }

    private static void persistentLog(String message) {
        XposedBridge.log(HookConstants.TAG + ": " + message);
    }
}
