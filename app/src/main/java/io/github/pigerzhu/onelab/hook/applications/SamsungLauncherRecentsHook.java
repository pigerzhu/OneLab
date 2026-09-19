package io.github.pigerzhu.onelab.hook.applications;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;

/** Applies Home Up recent-app layouts independently on the Fold main and cover displays. */
public final class SamsungLauncherRecentsHook {
    private static final String TAG = "OneLab/SamsungRecentsLayout";
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private SamsungLauncherRecentsHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(lpparam.processName)) return;
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof Context)
                        || !STARTED.compareAndSet(false, true)) return;
                Context attachContext = (Context) param.args[0];
                Context context = attachContext.getApplicationContext();
                if (context == null) context = attachContext;
                try {
                    SamsungLauncherRecentsTargets targets =
                            SamsungLauncherRecentsTargets.resolve(lpparam.classLoader);
                    RuntimeState state = new RuntimeState(context, targets);
                    observeSettings(state);
                    observeConfiguration(state);
                    XposedBridge.hookAllConstructors(targets.policyClass, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam constructorParam) {
                            registerAndApply(state, constructorParam.thisObject);
                        }
                    });
                    XposedBridge.hookMethod(targets.updateMethod, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam updateParam) {
                            registerAndApply(state, updateParam.thisObject);
                        }
                    });
                    writeStatus(state, "installed:" + targets.updateMethod.getDeclaringClass()
                            .getName() + "#" + targets.updateMethod.getName());
                    XposedBridge.log(TAG + ": installed per-policy synchronization");
                } catch (Throwable throwable) {
                    writeStatus(context, "failed:" + throwable.getClass().getSimpleName());
                    XposedBridge.log(TAG + ": failed open: " + throwable);
                }
            }
        });
    }

    private static void registerAndApply(RuntimeState state, Object policy) {
        synchronized (state) {
            try {
                SamsungRecentsPolicyRegistry.Entry entry = state.registry.findByPolicy(policy);
                if (entry == null) {
                    Object repository = state.targets.repositoryField.get(policy);
                    Object writableState = findWritableStateFlow(
                            state.targets.mutableStateField.get(policy));
                    entry = state.registry.register(
                            policy,
                            writableState,
                            repository,
                            state.targets.honeySpaceInfoField.get(policy),
                            state.targets.desktopLayoutManagerField.get(policy));
                    hookStateFlowWrite(state, writableState.getClass());
                }
                apply(state, entry);
            } catch (Throwable throwable) {
                logApplyFailure(state, throwable);
            }
        }
    }

    private static void apply(
            RuntimeState state, SamsungRecentsPolicyRegistry.Entry entry) throws Exception {
        Object repository = entry.repository();
        Object writableState = entry.writableState();
        if (repository == null || writableState == null) return;
        if (isSamsungForced(state, entry)) {
            entry.takePendingHomeUpLayout();
            writeStatus(state, "active:desktop-forced");
            return;
        }
        Object repositoryFlow = state.targets.repositoryLayoutMethod.invoke(repository);
        Object homeUpValue = entry.takePendingHomeUpLayout();
        if (homeUpValue == null) {
            homeUpValue = XposedHelpers.callMethod(repositoryFlow, "getValue");
        }
        if (!(homeUpValue instanceof Integer)) return;

        SamsungRecentsLayoutPolicy.UpdateResult result = SamsungRecentsLayoutPolicy.resolve(
                new SamsungRecentsLayoutPolicy.UpdateInput(
                        state.enabled, state.initialized, state.displayType,
                        (Integer) homeUpValue, state.lastObservedHomeUpLayout,
                        state.mainLayout, state.coverLayout));
        boolean writesSucceeded = persistWrites(state, result);
        if (writesSucceeded || (result.mainWrite == null && result.coverWrite == null)) {
            state.lastObservedHomeUpLayout = result.nextObservedHomeUpLayout;
        }
        writeLayout(entry, result.finalLayout);
        writeStatus(state, "active:display=" + state.displayType
                + ",homeUp=" + homeUpValue
                + ",main=" + state.mainLayout
                + ",cover=" + state.coverLayout
                + ",applied=" + result.finalLayout);
    }

    private static void syncRegistered(RuntimeState state, int displayType) {
        synchronized (state) {
            state.displayType = displayType;
            for (SamsungRecentsPolicyRegistry.Entry entry : state.registry.snapshot()) {
                try {
                    Integer selected = SamsungRecentsLayoutPolicy.selectSavedLayout(
                            state.enabled, isSamsungForced(state, entry), displayType,
                            state.mainLayout, state.coverLayout);
                    writeLayout(entry, selected);
                } catch (Throwable throwable) {
                    logApplyFailure(state, throwable);
                }
            }
        }
    }

    private static boolean isSamsungForced(
            RuntimeState state, SamsungRecentsPolicyRegistry.Entry entry) throws Exception {
        Object honeySpaceInfo = entry.honeySpaceInfo();
        Object desktopLayoutManager = entry.desktopLayoutManager();
        if (honeySpaceInfo == null || desktopLayoutManager == null) {
            throw new IllegalStateException("Policy dependencies were collected");
        }
        if (Boolean.TRUE.equals(state.targets.isDexSpaceMethod.invoke(honeySpaceInfo))) {
            return true;
        }
        Object forceFlow = state.targets.getForceLayoutMethod.invoke(desktopLayoutManager);
        return Boolean.TRUE.equals(XposedHelpers.callMethod(forceFlow, "getValue"));
    }

    private static void hookStateFlowWrite(RuntimeState state, Class<?> writableStateClass) {
        if (state.hookedStateFlowClasses.contains(writableStateClass)) return;
        try {
            Method setValue = writableStateClass.getMethod("setValue", Object.class);
            XposedBridge.hookMethod(setValue, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    SamsungRecentsPolicyRegistry.Entry entry =
                            state.registry.findByWritableState(param.thisObject);
                    if (entry == null) return;
                    synchronized (state) {
                        if (entry.isWritingOverride() || !state.enabled
                                || !(param.args[0] instanceof Integer)) return;
                        try {
                            if (isSamsungForced(state, entry)) return;
                            entry.setPendingHomeUpLayout((Integer) param.args[0]);
                            Integer selected = SamsungRecentsLayoutPolicy.selectSavedLayout(
                                    true, false, state.displayType,
                                    state.mainLayout, state.coverLayout);
                            if (selected != null) param.args[0] = selected;
                        } catch (Throwable ignored) {
                            // Preserve Samsung's original state-flow write.
                        }
                    }
                }
            });
            state.hookedStateFlowClasses.add(writableStateClass);
            XposedBridge.log(TAG + ": state-flow hook target="
                    + writableStateClass.getName());
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": state-flow interception unavailable: " + throwable);
        }
    }

    private static void writeLayout(
            SamsungRecentsPolicyRegistry.Entry entry, Integer layout) {
        Object writableState = entry.writableState();
        if (writableState == null || layout == null) return;
        entry.setWritingOverride(true);
        try {
            XposedHelpers.callMethod(writableState, "setValue", layout);
        } finally {
            entry.setWritingOverride(false);
        }
    }

    private static Object findWritableStateFlow(Object candidate) throws Exception {
        Object current = candidate;
        for (int depth = 0; depth < 3 && current != null; depth++) {
            try {
                current.getClass().getMethod("setValue", Object.class);
                return current;
            } catch (NoSuchMethodException ignored) {
                Field delegate = current.getClass().getDeclaredField("$$delegate_0");
                delegate.setAccessible(true);
                current = delegate.get(current);
            }
        }
        throw new NoSuchMethodException("Writable StateFlow delegate not found");
    }

    private static boolean persistWrites(
            RuntimeState state, SamsungRecentsLayoutPolicy.UpdateResult result) {
        boolean success = true;
        if (result.mainWrite != null) {
            boolean saved = putInt(state.context, SettingsKeys.KEY_RECENTS_LAYOUT_MAIN,
                    result.mainWrite);
            if (saved) state.mainLayout = result.mainWrite;
            success &= saved;
        }
        if (result.coverWrite != null) {
            boolean saved = putInt(state.context, SettingsKeys.KEY_RECENTS_LAYOUT_COVER,
                    result.coverWrite);
            if (saved) state.coverLayout = result.coverWrite;
            success &= saved;
        }
        if (result.markInitialized && success) {
            boolean saved = putInt(state.context,
                    SettingsKeys.KEY_RECENTS_LAYOUT_PER_DISPLAY_INITIALIZED, 1);
            if (saved) state.initialized = true;
            success &= saved;
        }
        return success;
    }

    private static int readDisplayType(Configuration configuration) throws Exception {
        Field field = configuration.getClass().getField("semDisplayDeviceType");
        return field.getInt(configuration);
    }

    private static void observeConfiguration(RuntimeState state) {
        state.context.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                try {
                    syncRegistered(state, readDisplayType(newConfig));
                } catch (Throwable throwable) {
                    logApplyFailure(state, throwable);
                }
            }

            @Override
            public void onLowMemory() {
            }
        });
    }

    private static void observeSettings(RuntimeState state) {
        Handler handler = new Handler(Looper.getMainLooper());
        ContentObserver observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                state.reload();
                handler.post(() -> {
                    if (state.enabled && !state.initialized) {
                        for (SamsungRecentsPolicyRegistry.Entry entry
                                : state.registry.snapshot()) {
                            try {
                                apply(state, entry);
                                if (state.initialized) break;
                            } catch (Throwable throwable) {
                                logApplyFailure(state, throwable);
                            }
                        }
                    }
                    syncRegistered(state, state.displayType);
                });
            }
        };
        register(state.context, observer,
                SettingsKeys.KEY_ENABLE_RECENTS_LAYOUT_PER_DISPLAY);
        register(state.context, observer, SettingsKeys.KEY_RECENTS_LAYOUT_MAIN);
        register(state.context, observer, SettingsKeys.KEY_RECENTS_LAYOUT_COVER);
    }

    private static void register(Context context, ContentObserver observer, String key) {
        context.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(key), false, observer);
    }

    private static boolean putInt(Context context, String key, int value) {
        try {
            return Settings.Global.putInt(context.getContentResolver(), key, value);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void logApplyFailure(RuntimeState state, Throwable throwable) {
        if (state.applyFailureLogged.compareAndSet(false, true)) {
            writeStatus(state, "failed:" + throwable.getClass().getSimpleName());
            XposedBridge.log(TAG + ": runtime failed open: " + throwable);
        }
    }

    private static void writeStatus(RuntimeState state, String status) {
        if (status.equals(state.lastStatus)) return;
        state.lastStatus = status;
        writeStatus(state.context, status);
    }

    private static void writeStatus(Context context, String status) {
        try {
            Settings.Global.putString(context.getContentResolver(),
                    SettingsKeys.KEY_RECENTS_LAYOUT_RUNTIME_STATUS, status);
        } catch (RuntimeException ignored) {
            // Diagnostics must never affect launcher behavior.
        }
    }

    private static final class RuntimeState {
        final Context context;
        final SamsungLauncherRecentsTargets targets;
        final SamsungRecentsPolicyRegistry registry = new SamsungRecentsPolicyRegistry();
        final Set<Class<?>> hookedStateFlowClasses =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final AtomicBoolean applyFailureLogged = new AtomicBoolean();
        volatile boolean enabled;
        volatile boolean initialized;
        volatile int mainLayout;
        volatile int coverLayout;
        int displayType;
        Integer lastObservedHomeUpLayout;
        String lastStatus = "";

        RuntimeState(Context context, SamsungLauncherRecentsTargets targets) throws Exception {
            this.context = context;
            this.targets = targets;
            displayType = readDisplayType(context.getResources().getConfiguration());
            reload();
        }

        void reload() {
            enabled = getInt(SettingsKeys.KEY_ENABLE_RECENTS_LAYOUT_PER_DISPLAY, 0) != 0;
            initialized = getInt(
                    SettingsKeys.KEY_RECENTS_LAYOUT_PER_DISPLAY_INITIALIZED, 0) != 0;
            mainLayout = getInt(SettingsKeys.KEY_RECENTS_LAYOUT_MAIN, -1);
            coverLayout = getInt(SettingsKeys.KEY_RECENTS_LAYOUT_COVER, -1);
        }

        private int getInt(String key, int defaultValue) {
            try {
                return Settings.Global.getInt(
                        context.getContentResolver(), key, defaultValue);
            } catch (RuntimeException ignored) {
                return defaultValue;
            }
        }
    }
}
