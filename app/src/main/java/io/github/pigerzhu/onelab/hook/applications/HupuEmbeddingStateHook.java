package io.github.pigerzhu.onelab.hook.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Makes Samsung's package switch control Hupu's public AndroidX embedding rules. */
public final class HupuEmbeddingStateHook {
    private static final String TAG = "OneLab/HupuEmbedding";
    private static final String RULE_CONTROLLER =
            "androidx.window.embedding.RuleController";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APPLYING_STATE = new AtomicBoolean();
    private static final Object RULE_LOCK = new Object();

    private static volatile boolean enabled = true;
    private static volatile Object ruleController;
    private static Set<Object> nativeRules = Collections.emptySet();
    private static volatile ContentObserver stateObserver;

    private HupuEmbeddingStateHook() {
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
            Class<?> controllerClass = XposedHelpers.findClass(RULE_CONTROLLER, classLoader);
            ContentResolver resolver = context.getContentResolver();
            refreshEnabled(resolver);
            hookSetRules(controllerClass);
            hookAddRule(controllerClass);
            hookRemoveRule(controllerClass);
            hookClearRules(controllerClass);
            registerObserver(resolver);
            XposedBridge.log(TAG + ": installed public AndroidX rule gate");
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": standard AndroidX rule gate unavailable; preserving native behavior");
            XposedBridge.log(throwable);
        }
    }

    private static void hookSetRules(Class<?> controllerClass) {
        XposedBridge.hookAllMethods(controllerClass, "setRules", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 1 || !(param.args[0] instanceof Set<?>)) return;
                ruleController = param.thisObject;
                if (!APPLYING_STATE.get()) {
                    synchronized (RULE_LOCK) {
                        nativeRules = new HashSet<>((Set<?>) param.args[0]);
                    }
                }
                if (!enabled) param.args[0] = Collections.emptySet();
            }
        });
    }

    private static void hookAddRule(Class<?> controllerClass) {
        XposedBridge.hookAllMethods(controllerClass, "addRule", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 1) return;
                ruleController = param.thisObject;
                synchronized (RULE_LOCK) {
                    Set<Object> updated = new HashSet<>(nativeRules);
                    updated.add(param.args[0]);
                    nativeRules = updated;
                }
                if (!enabled) param.setResult(null);
            }
        });
    }

    private static void hookRemoveRule(Class<?> controllerClass) {
        XposedBridge.hookAllMethods(controllerClass, "removeRule", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 1) return;
                ruleController = param.thisObject;
                synchronized (RULE_LOCK) {
                    Set<Object> updated = new HashSet<>(nativeRules);
                    updated.remove(param.args[0]);
                    nativeRules = updated;
                }
                if (!enabled) param.setResult(null);
            }
        });
    }

    private static void hookClearRules(Class<?> controllerClass) {
        XposedBridge.hookAllMethods(controllerClass, "clearRules", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ruleController = param.thisObject;
                if (!APPLYING_STATE.get()) {
                    synchronized (RULE_LOCK) {
                        nativeRules = Collections.emptySet();
                    }
                }
            }
        });
    }

    private static void registerObserver(ContentResolver resolver) {
        stateObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                refreshEnabled(resolver);
                applyCurrentState();
            }
        };
        resolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING),
                false,
                stateObserver);
    }

    private static void refreshEnabled(ContentResolver resolver) {
        enabled = HupuEmbeddingStatePolicy.isEnabled(Settings.Global.getString(
                resolver, KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING));
    }

    private static void applyCurrentState() {
        Object controller = ruleController;
        if (controller == null) return;
        Set<Object> rules;
        synchronized (RULE_LOCK) {
            rules = enabled ? new HashSet<>(nativeRules) : Collections.emptySet();
        }
        APPLYING_STATE.set(true);
        try {
            XposedHelpers.callMethod(controller, "setRules", rules);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": failed to apply Samsung switch state");
            XposedBridge.log(throwable);
        } finally {
            APPLYING_STATE.set(false);
        }
    }
}
