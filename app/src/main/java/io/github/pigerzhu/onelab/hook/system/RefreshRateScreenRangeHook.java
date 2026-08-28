package io.github.pigerzhu.onelab.hook.system;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_REFRESH_RATE_SCREEN_INNER;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_REFRESH_RATE_SCREEN_OUTER;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_INNER_MAX;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_INNER_MIN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_OUTER_MAX;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_OUTER_MIN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_RUNTIME_STATUS;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

/**
 * Screen-level refresh-rate boundary for the Fold main and cover panels, enforced in
 * system_server on top of the existing refresh-rate architecture.
 *
 * <p>Two independent sub-hooks clamp everything the window manager asks of the display,
 * and they only ever act on the same feasible range (the configured range of the
 * current panel intersected with the panel's supported rates), so one half of the
 * feature can never apply while the other half fails:
 * <ul>
 *   <li>{@code DisplayManagerService$LocalService.setDisplayProperties} — the single
 *       aggregation point (RootWindowContainer passes the per-window votes here once per
 *       traversal). The before-hook endpoint-clamps the preferred refresh rate and the
 *       min/max range votes, and only for logical display 0.</li>
 *   <li>{@code RefreshRatePolicy.getPreferredModeId} — mode-id votes bypass refresh-rate
 *       ranges, so the after-hook remaps an out-of-range mode to the nearest supported
 *       in-range mode of the same panel.</li>
 * </ul>
 *
 * <p>Panel identification reuses the public {@code DeviceStateManager} callback and maps
 * state names to panels (CLOSED/CLOSE/TENT → cover, OPENED/OPEN/HALF_OPENED/
 * HALF_FOLDED → main, concurrent defaults by name; One UI 8 and 8.5 use different
 * spellings, both accepted). An unrecognized state clears the panel to UNKNOWN instead
 * of reusing the previous panel, so an outdated role can never leak across a firmware
 * update. Samsung swaps the physical panel behind logical display 0 across fold states,
 * so only display 0 receives the range; every other display keeps its original
 * parameters.
 *
 * <p>The mode table cache is keyed per RefreshRatePolicy and invalidated whenever the
 * DisplayInfo object changes or its in-place fingerprint (logical size and active mode)
 * moves, which is what happens during a fold swap. Initialization never runs on a hot
 * path: it starts from {@code ActivityTaskManagerService.onSystemReady()} on a worker
 * thread, hot paths only schedule bounded, backoff-gated retries, and configuration and
 * device-state readiness fail independently.
 */
public final class RefreshRateScreenRangeHook {
    private static final String TAG = "OneLab/RefreshRateScreenRange";
    private static final String POLICY_CLASS = "com.android.server.wm.RefreshRatePolicy";
    private static final String ATMS_CLASS =
            "com.android.server.wm.ActivityTaskManagerService";
    private static final String DMS_LOCAL_SERVICE_CLASS =
            "com.android.server.display.DisplayManagerService$LocalService";
    private static final String SET_DISPLAY_PROPERTIES_METHOD = "setDisplayProperties";
    private static final String GET_PREFERRED_MODE_ID_METHOD = "getPreferredModeId";
    private static final String ON_SYSTEM_READY_METHOD = "onSystemReady";
    private static final String DEVICE_STATE_MANAGER_CLASS =
            "android.hardware.devicestate.DeviceStateManager";
    private static final String DEVICE_STATE_CALLBACK_CLASS =
            "android.hardware.devicestate.DeviceStateManager$DeviceStateCallback";
    private static final String ONEUI_VERSION_PROPERTY = "ro.build.version.oneui";

    private static final Object INIT_LOCK = new Object();
    private static final AtomicBoolean INIT_SCHEDULED = new AtomicBoolean();

    /** Reflectively resolved DisplayInfo fields, shared by every mode table. */
    private static volatile DisplayInfoFields fields;
    private static volatile boolean modeBackendBroken;

    private static volatile boolean dmsHookInstalled;
    private static volatile boolean modeClampInstalled;
    private static volatile boolean configReady;
    private static volatile boolean deviceStateReady;
    private static volatile boolean initGaveUp;
    private static volatile int initAttempts;
    private static volatile long nextInitAttemptAt;

    private static volatile int panelRole = RefreshRateScreenRangePolicy.PANEL_UNKNOWN;
    private static volatile float[] innerConfig;
    private static volatile float[] outerConfig;
    /** Configured range of the current panel intersected with its supported rates. */
    private static volatile float[] activePanelRange;
    private static volatile boolean tableDirty;
    private static volatile PanelTable displayZeroTable;

    private static volatile ContentResolver resolver;
    private static volatile Object initContextProvider;
    private static volatile Object hookContext;
    private static volatile Object wmService;
    private static volatile String lastPublishedStatus;
    private static volatile String cachedOneUiVersion;

    private static HandlerThread workerThread;
    private static Handler workerHandler;
    private static final ConcurrentHashMap<Object, PanelTable> MODE_TABLES =
            new ConcurrentHashMap<>();

    private RefreshRateScreenRangeHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> localServiceClass = XposedHelpers.findClass(
                    DMS_LOCAL_SERVICE_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(
                    localServiceClass, SET_DISPLAY_PROPERTIES_METHOD, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    applyDisplayPropertiesRange(param);
                }
            });
            dmsHookInstalled = true;
            Log.i(HookConstants.TAG,
                    "Hooked DisplayManagerService.setDisplayProperties for screen ranges");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": display-properties hook failed");
            XposedBridge.log(t);
        }
        try {
            Class<?> policyClass = XposedHelpers.findClass(POLICY_CLASS, lpparam.classLoader);
            // Resolve the reflective field accessors once at install time; a missing
            // member disables the mode clamp, and with it the whole feature, because
            // the two clamps must never apply only half of a configured range.
            fields = DisplayInfoFields.resolve(policyClass);
            XposedBridge.hookAllMethods(
                    policyClass, GET_PREFERRED_MODE_ID_METHOD, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyPreferredModeRange(param);
                }
            });
            modeClampInstalled = true;
            Log.i(HookConstants.TAG,
                    "Hooked RefreshRatePolicy.getPreferredModeId for screen ranges");
        } catch (Throwable t) {
            modeBackendBroken = true;
            XposedBridge.log(TAG + ": preferred-mode hook failed");
            XposedBridge.log(t);
        }
        try {
            // Primary, low-frequency initialization trigger; the documented project
            // pattern for system_server settings access (also used by the split hooks).
            Class<?> atmsClass = XposedHelpers.findClass(ATMS_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(atmsClass, ON_SYSTEM_READY_METHOD, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (initContextProvider == null) {
                        initContextProvider = param.thisObject;
                    }
                    scheduleInit();
                }
            });
            Log.i(HookConstants.TAG,
                    "Hooked ActivityTaskManagerService.onSystemReady for screen ranges");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": onSystemReady hook failed; relying on bounded retries");
            XposedBridge.log(t);
        }
    }

    /** Endpoint-clamps the aggregated display request of the active panel. */
    private static void applyDisplayPropertiesRange(XC_MethodHook.MethodHookParam param) {
        Object[] args = param.args;
        if (args == null || args.length != 9
                || !(args[0] instanceof Integer)
                || !(args[4] instanceof Float) || !(args[5] instanceof Float)) {
            return;
        }
        if (!RefreshRateScreenRangePolicy.appliesToDisplay((Integer) args[0])) {
            return;
        }
        if (initContextProvider == null) {
            // One-time fallback context source when the onSystemReady hook is absent.
            initContextProvider = HookUtils.findFieldValue(param.thisObject, "this$0");
        }
        if (!configReady) {
            maybeScheduleInit();
            return;
        }
        float[] range = activePanelRange;
        if (range == null) {
            return;
        }
        float screenMin = range[0];
        float screenMax = range[1];
        float[] merged = RefreshRateScreenRangePolicy.mergeDisplayRequest(
                (Float) args[4], (Float) args[5], screenMin, screenMax);
        args[4] = merged[0];
        args[5] = merged[1];
        if (args[2] instanceof Float) {
            args[2] = RefreshRateScreenRangePolicy.clampRequest(
                    (Float) args[2], screenMin, screenMax);
        }
    }

    /** Remaps mode-id votes that would bypass the screen range. */
    private static void applyPreferredModeRange(XC_MethodHook.MethodHookParam param) {
        if (!configReady) {
            return;
        }
        Object policy = param.thisObject;
        if (policy == null) return;

        DisplayInfoFields resolvedFields = fields;
        if (modeBackendBroken || resolvedFields == null) {
            return;
        }
        Object displayInfo = resolvedFields.displayInfoOf(policy);
        if (displayInfo == null) return;
        int displayId = resolvedFields.displayIdOf(displayInfo);
        if (!RefreshRateScreenRangePolicy.appliesToDisplay(displayId)) {
            return;
        }
        float[] roleConfig = RefreshRateScreenRangePolicy.rangeForPanel(
                panelRole, innerConfig, outerConfig);
        if (roleConfig == null) {
            return;
        }

        // Table maintenance must run before the result check: most windows never set a
        // preferred mode, and the shared feasible range still has to be built for the
        // display-properties clamp from this same traversal.
        PanelTable table = MODE_TABLES.get(policy);
        if (table == null || tableDirty
                || table.staleFor(displayInfo, resolvedFields)) {
            table = rebuildTable(policy, displayInfo, displayId, resolvedFields);
            if (table == null) return;
        }
        float[] range = activePanelRange;
        if (range == null) {
            return;
        }
        if (!(param.getResult() instanceof Integer)) return;
        int modeId = (Integer) param.getResult();
        if (modeId <= 0) return;
        Float requestedRate = table.rateOf(modeId);
        if (requestedRate == null) return;
        if (RefreshRateScreenRangePolicy.withinRange(requestedRate, range[0], range[1])) {
            return;
        }
        int replacement = RefreshRateScreenRangePolicy.pickModeId(
                table.modeIds, table.rates, requestedRate, range[0], range[1], modeId);
        if (replacement > 0 && replacement != modeId) {
            param.setResult(replacement);
        }
    }

    /** Rebuilds one panel's mode table and refreshes the shared active range. */
    private static PanelTable rebuildTable(
            Object policy, Object displayInfo, int displayId, DisplayInfoFields resolvedFields) {
        PanelTable table = PanelTable.capture(
                policy, displayInfo, displayId, resolvedFields);
        MODE_TABLES.put(policy, table);
        tableDirty = false;
        if (RefreshRateScreenRangePolicy.appliesToDisplay(table.displayId)) {
            displayZeroTable = table;
            Log.i(HookConstants.TAG, "Screen-range mode table rebuilt: display="
                    + table.displayId + " modes=" + table.modeIds.length
                    + " fingerprint=" + table.fingerprint);
        }
        if (wmService == null) {
            wmService = HookUtils.findFieldValue(policy, "mWmService");
        }
        refreshActivePanelRange();
        // The feasible range can change here without a settings change, so the
        // diagnostics snapshot must follow; publishing is deduplicated by content.
        publishStatus();
        return table;
    }

    /**
     * Recomputes the range both hot paths act on: the configured range of the current
     * panel intersected with the panel's supported rates. {@code null} means the feature
     * is fully inactive for the current panel (no config, unknown panel, or no supported
     * mode inside the configured range).
     */
    private static void refreshActivePanelRange() {
        float[] roleConfig = RefreshRateScreenRangePolicy.rangeForPanel(
                panelRole, innerConfig, outerConfig);
        PanelTable table = displayZeroTable;
        activePanelRange = roleConfig == null || table == null
                ? null
                : RefreshRateScreenRangePolicy.intersectWithSupportedRates(
                        roleConfig[0], roleConfig[1], table.rates);
    }

    private static void scheduleInit() {
        if (INIT_SCHEDULED.compareAndSet(false, true)) {
            backgroundHandler().post(() -> {
                INIT_SCHEDULED.set(false);
                tryInitialize();
            });
        }
    }

    /**
     * Hot paths only reach here while configuration initialization has not succeeded
     * yet. The clock gate bounds retries to one attempt per backoff window and the
     * attempt budget is finite, so an uninitialized backend never retries per frame.
     */
    private static void maybeScheduleInit() {
        if (initGaveUp) return;
        long now = SystemClock.elapsedRealtime();
        if (!RefreshRateScreenRangePolicy.initRetryAllowed(
                initAttempts, now, nextInitAttemptAt)) {
            return;
        }
        nextInitAttemptAt = now + RefreshRateScreenRangePolicy.INIT_RETRY_BACKOFF_MS;
        scheduleInit();
    }

    private static void tryInitialize() {
        synchronized (INIT_LOCK) {
            if (initGaveUp) return;
            if (configReady && deviceStateReady) return;
            initAttempts++;
            if (initAttempts > RefreshRateScreenRangePolicy.INIT_MAX_ATTEMPTS) {
                initGaveUp = true;
                publishStatus();
                return;
            }
            Object provider = initContextProvider;
            if (resolver == null && provider != null) {
                resolver = HookUtils.resolverFromAnyContext(provider);
                if (hookContext == null) {
                    hookContext = HookUtils.firstContextFromObject(provider);
                }
            }
            ContentResolver contentResolver = resolver;
            if (contentResolver == null) {
                publishStatus();
                return;
            }
            if (!configReady) {
                ConfigObserver observer = new ConfigObserver(backgroundHandler());
                contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(KEY_ENABLE_REFRESH_RATE_SCREEN_INNER),
                        false, observer);
                contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(KEY_REFRESH_RATE_SCREEN_INNER_MIN),
                        false, observer);
                contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(KEY_REFRESH_RATE_SCREEN_INNER_MAX),
                        false, observer);
                contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(KEY_ENABLE_REFRESH_RATE_SCREEN_OUTER),
                        false, observer);
                contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(KEY_REFRESH_RATE_SCREEN_OUTER_MIN),
                        false, observer);
                contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(KEY_REFRESH_RATE_SCREEN_OUTER_MAX),
                        false, observer);
                configReady = true;
                reloadConfig();
                Log.i(HookConstants.TAG, "Initialized screen refresh-rate range config");
            }
            if (!deviceStateReady) {
                registerDeviceStateCallback();
            }
        }
    }

    private static Handler backgroundHandler() {
        synchronized (RefreshRateScreenRangeHook.class) {
            if (workerThread == null) {
                workerThread = new HandlerThread("OneLabRefreshRateScreenRange");
                workerThread.start();
                workerHandler = new Handler(workerThread.getLooper());
            }
            return workerHandler;
        }
    }

    private static void reloadConfig() {
        ContentResolver contentResolver = resolver;
        if (contentResolver == null) return;
        try {
            innerConfig = RefreshRateScreenRangePolicy.parseScreenRange(
                    Settings.Global.getString(contentResolver,
                            KEY_ENABLE_REFRESH_RATE_SCREEN_INNER),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_INNER_MIN),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_INNER_MAX));
            outerConfig = RefreshRateScreenRangePolicy.parseScreenRange(
                    Settings.Global.getString(contentResolver,
                            KEY_ENABLE_REFRESH_RATE_SCREEN_OUTER),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_OUTER_MIN),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_OUTER_MAX));
            tableDirty = true;
            refreshActivePanelRange();
            publishStatus();
            requestTraversal();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": config reload failed");
            XposedBridge.log(t);
        }
    }

    private static void registerDeviceStateCallback() {
        try {
            Class<?> managerClass = Class.forName(DEVICE_STATE_MANAGER_CLASS);
            Class<?> callbackClass = Class.forName(DEVICE_STATE_CALLBACK_CLASS);
            Object context = hookContext;
            if (context == null) {
                return;
            }
            Object manager = XposedHelpers.callMethod(
                    context, "getSystemService", managerClass);
            if (manager == null || !managerClass.isInstance(manager)) {
                return;
            }
            Object callback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "equals":
                                    return proxy == args[0];
                                case "toString":
                                    return TAG + "Callback";
                                default:
                                    return null;
                            }
                        }
                        if ("onDeviceStateChanged".equals(method.getName())
                                && args != null && args.length == 1) {
                            handleDeviceStateChanged(args[0]);
                        }
                        return null;
                    });
            Method register = managerClass.getMethod(
                    "registerCallback", Executor.class, callbackClass);
            register.invoke(manager, mainExecutor(), callback);
            deviceStateReady = true;
            Log.i(HookConstants.TAG,
                    "Registered device-state callback for screen ranges");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": device-state callback unavailable; "
                    + "screen ranges stay inactive until a panel is recognized");
            XposedBridge.log(t);
        }
    }

    /**
     * An unrecognized state clears the panel role instead of reusing the previous one:
     * after a firmware update renames states, the previous role must never keep
     * applying one panel's range to the other panel.
     */
    private static void handleDeviceStateChanged(Object state) {
        panelRole = parsePanelRole(state);
        MODE_TABLES.clear();
        displayZeroTable = null;
        tableDirty = true;
        refreshActivePanelRange();
        publishStatus();
        requestTraversal();
    }

    private static int parsePanelRole(Object state) {
        if (state == null || state instanceof Integer) {
            return RefreshRateScreenRangePolicy.PANEL_UNKNOWN;
        }
        try {
            Object name = state.getClass().getMethod("getName").invoke(state);
            return RefreshRateScreenRangePolicy.panelForStateName(String.valueOf(name));
        } catch (Throwable ignored) {
            // Fall through to the toString contract used by Samsung dump formats.
        }
        String text = String.valueOf(state);
        for (String name : new String[]{
                "HALF_OPENED", "HALF_FOLDED", "CONCURRENT_INNER_DEFAULT",
                "CONCURRENT_OUTER_DEFAULT", "OPENED", "OPEN", "CLOSED", "CLOSE", "TENT"}) {
            if (text.contains("name='" + name + "'") || text.contains("name=" + name)) {
                return RefreshRateScreenRangePolicy.panelForStateName(name);
            }
        }
        return RefreshRateScreenRangePolicy.PANEL_UNKNOWN;
    }

    private static void requestTraversal() {
        Object service = wmService;
        if (service == null) return;
        try {
            XposedHelpers.callMethod(service, "requestTraversal");
        } catch (Throwable ignored) {
            // The next natural traversal applies the new configuration anyway.
        }
    }

    private static void publishStatus() {
        backgroundHandler().post(() -> {
            ContentResolver contentResolver = resolver;
            if (contentResolver == null) return;
            String status = buildStatus();
            if (status.equals(lastPublishedStatus)) return;
            try {
                Settings.Global.putString(
                        contentResolver, KEY_REFRESH_RATE_SCREEN_RUNTIME_STATUS, status);
                lastPublishedStatus = status;
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": status publish failed");
                XposedBridge.log(t);
            }
        });
    }

    private static String buildStatus() {
        return "backend=" + (dmsHookInstalled ? "dms_set_display_properties" : "missing")
                + " mode_clamp=" + (modeBackendBroken
                        ? "broken" : (modeClampInstalled ? "installed" : "missing"))
                + " config=" + (configReady ? "ready" : "failed")
                + " device_state=" + (deviceStateReady ? "callback" : "unavailable")
                + " panel=" + panelName(panelRole)
                + " inner=" + describeRange(innerConfig)
                + " outer=" + describeRange(outerConfig)
                + " active=" + describeRange(activePanelRange)
                + " oneui=" + oneUiVersion();
    }

    private static String describeRange(float[] range) {
        return range == null
                ? "off"
                : String.format(Locale.US, "%.1f-%.1f", range[0], range[1]);
    }

    private static String panelName(int role) {
        if (role == RefreshRateScreenRangePolicy.PANEL_INNER) return "inner";
        if (role == RefreshRateScreenRangePolicy.PANEL_OUTER) return "outer";
        return "unknown";
    }

    private static String oneUiVersion() {
        String cached = cachedOneUiVersion;
        if (cached != null) return cached;
        String version = "unknown";
        try {
            version = (String) XposedHelpers.callStaticMethod(
                    Class.forName("android.os.SystemProperties"),
                    "get", ONEUI_VERSION_PROPERTY, "unknown");
        } catch (Throwable ignored) {
            // Diagnostics only; never fail the hook for a missing property.
        }
        cachedOneUiVersion = version;
        return version;
    }

    private static Executor mainExecutor() {
        Object context = hookContext;
        if (context != null) {
            try {
                return (Executor) XposedHelpers.callMethod(context, "getMainExecutor");
            } catch (Throwable ignored) {
                // Fall through to a direct main-looper executor.
            }
        }
        return new DirectExecutor();
    }

    private static final class DirectExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                command.run();
            } else {
                new Handler(Looper.getMainLooper()).post(command);
            }
        }
    }

    private static final class ConfigObserver extends ContentObserver {
        ConfigObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            reloadConfig();
        }
    }

    /**
     * Reflective field accessors for {@code RefreshRatePolicy.mDisplayInfo} and the
     * public {@code DisplayInfo} members used for the panel fingerprint. Resolved once;
     * any missing member disables the mode clamp and thereby the whole feature instead
     * of applying an unvalidatable range.
     */
    private static final class DisplayInfoFields {
        final Field displayInfoField;
        final Field displayIdField;
        final Field logicalWidthField;
        final Field logicalHeightField;
        final Field modeIdField;
        final Field appsSupportedModesField;

        DisplayInfoFields(Field displayInfoField, Field displayIdField,
                Field logicalWidthField, Field logicalHeightField,
                Field modeIdField, Field appsSupportedModesField) {
            this.displayInfoField = displayInfoField;
            this.displayIdField = displayIdField;
            this.logicalWidthField = logicalWidthField;
            this.logicalHeightField = logicalHeightField;
            this.modeIdField = modeIdField;
            this.appsSupportedModesField = appsSupportedModesField;
        }

        static DisplayInfoFields resolve(Class<?> policyClass) throws Throwable {
            Field displayInfoField = policyClass.getDeclaredField("mDisplayInfo");
            displayInfoField.setAccessible(true);
            Class<?> infoClass = displayInfoField.getType();
            DisplayInfoFields candidate = new DisplayInfoFields(
                    displayInfoField,
                    requiredField(infoClass, "displayId"),
                    optionalField(infoClass, "logicalWidth"),
                    optionalField(infoClass, "logicalHeight"),
                    optionalField(infoClass, "modeId"),
                    requiredField(infoClass, "appsSupportedModes"));
            return candidate;
        }

        private static Field requiredField(Class<?> type, String name) throws Throwable {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private static Field optionalField(Class<?> type, String name) {
            try {
                return requiredField(type, name);
            } catch (Throwable ignored) {
                return null;
            }
        }

        Object displayInfoOf(Object policy) {
            try {
                return displayInfoField.get(policy);
            } catch (Throwable ignored) {
                return null;
            }
        }

        int displayIdOf(Object displayInfo) {
            try {
                return displayIdField.getInt(displayInfo);
            } catch (Throwable ignored) {
                return -1;
            }
        }

        int fingerprintOf(Object displayInfo) {
            if (logicalWidthField == null || logicalHeightField == null
                    || modeIdField == null) {
                // Identity comparison still detects a replaced DisplayInfo object.
                return 0;
            }
            try {
                return RefreshRateScreenRangePolicy.fingerprint(
                        logicalWidthField.getInt(displayInfo),
                        logicalHeightField.getInt(displayInfo),
                        modeIdField.getInt(displayInfo));
            } catch (Throwable ignored) {
                return 0;
            }
        }

        Object[] supportedModesOf(Object displayInfo) {
            try {
                Object modes = appsSupportedModesField.get(displayInfo);
                return modes instanceof Object[] ? (Object[]) modes : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /** Cached supported modes of one panel behind one RefreshRatePolicy instance. */
    private static final class PanelTable {
        final int displayId;
        final Object infoRef;
        final int fingerprint;
        final int[] modeIds;
        final float[] rates;

        PanelTable(int displayId, Object infoRef, int fingerprint,
                int[] modeIds, float[] rates) {
            this.displayId = displayId;
            this.infoRef = infoRef;
            this.fingerprint = fingerprint;
            this.modeIds = modeIds;
            this.rates = rates;
        }

        static PanelTable capture(
                Object policy, Object displayInfo, int displayId, DisplayInfoFields fields) {
            try {
                Object[] modes = fields.supportedModesOf(displayInfo);
                if (modes == null) {
                    return new PanelTable(displayId, displayInfo, 0,
                            new int[0], new float[0]);
                }
                ModeEntry[] entries = new ModeEntry[modes.length];
                int count = 0;
                for (Object mode : modes) {
                    try {
                        int id = ((Number) XposedHelpers.callMethod(
                                mode, "getModeId")).intValue();
                        float rate = ((Number) XposedHelpers.callMethod(
                                mode, "getRefreshRate")).floatValue();
                        if (id > 0 && Float.isFinite(rate) && rate > 0f) {
                            entries[count++] = new ModeEntry(id, rate);
                        }
                    } catch (Throwable ignored) {
                        // Skip one invalid mode and keep evaluating the remaining modes.
                    }
                }
                entries = Arrays.copyOf(entries, count);
                Arrays.sort(entries, (left, right) -> Float.compare(left.rate, right.rate));
                int[] ids = new int[count];
                float[] rates = new float[count];
                for (int index = 0; index < count; index++) {
                    ids[index] = entries[index].modeId;
                    rates[index] = entries[index].rate;
                }
                return new PanelTable(displayId, displayInfo,
                        fields.fingerprintOf(displayInfo), ids, rates);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": mode table capture failed");
                XposedBridge.log(t);
                return new PanelTable(displayId, displayInfo, 0, new int[0], new float[0]);
            }
        }

        boolean staleFor(Object displayInfo, DisplayInfoFields fields) {
            return RefreshRateScreenRangePolicy.modeTableStale(
                    infoRef, fingerprint, displayInfo, fields.fingerprintOf(displayInfo));
        }

        Float rateOf(int modeId) {
            for (int index = 0; index < modeIds.length; index++) {
                if (modeIds[index] == modeId) return rates[index];
            }
            return null;
        }
    }

    private static final class ModeEntry {
        final int modeId;
        final float rate;

        ModeEntry(int modeId, float rate) {
            this.modeId = modeId;
            this.rate = rate;
        }
    }
}
