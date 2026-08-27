package io.github.pigerzhu.onelab.hook.applications;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dalvik.system.DexFile;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

/** Enables the native responsive layout shipped by Bilibili International. */
public final class BiliInternationalFoldHook {
    private static final String TAG = "OneLab/BiliInFold";
    private static final String CONFIG_PACKAGE_PREFIX = "kntr.base.config.";
    private static final String SCREEN_ADJUST_CLASS =
            "com.bilibili.app.screen.adjust.utils.ScreenAdjustUtilsKt";

    private static final Object INSTALL_LOCK = new Object();
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Method> HOOKED_CONFIG_METHODS = new HashSet<>();
    private static final AtomicBoolean LOGGED_REWRITE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_PROMOTION = new AtomicBoolean();

    private BiliInternationalFoldHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof Context)) {
                    return;
                }
                Context context = (Context) param.args[0];
                installForClassLoader(context, context.getClassLoader());
            }
        });
    }

    private static void installForClassLoader(Context context, ClassLoader classLoader) {
        if (classLoader == null
                || !context.getPackageName().equals(Application.getProcessName())) {
            return;
        }
        synchronized (INSTALL_LOCK) {
            if (!INSTALLED_LOADERS.add(classLoader)) return;
        }

        AtomicBoolean enabled = new AtomicBoolean(isEnabled(context));
        AtomicBoolean tabletEnabled = new AtomicBoolean(isTabletEnabled(context));
        observeSetting(context, SettingsKeys.KEY_ENABLE_BILI_IN_FOLD_GATE, enabled);
        observeSetting(context, SettingsKeys.KEY_ENABLE_BILI_IN_TABLET_LAYOUT, tabletEnabled);

        boolean configInstalled = installConfigHooks(context, classLoader, enabled);
        boolean windowInstalled = installWindowSizeHook(classLoader, enabled, tabletEnabled);
        if (!configInstalled) {
            Log.w(TAG, "No compatible config method found; leaving app behavior unchanged");
        }
        if (!windowInstalled) {
            Log.w(TAG, "Tablet window promotion unavailable; native gate remains independent");
        }
    }

    private static boolean installConfigHooks(
            Context context, ClassLoader classLoader, AtomicBoolean enabled) {
        boolean installed = false;
        for (String apkPath : apkPaths(context)) {
            DexFile dexFile = null;
            try {
                dexFile = new DexFile(apkPath);
                Enumeration<String> entries = dexFile.entries();
                while (entries.hasMoreElements()) {
                    String className = entries.nextElement();
                    if (!className.startsWith(CONFIG_PACKAGE_PREFIX)) continue;
                    try {
                        Class<?> candidate = Class.forName(className, false, classLoader);
                        if (hookConfigClass(candidate, enabled)) {
                            installed = true;
                        }
                    } catch (Throwable ignored) {
                        // Optional config helpers may reference unavailable dynamic features.
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": config scan failed for " + apkPath);
                XposedBridge.log(t);
            } finally {
                if (dexFile != null) {
                    try {
                        dexFile.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return installed;
    }

    private static boolean hookConfigClass(Class<?> candidate, AtomicBoolean enabled) {
        boolean installed = false;
        for (Method method : candidate.getDeclaredMethods()) {
            if (!isStringConfigMethod(method) || !markConfigMethod(method)) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = param.args != null && param.args.length > 0
                            ? (String) param.args[0] : null;
                    Object original = param.getResult();
                    Object rewritten = BiliInternationalPolicy.rewriteConfigValue(
                            key, original, enabled.get());
                    if (rewritten != original) {
                        param.setResult(rewritten);
                        if (LOGGED_REWRITE.compareAndSet(false, true)) {
                            Log.i(TAG, BiliInternationalPolicy.LARGE_SCREEN_KEY
                                    + " off -> large");
                        }
                    }
                }
            });
            Log.i(TAG, "Matched config path "
                    + candidate.getName() + "#" + method.getName());
            installed = true;
        }
        return installed;
    }

    private static boolean installWindowSizeHook(
            ClassLoader classLoader,
            AtomicBoolean enabled,
            AtomicBoolean tabletEnabled) {
        try {
            Class<?> screenAdjust = classLoader.loadClass(SCREEN_ADJUST_CLASS);
            for (Method method : screenAdjust.getDeclaredMethods()) {
                if (!isWindowSizeFactory(method)) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!enabled.get() || !tabletEnabled.get()
                                || param.args == null || param.args.length != 1
                                || !(param.args[0] instanceof Context)) {
                            return;
                        }
                        Object original = param.getResult();
                        Object promoted = promoteWindowSize((Context) param.args[0], original);
                        if (promoted != null) {
                            param.setResult(promoted);
                            if (LOGGED_PROMOTION.compareAndSet(false, true)) {
                                Log.i(TAG, "Promoted unfolded window to tablet classification");
                            }
                        }
                    }
                });
                Log.i(TAG, "Matched window path "
                        + screenAdjust.getName() + "#" + method.getName());
                return true;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": window-size hook installation failed");
            XposedBridge.log(t);
        }
        return false;
    }

    private static Object promoteWindowSize(Context context, Object original) {
        if (original == null) return null;
        try {
            List<Field> dimensions = new ArrayList<>();
            for (Field field : original.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                    field.setAccessible(true);
                    dimensions.add(field);
                }
            }
            if (dimensions.size() != 2) return null;

            int first = dimensions.get(0).getInt(original);
            int second = dimensions.get(1).getInt(original);
            int configWidth = context.getResources().getConfiguration().screenWidthDp;
            int configHeight = context.getResources().getConfiguration().screenHeightDp;
            boolean direct = distance(first, configWidth) + distance(second, configHeight)
                    <= distance(second, configWidth) + distance(first, configHeight);
            int width = direct ? first : second;
            int height = direct ? second : first;
            int[] promoted = BiliWindowPolicy.promotedDimensions(width, height);
            if (promoted[0] == width && promoted[1] == height) return null;

            Constructor<?> constructor = original.getClass()
                    .getDeclaredConstructor(int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(promoted[0], promoted[1]);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": window-size promotion failed open");
            XposedBridge.log(t);
            return null;
        }
    }

    private static int distance(int left, int right) {
        return Math.abs(left - right);
    }

    private static boolean isStringConfigMethod(Method method) {
        if (Modifier.isAbstract(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != String.class) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 2
                && parameters[0] == String.class
                && parameters[1] == String.class;
    }

    private static boolean markConfigMethod(Method method) {
        synchronized (INSTALL_LOCK) {
            return HOOKED_CONFIG_METHODS.add(method);
        }
    }

    private static boolean isWindowSizeFactory(Method method) {
        if (!Modifier.isStatic(method.getModifiers())
                || method.getReturnType() == void.class
                || method.getReturnType().isPrimitive()) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 1 && parameters[0] == Context.class;
    }

    private static List<String> apkPaths(Context context) {
        List<String> paths = new ArrayList<>();
        if (context.getApplicationInfo().sourceDir != null) {
            paths.add(context.getApplicationInfo().sourceDir);
        }
        String[] splitDirs = context.getApplicationInfo().splitSourceDirs;
        if (splitDirs != null) {
            Collections.addAll(paths, splitDirs);
        }
        return paths;
    }

    private static void observeSetting(Context context, String key, AtomicBoolean value) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(key),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        value.set(HookUtils.globalEnabled(resolver, key, 0));
                    }
                });
    }

    private static boolean isEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_BILI_IN_FOLD_GATE,
                0);
    }

    private static boolean isTabletEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(),
                SettingsKeys.KEY_ENABLE_BILI_IN_TABLET_LAYOUT,
                0);
    }
}
