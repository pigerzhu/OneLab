package io.github.pigerzhu.onelab.hook.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;
import io.github.pigerzhu.onelab.hook.core.HookConstants;

/** Applies the configured ratio to QQ's in-Activity Fold split containers. */
public final class QqSplitRatioHook {
    private static final String TAG = "OneLab/QqSplitRatio";
    private static final String SPLASH_ACTIVITY =
            "com.tencent.mobileqq.activity.SplashActivity";
    private static final String FRAGMENT_CONTAINER =
            "androidx.fragment.app.FragmentContainerView";
    private static final String CONSTRAINT_PARAMS =
            "androidx.constraintlayout.widget.ConstraintLayout$LayoutParams";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_LEFT = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_RIGHT = new AtomicBoolean();
    private static final AtomicReference<Float> RATIO = new AtomicReference<>();

    private QqSplitRatioHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length != 1
                        || !(param.args[0] instanceof Context)) {
                    return;
                }
                installForContext((Context) param.args[0]);
            }
        });
    }

    private static void installForContext(Context context) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class<?> fragmentContainer = classLoader.loadClass(FRAGMENT_CONTAINER);
            Class<?> constraintParams = classLoader.loadClass(CONSTRAINT_PARAMS);

            refreshRatio(context.getContentResolver());
            observeRatio(context.getContentResolver());
            hookLayoutParams(fragmentContainer, constraintParams);
            Log.i(TAG, "Installed structural QQ split-ratio hook");
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static void hookLayoutParams(
            Class<?> fragmentContainer,
            Class<?> constraintParams
    ) {
        XposedBridge.hookAllMethods(View.class, "setLayoutParams", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Float ratio = RATIO.get();
                if (ratio == null || param.args == null || param.args.length != 1
                        || !(param.thisObject instanceof View)
                        || !fragmentContainer.isInstance(param.thisObject)
                        || !constraintParams.isInstance(param.args[0])) {
                    return;
                }

                View view = (View) param.thisObject;
                if (!isQqMainHost(view.getContext())) return;

                Object layoutParams = param.args[0];
                if (XposedHelpers.getIntField(layoutParams, "width") != 0
                        || XposedHelpers.getIntField(layoutParams, "height")
                        != ViewGroup.LayoutParams.MATCH_PARENT) {
                    return;
                }

                float originalWeight = XposedHelpers.getFloatField(
                        layoutParams, "horizontalWeight");
                if (!(originalWeight > 0f && originalWeight <= 1f)) return;

                int startToEnd = XposedHelpers.getIntField(layoutParams, "startToEnd");
                int endToStart = XposedHelpers.getIntField(layoutParams, "endToStart");
                if (endToStart > 0 && startToEnd <= 0) {
                    XposedHelpers.setFloatField(layoutParams, "horizontalWeight", ratio);
                    logMatch(LOGGED_LEFT, "left", view);
                } else if (startToEnd > 0 && endToStart <= 0) {
                    XposedHelpers.setFloatField(
                            layoutParams, "horizontalWeight", 1f - ratio);
                    logMatch(LOGGED_RIGHT, "right", view);
                }
            }
        });
    }

    private static boolean isQqMainHost(Context context) {
        Context current = context;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (current instanceof Activity) {
                return SPLASH_ACTIVITY.equals(current.getClass().getName());
            }
            current = current instanceof ContextWrapper
                    ? ((ContextWrapper) current).getBaseContext()
                    : null;
        }
        return false;
    }

    private static void observeRatio(ContentResolver resolver) {
        resolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_SPLIT_VIEW_RATIO_OVERRIDES),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        refreshRatio(resolver);
                    }
                });
    }

    private static void refreshRatio(ContentResolver resolver) {
        RATIO.set(SplitViewRatioOverrides.parse(
                Settings.Global.getString(resolver, KEY_SPLIT_VIEW_RATIO_OVERRIDES))
                .get(HookConstants.QQ_PACKAGE));
    }

    private static void logMatch(AtomicBoolean logged, String side, View view) {
        if (logged.compareAndSet(false, true)) {
            Log.i(TAG, "Matched QQ " + side + " split container id=" + view.getId());
        }
    }
}
