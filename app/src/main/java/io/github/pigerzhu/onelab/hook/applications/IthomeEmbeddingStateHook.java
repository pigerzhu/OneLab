package io.github.pigerzhu.onelab.hook.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.hook.core.HookConstants;

/** Keeps IT Home's native Activity Embedding switch in sync with Samsung Settings. */
public final class IthomeEmbeddingStateHook {
    private static final String TAG = "OneLab/IthomeEmbedding";
    private static final String PREFERENCES_NAME = "share_data";
    private static final String NATIVE_ENABLED_KEY = "activityEmbeddingEnable";
    private static final String SAMSUNG_MULTI_WINDOW_MANAGER_CLASS =
            "com.samsung.android.multiwindow.MultiWindowManager";

    private static final AtomicBoolean SYNCING_FROM_SYSTEM = new AtomicBoolean();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile SharedPreferences.OnSharedPreferenceChangeListener
            preferenceListener;
    private static volatile ContentObserver systemStateObserver;

    private IthomeEmbeddingStateHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = (Context) param.args[0];
                        installForContext(context, context.getClassLoader());
                    }
                });
    }

    private static void installForContext(Context context, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            SharedPreferences preferences = context.getSharedPreferences(
                    PREFERENCES_NAME, Context.MODE_PRIVATE);
            ContentResolver resolver = context.getContentResolver();
            String systemValue = Settings.Global.getString(
                    resolver, KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING);

            if (systemValue == null) {
                requestSamsungStateUpdate(
                        classLoader,
                        preferences.getBoolean(NATIVE_ENABLED_KEY, false));
            } else {
                syncFromSystem(preferences, "1".equals(systemValue));
            }

            preferenceListener = (shared, key) -> {
                if (!NATIVE_ENABLED_KEY.equals(key) || SYNCING_FROM_SYSTEM.get()) return;
                boolean enabled = shared.getBoolean(NATIVE_ENABLED_KEY, false);
                requestSamsungStateUpdate(classLoader, enabled);
            };
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener);

            systemStateObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    boolean enabled = Settings.Global.getInt(
                            resolver,
                            KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING,
                            0) == 1;
                    syncFromSystem(preferences, enabled);
                }
            };
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING),
                    false,
                    systemStateObserver);
            XposedBridge.log(TAG + ": installed native/Samsung state bridge");
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static void syncFromSystem(SharedPreferences preferences, boolean enabled) {
        SYNCING_FROM_SYSTEM.set(true);
        try {
            if (preferences.getBoolean(NATIVE_ENABLED_KEY, false) != enabled) {
                preferences.edit().putBoolean(NATIVE_ENABLED_KEY, enabled).commit();
            }
        } finally {
            SYNCING_FROM_SYSTEM.set(false);
        }
    }

    private static void requestSamsungStateUpdate(ClassLoader classLoader, boolean enabled) {
        try {
            Class<?> managerClass = XposedHelpers.findClass(
                    SAMSUNG_MULTI_WINDOW_MANAGER_CLASS, classLoader);
            Object manager = XposedHelpers.callStaticMethod(managerClass, "getInstance");
            XposedHelpers.callMethod(
                    manager,
                    "setEmbedActivityPackageEnabled",
                    HookConstants.ITHOME_PACKAGE,
                    enabled,
                    0);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": failed to publish native switch state");
            XposedBridge.log(throwable);
        }
    }

}
