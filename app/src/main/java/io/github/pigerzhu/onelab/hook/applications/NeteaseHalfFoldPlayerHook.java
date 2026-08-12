package io.github.pigerzhu.onelab.hook.applications;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

/** Bridges Samsung Fold posture into NetEase Cloud Music's built-in half-fold player. */
public final class NeteaseHalfFoldPlayerHook {
    private static final String TAG = "OneLab/NeteaseHalfFold";
    private static final String PLAYER_ACTIVITY = "com.netease.cloudmusic.activity.PlayerActivity";
    private static final String HALF_PLAYER_ACTIVITY =
            "com.netease.cloudmusic.halffold.HalfFoldPlayerActivity";
    private static final String HALF_FOLD_OBSERVER =
            "com.netease.cloudmusic.halffold.HalfFoldStateObserver";

    private NeteaseHalfFoldPlayerHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(lpparam.processName)) return;
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof Context)) return;
                installForContext((Context) param.args[0]);
            }
        });
    }

    private static void installForContext(Context context) {
        if (!"samsung".equalsIgnoreCase(Build.MANUFACTURER)) return;
        ClassLoader classLoader = context.getClassLoader();
        try {
            AtomicBoolean enabled = new AtomicBoolean(isEnabled(context));
            SamsungPostureBridge bridge = new SamsungPostureBridge(context, enabled);
            observeSetting(context, enabled, bridge);
            hookPlayerLifecycle(classLoader, PLAYER_ACTIVITY, false, bridge);
            hookPlayerLifecycle(classLoader, HALF_PLAYER_ACTIVITY, true, bridge);
            installObserverCompatibility(classLoader, enabled);
            bridge.start();
            Log.i(TAG, "Installed Samsung posture bridge");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": installation failed");
            XposedBridge.log(t);
        }
    }

    private static void hookPlayerLifecycle(
            ClassLoader classLoader,
            String className,
            boolean halfPlayer,
            SamsungPostureBridge bridge) throws ClassNotFoundException {
        Class<?> activityClass = classLoader.loadClass(className);
        XposedBridge.hookAllMethods(activityClass, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                bridge.onResumed((Activity) param.thisObject, halfPlayer);
            }
        });
        XC_MethodHook clearResumed = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                bridge.onPaused((Activity) param.thisObject);
            }
        };
        XposedBridge.hookAllMethods(activityClass, "onStop", clearResumed);
        XposedBridge.hookAllMethods(activityClass, "onDestroy", clearResumed);
    }

    private static void installObserverCompatibility(
            ClassLoader classLoader,
            AtomicBoolean enabled) {
        try {
            Class<?> observerClass = classLoader.loadClass(HALF_FOLD_OBSERVER);
            XposedBridge.hookAllConstructors(observerClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length != 2 || !(param.args[1] instanceof Boolean)) return;
                    Object owner = param.args[0];
                    String ownerClassName = owner == null ? "" : owner.getClass().getName();
                    param.args[1] = NeteaseHalfFoldPolicy.observerNewHalfArgument(
                            ownerClassName,
                            (Boolean) param.args[1],
                            enabled.get());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": observer compatibility unavailable; posture bridge kept active");
            XposedBridge.log(t);
        }
    }

    private static void observeSetting(
            Context context,
            AtomicBoolean enabled,
            SamsungPostureBridge bridge) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_NETEASE_HALF_FOLD_PLAYER),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        enabled.set(isEnabled(context));
                        bridge.dispatchKnownPosture();
                    }
                });
    }

    private static boolean isEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_NETEASE_HALF_FOLD_PLAYER,
                0);
    }

    private static final class SamsungPostureBridge implements SensorEventListener {
        private final Context context;
        private final AtomicBoolean enabled;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final AtomicBoolean transitionPending = new AtomicBoolean();
        private WeakReference<Activity> resumedActivity = new WeakReference<>(null);
        private boolean halfPlayer;
        private SensorManager sensorManager;
        private boolean hingeFallbackRegistered;
        private int lastDeviceState = -1;
        private float lastHingeAngle = Float.NaN;

        SamsungPostureBridge(Context context, AtomicBoolean enabled) {
            this.context = NeteaseHalfFoldPolicy.preferAvailable(
                    context.getApplicationContext(), context);
            this.enabled = enabled;
        }

        void start() {
            if (registerDeviceStateCallback()) return;
            registerHingeFallback();
        }

        private void registerHingeFallback() {
            if (hingeFallbackRegistered) return;
            sensorManager = context.getSystemService(SensorManager.class);
            Sensor hinge = sensorManager == null
                    ? null : sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
            if (hinge != null) {
                hingeFallbackRegistered = sensorManager.registerListener(
                        this, hinge, SensorManager.SENSOR_DELAY_NORMAL);
                if (hingeFallbackRegistered) Log.i(TAG, "Using public hinge-angle fallback");
            } else {
                Log.i(TAG, "No Samsung posture source available");
            }
        }

        void onResumed(Activity activity, boolean isHalfPlayer) {
            resumedActivity = new WeakReference<>(activity);
            halfPlayer = isHalfPlayer;
            transitionPending.set(false);
            dispatchKnownPosture();
        }

        void onPaused(Activity activity) {
            if (resumedActivity.get() == activity) resumedActivity.clear();
        }

        private boolean registerDeviceStateCallback() {
            try {
                Class<?> managerClass = Class.forName("android.hardware.devicestate.DeviceStateManager");
                Class<?> callbackClass = Class.forName(
                        "android.hardware.devicestate.DeviceStateManager$DeviceStateCallback");
                Object manager = Context.class
                        .getMethod("getSystemService", Class.class)
                        .invoke(context, managerClass);
                if (manager == null || !managerClass.isInstance(manager)) return false;
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
                                int state = stateOf(args[0]);
                                if (state >= 0) {
                                    dispatchDeviceState(state);
                                } else {
                                    registerHingeFallback();
                                }
                            }
                            return null;
                        });
                Method register = managerClass.getMethod(
                        "registerCallback", Executor.class, callbackClass);
                register.invoke(manager, context.getMainExecutor(), callback);
                Log.i(TAG, "Using Samsung device-state callback");
                return true;
            } catch (Throwable t) {
                Log.i(TAG, "Device-state callback unavailable; using hinge fallback");
                return false;
            }
        }

        private int stateOf(Object state) {
            if (state instanceof Integer) return -1;
            try {
                Object name = state.getClass().getMethod("getName").invoke(state);
                return NeteaseHalfFoldPolicy.stateForName(String.valueOf(name));
            } catch (Throwable ignored) {
            }
            String text = String.valueOf(state);
            for (String name : new String[]{
                    "HALF_OPENED", "HALF_FOLDED", "OPENED", "OPEN", "CLOSED", "CLOSE", "TENT"
            }) {
                if (text.contains("name='" + name + "'") || text.contains("name=" + name)) {
                    return NeteaseHalfFoldPolicy.stateForName(name);
                }
            }
            return -1;
        }

        private void dispatchDeviceState(int state) {
            lastDeviceState = state;
            dispatch(NeteaseHalfFoldPolicy.actionForDeviceState(state, halfPlayer));
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length == 0) return;
            lastHingeAngle = event.values[0];
            dispatch(NeteaseHalfFoldPolicy.actionForHingeAngle(lastHingeAngle, halfPlayer));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        private void dispatchKnownPosture() {
            if (lastDeviceState < 0 && Float.isNaN(lastHingeAngle)) return;
            dispatch(NeteaseHalfFoldPolicy.actionForKnownPosture(
                    lastDeviceState, lastHingeAngle, halfPlayer));
        }

        private void dispatch(NeteaseHalfFoldPolicy.Action action) {
            if (!enabled.get() || action == NeteaseHalfFoldPolicy.Action.NONE) return;
            Activity activity = resumedActivity.get();
            if (activity == null || activity.isFinishing()) return;
            if (!transitionPending.compareAndSet(false, true)) return;
            mainHandler.post(() -> {
                try {
                    if (!enabled.get()
                            || resumedActivity.get() != activity
                            || activity.isFinishing()
                            || currentAction() != action) {
                        transitionPending.set(false);
                        return;
                    }
                    if (action == NeteaseHalfFoldPolicy.Action.ENTER_HALF_PLAYER) {
                        Intent intent = new Intent();
                        intent.setClassName(activity, HALF_PLAYER_ACTIVITY);
                        activity.startActivity(intent);
                        Log.i(TAG, "Entered built-in half-fold player");
                    } else {
                        activity.finish();
                        Log.i(TAG, "Exited built-in half-fold player");
                    }
                } catch (Throwable t) {
                    transitionPending.set(false);
                    XposedBridge.log(TAG + ": player transition failed");
                    XposedBridge.log(t);
                }
            });
        }

        private NeteaseHalfFoldPolicy.Action currentAction() {
            if (lastDeviceState < 0 && Float.isNaN(lastHingeAngle)) {
                return NeteaseHalfFoldPolicy.Action.NONE;
            }
            return NeteaseHalfFoldPolicy.actionForKnownPosture(
                    lastDeviceState, lastHingeAngle, halfPlayer);
        }
    }
}
