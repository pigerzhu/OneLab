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
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

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
 * <p>Two independent sub-hooks clamp everything the window manager asks of the display:
 * <ul>
 *   <li>{@code DisplayManagerService$LocalService.setDisplayProperties} — the single
 *       aggregation point (RootWindowContainer passes the per-window votes here once per
 *       traversal). The before-hook endpoint-clamps the preferred refresh rate and the
 *       min/max range votes of the current panel.</li>
 *   <li>{@code RefreshRatePolicy.getPreferredModeId} — mode-id votes bypass refresh-rate
 *       ranges, so the after-hook remaps an out-of-range mode to the nearest supported
 *       in-range mode of the same panel.</li>
 * </ul>
 *
 * <p>Panel identification reuses the public {@code DeviceStateManager} callback and maps
 * state names to panels (CLOSED/TENT → cover, OPENED/HALF_OPENED → main, concurrent
 * defaults by name). Samsung swaps the physical panel behind logical display 0 across
 * fold states, so only display 0 receives the range; every other display fails open.
 * Hot paths only read volatile memory; settings and device state update the cache from
 * low-frequency callbacks.
 */
public final class RefreshRateScreenRangeHook {
    private static final String TAG = "OneLab/RefreshRateScreenRange";
    private static final String POLICY_CLASS = "com.android.server.wm.RefreshRatePolicy";
    private static final String DMS_LOCAL_SERVICE_CLASS =
            "com.android.server.display.DisplayManagerService$LocalService";
    private static final String SET_DISPLAY_PROPERTIES_METHOD = "setDisplayProperties";
    private static final String GET_PREFERRED_MODE_ID_METHOD = "getPreferredModeId";
    private static final String DEVICE_STATE_MANAGER_CLASS =
            "android.hardware.devicestate.DeviceStateManager";
    private static final String DEVICE_STATE_CALLBACK_CLASS =
            "android.hardware.devicestate.DeviceStateManager$DeviceStateCallback";
    private static final String ONEUI_VERSION_PROPERTY = "ro.build.version.oneui";

    /** Samsung maps the active panel onto logical display 0 across fold states. */
    private static final int MAIN_LOGICAL_DISPLAY_ID = 0;

    private static final Object INIT_LOCK = new Object();

    private static volatile boolean dmsHookInstalled;
    private static volatile boolean modeClampInstalled;
    private static volatile boolean initialized;
    private static volatile boolean deviceStateRegistered;

    private static volatile int panelRole = RefreshRateScreenRangePolicy.PANEL_UNKNOWN;
    private static volatile float[] innerRange;
    private static volatile float[] outerRange;
    private static volatile ContentResolver resolver;
    private static volatile Object hookContext;
    private static volatile Object wmService;
    private static volatile String lastPublishedStatus;
    private static volatile String cachedOneUiVersion;

    private static HandlerThread workerThread;
    private static Handler workerHandler;
    private static final ConcurrentHashMap<Object, ModeTable> MODE_TABLES =
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
            XposedBridge.log(TAG + ": preferred-mode hook failed");
            XposedBridge.log(t);
        }
    }

    /** Endpoint-clamps the aggregated display request of the active panel. */
    private static void applyDisplayPropertiesRange(XC_MethodHook.MethodHookParam param) {
        Object[] args = param.args;
        if (args == null || args.length != 9
                || !(args[4] instanceof Float) || !(args[5] instanceof Float)) {
            return;
        }
        ensureInitialized(HookUtils.findFieldValue(param.thisObject, "this$0"));
        float[] range = RefreshRateScreenRangePolicy.rangeForPanel(
                panelRole, innerRange, outerRange);
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
        Object policy = param.thisObject;
        if (policy == null) return;
        ensureInitialized(HookUtils.findFieldValue(policy, "mWmService"));
        float[] range = RefreshRateScreenRangePolicy.rangeForPanel(
                panelRole, innerRange, outerRange);
        if (range == null) return;
        if (!(param.getResult() instanceof Integer)) return;
        int modeId = (Integer) param.getResult();
        if (modeId <= 0) return;

        ModeTable table = MODE_TABLES.get(policy);
        if (table == null) {
            table = buildModeTable(policy);
            MODE_TABLES.put(policy, table);
            if (wmService == null) {
                wmService = HookUtils.findFieldValue(policy, "mWmService");
            }
        }
        if (table.displayId != MAIN_LOGICAL_DISPLAY_ID) return;
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

    private static ModeTable buildModeTable(Object policy) {
        try {
            Object displayInfo = HookUtils.findFieldValue(policy, "mDisplayInfo");
            int displayId = -1;
            Object modes = null;
            if (displayInfo != null) {
                Object displayIdValue = HookUtils.findFieldValue(displayInfo, "displayId");
                if (displayIdValue instanceof Number) {
                    displayId = ((Number) displayIdValue).intValue();
                }
                modes = HookUtils.findFieldValue(displayInfo, "appsSupportedModes");
            }
            if (!(modes instanceof Object[])) {
                return ModeTable.unsupported(displayId);
            }
            Object[] modeArray = (Object[]) modes;
            ModeEntry[] entries = new ModeEntry[modeArray.length];
            int count = 0;
            for (Object mode : modeArray) {
                try {
                    int id = ((Number) XposedHelpers.callMethod(mode, "getModeId")).intValue();
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
            return new ModeTable(displayId, ids, rates);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": mode table unavailable");
            XposedBridge.log(t);
            return ModeTable.unsupported(-1);
        }
    }

    private static void ensureInitialized(Object contextProvider) {
        if (initialized) return;
        if (contextProvider == null) return;
        synchronized (INIT_LOCK) {
            if (initialized) return;
            ContentResolver contentResolver =
                    HookUtils.resolverFromAnyContext(contextProvider);
            if (contentResolver == null) {
                return;
            }
            resolver = contentResolver;
            hookContext = HookUtils.firstContextFromObject(contextProvider);
            Handler handler = backgroundHandler();
            ConfigObserver observer = new ConfigObserver(handler);
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
            reloadConfig();
            registerDeviceStateCallback();
            initialized = true;
            Log.i(HookConstants.TAG, "Initialized screen refresh-rate range backend");
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
            innerRange = RefreshRateScreenRangePolicy.parseScreenRange(
                    Settings.Global.getString(contentResolver,
                            KEY_ENABLE_REFRESH_RATE_SCREEN_INNER),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_INNER_MIN),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_INNER_MAX));
            outerRange = RefreshRateScreenRangePolicy.parseScreenRange(
                    Settings.Global.getString(contentResolver,
                            KEY_ENABLE_REFRESH_RATE_SCREEN_OUTER),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_OUTER_MIN),
                    Settings.Global.getString(contentResolver,
                            KEY_REFRESH_RATE_SCREEN_OUTER_MAX));
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
            deviceStateRegistered = true;
            Log.i(HookConstants.TAG,
                    "Registered device-state callback for screen ranges");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": device-state callback unavailable; "
                    + "screen ranges stay inactive until a panel is recognized");
            XposedBridge.log(t);
        }
    }

    private static void handleDeviceStateChanged(Object state) {
        int role = parsePanelRole(state);
        if (role == RefreshRateScreenRangePolicy.PANEL_UNKNOWN) {
            // An unrecognized state must not clear a previously verified role.
            return;
        }
        panelRole = role;
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
                + " mode_clamp=" + (modeClampInstalled ? "installed" : "missing")
                + " device_state=" + (deviceStateRegistered ? "callback" : "unavailable")
                + " panel=" + panelName(panelRole)
                + " inner=" + describeRange(innerRange)
                + " outer=" + describeRange(outerRange)
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

    private static final class ModeEntry {
        final int modeId;
        final float rate;

        ModeEntry(int modeId, float rate) {
            this.modeId = modeId;
            this.rate = rate;
        }
    }

    private static final class ModeTable {
        final int displayId;
        final int[] modeIds;
        final float[] rates;

        ModeTable(int displayId, int[] modeIds, float[] rates) {
            this.displayId = displayId;
            this.modeIds = modeIds;
            this.rates = rates;
        }

        static ModeTable unsupported(int displayId) {
            return new ModeTable(displayId, new int[0], new float[0]);
        }

        Float rateOf(int modeId) {
            for (int index = 0; index < modeIds.length; index++) {
                if (modeIds[index] == modeId) return rates[index];
            }
            return null;
        }
    }
}
