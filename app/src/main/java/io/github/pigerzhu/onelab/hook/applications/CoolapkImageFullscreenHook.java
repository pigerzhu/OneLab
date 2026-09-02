package io.github.pigerzhu.onelab.hook.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN;

import android.app.Application;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Expands Coolapk's existing image-viewer Activity without replacing its UI. */
public final class CoolapkImageFullscreenHook {
    private static final String TAG = "OneLab/CoolapkImageFullscreen";
    private static final String RULE_CONTROLLER =
            "androidx.window.embedding.RuleController";
    private static final String ACTIVITY_FILTER =
            "androidx.window.embedding.ActivityFilter";
    private static final String ACTIVITY_RULE =
            "androidx.window.embedding.ActivityRule";

    private static final Object INSTALL_LOCK = new Object();
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private CoolapkImageFullscreenHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof Context)) return;
                Context context = (Context) param.args[0];
                installForContext(context, context.getClassLoader());
            }
        });
    }

    private static void installForContext(Context context, ClassLoader classLoader) {
        if (classLoader == null) return;
        synchronized (INSTALL_LOCK) {
            if (!INSTALLED_LOADERS.add(classLoader)) return;
        }

        try {
            Class<?> controllerClass = classLoader.loadClass(RULE_CONTROLLER);
            Object fullscreenRule = createFullscreenRule(classLoader);
            Method addRule = singleArgumentMethod(controllerClass, "addRule");
            Method removeRule = singleArgumentMethod(controllerClass, "removeRule");
            AtomicReference<Object> controller = new AtomicReference<>();
            AtomicBoolean enabled = new AtomicBoolean(isEnabled(context.getContentResolver()));

            hookPhotoTransitions(enabled);
            hookControllerAcquisition(controllerClass, fullscreenRule, enabled,
                    controller, addRule, removeRule);
            hookRuleReplacement(controllerClass, fullscreenRule, enabled, controller);
            hookRuleClearing(controllerClass, fullscreenRule, enabled, controller, addRule);
            observeSettings(context.getContentResolver(), fullscreenRule, enabled,
                    controller, addRule, removeRule);
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Context applicationContext = context.getApplicationContext();
                    controllerClass.getMethod("getInstance", Context.class).invoke(
                            null,
                            applicationContext != null ? applicationContext : context);
                } catch (Throwable throwable) {
                    XposedBridge.log(TAG + ": controller not ready after attach");
                    XposedBridge.log(throwable);
                }
            });
            XposedBridge.log(TAG + ": installed for "
                    + CoolapkImageFullscreenPolicy.TARGET_ACTIVITY);
        } catch (Throwable throwable) {
            synchronized (INSTALL_LOCK) {
                INSTALLED_LOADERS.remove(classLoader);
            }
            XposedBridge.log(TAG + ": public AndroidX rule injection unavailable");
            XposedBridge.log(throwable);
        }
    }

    private static void hookPhotoTransitions(AtomicBoolean enabled) {
        XposedBridge.hookAllMethods(Activity.class, "startActivityForResult",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args.length < 3 || !(param.thisObject instanceof Activity)
                                || !(param.args[0] instanceof Intent)) return;
                        Intent intent = (Intent) param.args[0];
                        ComponentName component = intent.getComponent();
                        String target = component == null ? null : component.getClassName();
                        if (!CoolapkImageFullscreenPolicy.shouldReplaceTransition(
                                enabled.get(), target)) return;
                        Activity source = (Activity) param.thisObject;
                        param.args[2] = ActivityOptions.makeCustomAnimation(
                                source,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out).toBundle();
                    }
                });

        XposedBridge.hookAllMethods(Activity.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity)) return;
                Activity activity = (Activity) param.thisObject;
                if (!isTargetActivity(enabled, activity)) return;
                clearSharedElementTransitions(activity);
            }
        });

        XposedBridge.hookAllMethods(Activity.class, "finishAfterTransition",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof Activity)) return;
                        Activity activity = (Activity) param.thisObject;
                        if (!isTargetActivity(enabled, activity)) return;
                        clearSharedElementTransitions(activity);
                        activity.finish();
                        activity.overridePendingTransition(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out);
                        param.setResult(null);
                    }
                });
    }

    private static boolean isTargetActivity(AtomicBoolean enabled, Activity activity) {
        return CoolapkImageFullscreenPolicy.shouldReplaceTransition(
                enabled.get(), activity.getClass().getName());
    }

    private static void clearSharedElementTransitions(Activity activity) {
        activity.getWindow().setSharedElementEnterTransition(null);
        activity.getWindow().setSharedElementExitTransition(null);
        activity.getWindow().setSharedElementReenterTransition(null);
        activity.getWindow().setSharedElementReturnTransition(null);
        activity.getWindow().setSharedElementsUseOverlay(false);
    }

    private static void hookControllerAcquisition(
            Class<?> controllerClass,
            Object fullscreenRule,
            AtomicBoolean enabled,
            AtomicReference<Object> controller,
            Method addRule,
            Method removeRule) {
        XposedBridge.hookAllMethods(controllerClass, "getInstance", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object instance = param.getResult();
                if (instance == null) return;
                controller.set(instance);
                try {
                    applyRule(instance, fullscreenRule, enabled.get(), addRule, removeRule);
                } catch (Throwable throwable) {
                    XposedBridge.log(TAG + ": failed to apply rule after controller creation");
                    XposedBridge.log(throwable);
                }
            }
        });
    }

    private static Object createFullscreenRule(ClassLoader classLoader) throws Exception {
        Class<?> filterClass = classLoader.loadClass(ACTIVITY_FILTER);
        Constructor<?> filterConstructor =
                filterClass.getConstructor(ComponentName.class, String.class);
        Object filter = filterConstructor.newInstance(
                new ComponentName(
                        CoolapkImageFullscreenPolicy.TARGET_PACKAGE,
                        CoolapkImageFullscreenPolicy.TARGET_ACTIVITY),
                null);
        Set<Object> filters = Collections.singleton(filter);

        Class<?> ruleClass = classLoader.loadClass(ACTIVITY_RULE);
        try {
            Class<?> builderClass = classLoader.loadClass(ACTIVITY_RULE + "$Builder");
            Object builder = builderClass.getConstructor(Set.class).newInstance(filters);
            builderClass.getMethod("setAlwaysExpand", boolean.class).invoke(builder, true);
            return builderClass.getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException noBuilder) {
            try {
                return ruleClass.getConstructor(Set.class, boolean.class)
                        .newInstance(filters, true);
            } catch (NoSuchMethodException oldConstructorMissing) {
                return ruleClass.getConstructor(String.class, Set.class, boolean.class)
                        .newInstance("onelab-coolapk-image-fullscreen", filters, true);
            }
        }
    }

    private static void hookRuleReplacement(
            Class<?> controllerClass,
            Object fullscreenRule,
            AtomicBoolean enabled,
            AtomicReference<Object> controller) {
        XposedBridge.hookAllMethods(controllerClass, "setRules", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                controller.set(param.thisObject);
                if (!enabled.get() || param.args.length == 0
                        || !(param.args[0] instanceof Set<?>)) return;
                Set<Object> rules = new HashSet<>((Set<?>) param.args[0]);
                rules.add(fullscreenRule);
                param.args[0] = rules;
            }
        });
    }

    private static void hookRuleClearing(
            Class<?> controllerClass,
            Object fullscreenRule,
            AtomicBoolean enabled,
            AtomicReference<Object> controller,
            Method addRule) {
        XposedBridge.hookAllMethods(controllerClass, "clearRules", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                controller.set(param.thisObject);
                if (!enabled.get()) return;
                try {
                    addRule.invoke(param.thisObject, fullscreenRule);
                } catch (Throwable throwable) {
                    XposedBridge.log(TAG + ": failed to restore rule after clearRules");
                    XposedBridge.log(throwable);
                }
            }
        });
    }

    private static void observeSettings(
            ContentResolver resolver,
            Object fullscreenRule,
            AtomicBoolean enabled,
            AtomicReference<Object> controller,
            Method addRule,
            Method removeRule) {
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean next = isEnabled(resolver);
                enabled.set(next);
                try {
                    applyRule(controller.get(), fullscreenRule, next, addRule, removeRule);
                } catch (Throwable throwable) {
                    XposedBridge.log(TAG + ": failed to refresh fullscreen rule");
                    XposedBridge.log(throwable);
                }
            }
        };
        resolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN),
                false,
                observer);
        resolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN),
                false,
                observer);
    }

    private static void applyRule(
            Object controller,
            Object fullscreenRule,
            boolean enabled,
            Method addRule,
            Method removeRule) throws Exception {
        if (controller == null) return;
        (enabled ? addRule : removeRule).invoke(controller, fullscreenRule);
    }

    private static Method singleArgumentMethod(Class<?> owner, String name)
            throws NoSuchMethodException {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + '#' + name);
    }

    private static boolean isEnabled(ContentResolver resolver) {
        return CoolapkImageFullscreenPolicy.isEnabled(
                Settings.Global.getString(resolver, KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN),
                Settings.Global.getString(resolver, KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN));
    }
}
