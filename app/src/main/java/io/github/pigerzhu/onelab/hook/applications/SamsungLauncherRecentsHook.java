package io.github.pigerzhu.onelab.hook.applications;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
                    XposedBridge.hookMethod(targets.updateMethod, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam updateParam) {
                            apply(state, updateParam.thisObject);
                        }
                    });
                    writeStatus(state, "installed:" + targets.updateMethod.getDeclaringClass()
                            .getName() + "#" + targets.updateMethod.getName());
                    XposedBridge.log(TAG + ": installed stable policy hook");
                } catch (Throwable throwable) {
                    writeStatus(context, "failed:" + throwable.getClass().getSimpleName());
                    XposedBridge.log(TAG + ": failed open: " + throwable);
                }
            }
        });
    }

    private static void apply(RuntimeState state, Object policy) {
        synchronized (state) {
            try {
                state.policy = new WeakReference<>(policy);
                Object repository = state.targets.repositoryField.get(policy);
                Object repositoryFlow = state.targets.repositoryLayoutMethod.invoke(repository);
                Object mutableState = state.targets.mutableStateField.get(policy);
                Object writableState = findWritableStateFlow(mutableState);
                hookStateFlowWrite(state, writableState);
                Object homeUpValue = state.pendingHomeUpLayout;
                state.pendingHomeUpLayout = null;
                if (homeUpValue == null) {
                    homeUpValue = XposedHelpers.callMethod(repositoryFlow, "getValue");
                }
                if (!(homeUpValue instanceof Integer)) return;

                int displayType = readDisplayType(state.context);
                SamsungRecentsLayoutPolicy.UpdateResult result =
                        SamsungRecentsLayoutPolicy.resolve(
                                new SamsungRecentsLayoutPolicy.UpdateInput(
                                        state.enabled,
                                        state.initialized,
                                        displayType,
                                        (Integer) homeUpValue,
                                        state.lastObservedHomeUpLayout,
                                        state.mainLayout,
                                        state.coverLayout));

                boolean writesSucceeded = persistWrites(state, result);
                if (writesSucceeded || (result.mainWrite == null && result.coverWrite == null)) {
                    state.lastObservedHomeUpLayout = result.nextObservedHomeUpLayout;
                }
                if (result.finalLayout != null) {
                    state.writingOverride = true;
                    try {
                        XposedHelpers.callMethod(writableState, "setValue", result.finalLayout);
                    } finally {
                        state.writingOverride = false;
                    }
                }
                writeStatus(state, "active:display=" + displayType
                        + ",homeUp=" + homeUpValue
                        + ",main=" + state.mainLayout
                        + ",cover=" + state.coverLayout
                        + ",applied=" + result.finalLayout);
            } catch (Throwable throwable) {
                if (state.applyFailureLogged.compareAndSet(false, true)) {
                    writeStatus(state, "failed:" + throwable.getClass().getSimpleName());
                    XposedBridge.log(TAG + ": runtime failed open: " + throwable);
                }
            }
        }
    }

    private static void hookStateFlowWrite(RuntimeState state, Object writableState) {
        if (!state.stateFlowHooked.compareAndSet(false, true)) return;
        try {
            Method setValue = writableState.getClass().getMethod("setValue", Object.class);
            XposedBridge.hookMethod(setValue, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject != writableState
                            || state.writingOverride || !state.enabled
                            || !(param.args[0] instanceof Integer)) return;
                    try {
                        int proposed = (Integer) param.args[0];
                        state.pendingHomeUpLayout = proposed;
                        int selected = SamsungRecentsLayoutPolicy.isCoverDisplay(
                                readDisplayType(state.context))
                                ? state.coverLayout : state.mainLayout;
                        if (SamsungRecentsLayoutPolicy.isSupportedLayout(selected)) {
                            param.args[0] = selected;
                        }
                    } catch (Throwable ignored) {
                        // Preserve Samsung's original state-flow write.
                    }
                }
            });
            XposedBridge.log(TAG + ": state-flow hook target="
                    + writableState.getClass().getName());
        } catch (Throwable throwable) {
            state.stateFlowHooked.set(false);
            XposedBridge.log(TAG + ": state-flow interception unavailable: " + throwable);
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

    private static int readDisplayType(Context context) throws Exception {
        Configuration configuration = context.getResources().getConfiguration();
        Field field = configuration.getClass().getField("semDisplayDeviceType");
        return field.getInt(configuration);
    }

    private static void observeSettings(RuntimeState state) {
        Handler handler = new Handler(Looper.getMainLooper());
        ContentObserver observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                state.reload();
                Object policy = state.policy.get();
                if (policy == null) return;
                handler.post(() -> {
                    try {
                        state.targets.updateMethod.invoke(policy);
                    } catch (Throwable throwable) {
                        if (state.refreshFailureLogged.compareAndSet(false, true)) {
                            XposedBridge.log(TAG + ": settings refresh failed open: "
                                    + throwable);
                        }
                    }
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
        final AtomicBoolean applyFailureLogged = new AtomicBoolean();
        final AtomicBoolean refreshFailureLogged = new AtomicBoolean();
        final AtomicBoolean stateFlowHooked = new AtomicBoolean();
        WeakReference<Object> policy = new WeakReference<>(null);
        volatile boolean enabled;
        volatile boolean initialized;
        volatile int mainLayout;
        volatile int coverLayout;
        Integer lastObservedHomeUpLayout;
        Integer pendingHomeUpLayout;
        boolean writingOverride;
        String lastStatus = "";

        RuntimeState(Context context, SamsungLauncherRecentsTargets targets) {
            this.context = context;
            this.targets = targets;
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
