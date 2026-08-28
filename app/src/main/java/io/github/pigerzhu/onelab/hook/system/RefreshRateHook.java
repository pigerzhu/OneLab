package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.contract.RefreshRateOverride;
import io.github.pigerzhu.onelab.contract.RefreshRateOverrides;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import io.github.pigerzhu.onelab.contract.SettingsKeys;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Applies per-app refresh-rate policies without doing I/O or scheduling traversals in WM hot paths. */
public final class RefreshRateHook {
    private static final String POLICY_CLASS = "com.android.server.wm.RefreshRatePolicy";
    private static final String SPECIAL_LIST_CLASS =
            "com.samsung.android.server.packagefeature.util.PackageSpecialManagementList";
    private static final String RANGE_CLASS = "android.view.SurfaceControl$RefreshRateRange";

    private static final Object LOCK = new Object();
    private static final Object ABSENT = new Object();
    private static final Map<Object, PolicyState> POLICY_STATES = new ConcurrentHashMap<>();

    private static volatile ContentResolver resolver;
    private static volatile Map<String, RefreshRateOverride> overrides =
            Collections.emptyMap();
    private static volatile Object highRefreshRateBlockList;
    private static boolean observerRegistered;
    private static boolean configLoaded;
    private static String cachedRaw;

    private RefreshRateHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> policyClass = XposedHelpers.findClass(POLICY_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(policyClass, "getPreferredModeId", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ensureRegistered(param.thisObject);
                    String packageName = packageNameFromWindowState(firstArg(param));
                    RefreshRateOverride override = overrides.get(packageName);
                    if (override == null || override.mode != RefreshRateOverrides.MODE_FIXED) {
                        return;
                    }
                    FixedSelection selection = fixedSelection(param.thisObject, packageName);
                    if (selection != null) {
                        param.setResult(selection.modeId);
                    }
                }
            });

            XposedBridge.hookAllMethods(
                    policyClass,
                    "getRefreshRateFromFixedRefreshRatePackages",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ensureRegistered(param.thisObject);
                            String packageName = packageNameFromWindowState(firstArg(param));
                            RefreshRateOverride override = overrides.get(packageName);
                            if (override == null || override.mode != RefreshRateOverrides.MODE_FIXED) {
                                return;
                            }
                            FixedSelection selection = fixedSelection(param.thisObject, packageName);
                            if (selection != null) {
                                param.setResult(selection.refreshRate);
                            }
                        }
                    }
            );

            Class<?> specialListClass = XposedHelpers.findClass(SPECIAL_LIST_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(specialListClass, "contains", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject != highRefreshRateBlockList
                            || param.args == null
                            || param.args.length == 0
                            || !(param.args[0] instanceof String)) {
                        return;
                    }
                    if (overrides.containsKey((String) param.args[0])) {
                        param.setResult(false);
                    }
                }
            });
            Log.i(HookConstants.TAG, "Hooked Samsung per-app refresh-rate policy safely");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": refresh-rate hook failed");
            XposedBridge.log(t);
        }
    }

    private static Object firstArg(XC_MethodHook.MethodHookParam param) {
        return param.args != null && param.args.length > 0 ? param.args[0] : null;
    }

    private static void ensureRegistered(Object policy) {
        if (!POLICY_STATES.containsKey(policy)) {
            registerPolicy(policy);
        } else if (highRefreshRateBlockList == null) {
            captureHighRefreshRateList(policy);
        }
    }

    private static void registerPolicy(Object policy) {
        if (policy == null) {
            return;
        }
        PolicyState newState = new PolicyState(policy);
        boolean added = POLICY_STATES.putIfAbsent(policy, newState) == null;
        captureHighRefreshRateList(policy);
        ensureObserver(policy);
        if (added && configLoaded) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> applyCurrentConfigToPolicy(policy));
        }
    }

    private static void ensureObserver(Object policy) {
        if (observerRegistered) {
            return;
        }
        Object wmService = HookUtils.findFieldValue(policy, "mWmService");
        ContentResolver contentResolver = HookUtils.resolverFromAnyContext(wmService);
        if (contentResolver == null) {
            return;
        }
        synchronized (LOCK) {
            if (observerRegistered) {
                return;
            }
            resolver = contentResolver;
            Handler handler = new Handler(Looper.getMainLooper());
            contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(SettingsKeys.KEY_REFRESH_RATE_OVERRIDES),
                    false,
                    new ContentObserver(handler) {
                        @Override
                        public void onChange(boolean selfChange) {
                            reloadConfig();
                        }
                    }
            );
            observerRegistered = true;
            Log.i(HookConstants.TAG, "Registered refresh-rate settings observer");
            handler.post(RefreshRateHook::reloadConfig);
        }
    }

    private static void reloadConfig() {
        ContentResolver contentResolver = resolver;
        if (contentResolver == null) {
            return;
        }
        try {
            String raw = Settings.Global.getString(
                    contentResolver,
                    SettingsKeys.KEY_REFRESH_RATE_OVERRIDES
            );
            synchronized (LOCK) {
                if (configLoaded && Objects.equals(raw, cachedRaw)) {
                    return;
                }
                cachedRaw = raw;
                configLoaded = true;
            }
            Map<String, RefreshRateOverride> parsed = RefreshRateOverrides.parse(raw);
            applyConfig(parsed);
            overrides = parsed;
            requestTraversals();
            Log.i(HookConstants.TAG, "Applied " + parsed.size() + " per-app refresh-rate policies");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": refresh-rate config reload failed");
            XposedBridge.log(t);
        }
    }

    private static void applyConfig(Map<String, RefreshRateOverride> parsed) {
        List<PolicyState> states = new ArrayList<>(POLICY_STATES.values());
        for (PolicyState state : states) {
            applyConfigToPolicy(state, parsed);
        }
    }

    private static void applyCurrentConfigToPolicy(Object policy) {
        PolicyState state = POLICY_STATES.get(policy);
        if (state == null) {
            return;
        }
        applyConfigToPolicy(state, overrides);
        requestTraversal(state.wmService);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyConfigToPolicy(PolicyState state, Map<String, RefreshRateOverride> parsed) {
        Map<String, FixedSelection> fixedSelections = new HashMap<>();
        Set<String> desiredRanges = new HashSet<>();
        for (Map.Entry<String, RefreshRateOverride> entry : parsed.entrySet()) {
            RefreshRateOverride override = entry.getValue();
            if (override.mode == RefreshRateOverrides.MODE_FIXED) {
                FixedSelection selection = selectionForRate(state.policy, override.min);
                if (selection != null) {
                    fixedSelections.put(entry.getKey(), selection);
                }
            } else if (override.mode == RefreshRateOverrides.MODE_RANGE) {
                desiredRanges.add(entry.getKey());
            }
        }

        Object packageRanges = HookUtils.findFieldValue(state.policy, "mNonHighRefreshRatePackages");
        Object ranges = HookUtils.findFieldValue(packageRanges, "mPackages");
        if (ranges instanceof Map) {
            Map rangeMap = (Map) ranges;
            Object monitor = state.globalLock != null ? state.globalLock : state;
            synchronized (monitor) {
                Set<String> appliedRanges = new HashSet<>();
                Set<String> previousRanges = new HashSet<>(state.managedRangePackages);
                for (String packageName : previousRanges) {
                    if (!desiredRanges.contains(packageName)) {
                        restoreOriginalRange(state, rangeMap, packageName);
                    }
                }
                for (String packageName : desiredRanges) {
                    if (!state.originalRanges.containsKey(packageName)) {
                        state.originalRanges.put(
                                packageName,
                                rangeMap.containsKey(packageName) ? rangeMap.get(packageName) : ABSENT
                        );
                    }
                    RefreshRateOverride override = parsed.get(packageName);
                    Object range = newRangeForPolicy(state.policy, override.min, override.max);
                    if (range != null) {
                        rangeMap.put(packageName, range);
                        appliedRanges.add(packageName);
                    } else {
                        restoreOriginalRange(state, rangeMap, packageName);
                    }
                }
                state.managedRangePackages.clear();
                state.managedRangePackages.addAll(appliedRanges);
            }
        }
        state.fixedSelections = Collections.unmodifiableMap(fixedSelections);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreOriginalRange(PolicyState state, Map rangeMap, String packageName) {
        Object original = state.originalRanges.remove(packageName);
        if (original == null || original == ABSENT) {
            rangeMap.remove(packageName);
        } else {
            rangeMap.put(packageName, original);
        }
    }

    private static void requestTraversals() {
        Set<Object> services = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PolicyState state : POLICY_STATES.values()) {
            if (state.wmService != null) {
                services.add(state.wmService);
            }
        }
        for (Object wmService : services) {
            requestTraversal(wmService);
        }
    }

    private static void requestTraversal(Object wmService) {
        if (wmService == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(wmService, "requestTraversal");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": refresh-rate traversal request failed");
            XposedBridge.log(t);
        }
    }

    private static void captureHighRefreshRateList(Object policy) {
        if (highRefreshRateBlockList != null || policy == null) {
            return;
        }
        Object wmService = HookUtils.findFieldValue(policy, "mWmService");
        Object atmService = HookUtils.findFieldValue(wmService, "mAtmService");
        Object extension = HookUtils.findFieldValue(atmService, "mExt");
        Object list = HookUtils.findFieldValue(extension, "mHighRefreshRateBlockList");
        if (list != null) {
            highRefreshRateBlockList = list;
        }
    }

    private static String packageNameFromWindowState(Object windowState) {
        Object attrs = HookUtils.findFieldValue(windowState, "mAttrs");
        Object packageName = HookUtils.findFieldValue(attrs, "packageName");
        return packageName instanceof String ? (String) packageName : null;
    }

    private static FixedSelection fixedSelection(Object policy, String packageName) {
        PolicyState state = POLICY_STATES.get(policy);
        return state == null ? null : state.fixedSelections.get(packageName);
    }

    private static FixedSelection selectionForRate(Object policy, float requestedRate) {
        Object displayInfo = HookUtils.findFieldValue(policy, "mDisplayInfo");
        Object modes = HookUtils.findFieldValue(displayInfo, "appsSupportedModes");
        if (!(modes instanceof Object[])) {
            return null;
        }
        FixedSelection result = null;
        float closestDistance = Float.MAX_VALUE;
        for (Object mode : (Object[]) modes) {
            try {
                float rate = ((Number) XposedHelpers.callMethod(mode, "getRefreshRate")).floatValue();
                int modeId = ((Number) XposedHelpers.callMethod(mode, "getModeId")).intValue();
                float distance = requestedRate <= 0f ? -rate : Math.abs(rate - requestedRate);
                if (result == null || distance < closestDistance) {
                    result = new FixedSelection(modeId, rate);
                    closestDistance = distance;
                }
            } catch (Throwable ignored) {
                // Ignore an invalid mode and keep evaluating the remaining display modes.
            }
        }
        return result;
    }

    private static Object newRange(ClassLoader loader, float min, float max) {
        try {
            Class<?> rangeClass = XposedHelpers.findClass(RANGE_CLASS, loader);
            java.lang.reflect.Constructor<?> constructor = rangeClass.getDeclaredConstructor(
                    float.class,
                    float.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(min, max);
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": cannot create refresh-rate range");
            XposedBridge.log(t);
            return null;
        }
    }

    private static Object newRangeForPolicy(Object policy, float requestedMin, float requestedMax) {
        float supportedMin = floatField(policy, "mMinSupportedRefreshRate", requestedMin);
        float supportedMax = floatField(policy, "mMaxSupportedRefreshRate", requestedMax);
        float min = Math.max(supportedMin, Math.min(requestedMin, supportedMax));
        float max = Math.max(min, Math.min(requestedMax, supportedMax));
        return newRange(policy.getClass().getClassLoader(), min, max);
    }

    private static float floatField(Object target, String name, float fallback) {
        Object value = HookUtils.findFieldValue(target, name);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static final class PolicyState {
        final Object policy;
        final Object wmService;
        final Object globalLock;
        final Map<String, Object> originalRanges = new HashMap<>();
        final Set<String> managedRangePackages = new HashSet<>();
        volatile Map<String, FixedSelection> fixedSelections = Collections.emptyMap();

        PolicyState(Object policy) {
            this.policy = policy;
            this.wmService = HookUtils.findFieldValue(policy, "mWmService");
            this.globalLock = HookUtils.findFieldValue(wmService, "mGlobalLock");
        }
    }

    private static final class FixedSelection {
        final int modeId;
        final float refreshRate;

        FixedSelection(int modeId, float refreshRate) {
            this.modeId = modeId;
            this.refreshRate = refreshRate;
        }
    }
}
